package dev.delivery

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import static org.junit.Assert.*

class GitOpsShellTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void pushesOnlyTheManifestAndReusesThePullRequestOnRetry() {
        File remote = temp.newFolder('remote.git')
        checked(remote, 'git', 'init', '--bare', '--initial-branch=main')
        File seed = temp.newFolder('seed')
        checked(seed, 'git', 'init', '--initial-branch=main')
        File manifest = new File(seed, 'deployment.yaml')
        manifest.text = 'image: team/api:old\n'
        new File(seed, 'service.yaml').text = 'kind: Service\n'
        checked(seed, 'git', 'add', '.')
        checked(seed, 'git', '-c', 'user.name=Fixture', '-c', 'user.email=test@example.com', 'commit', '-m', 'Seed')
        String remoteUrl = 'file://' + remote.absolutePath
        checked(seed, 'git', 'push', remoteUrl, 'main')
        String main = checked(remote, 'git', 'rev-parse', 'main').trim()

        File gh = temp.newFile('gh')
        gh.text = '''#!/bin/sh
set -eu
test "$GH_TOKEN" = 'stub-secret'
test "$1 $2 $4" = 'api --method repos/team/environments/pulls'
case "$3" in
    GET)
        if [ -f "$PR_STATE" ]; then
            cat "$PR_STATE"
        fi
        ;;
    POST)
        test ! -f "$PR_STATE"
        test "$7" = -f
        case "$8" in
            head=delivery/api/*) ;;
            *) exit 2 ;;
        esac
        git --git-dir="$REMOTE" rev-parse "refs/heads/${8#head=}" >/dev/null
        printf 'OPEN https://github.com/team/environments/pull/1\n' > "$PR_STATE"
        printf 'https://github.com/team/environments/pull/1\n'
        ;;
    *) exit 2 ;;
esac
'''
        assertTrue(gh.setExecutable(true))
        String image = 'docker.io/team/api@sha256:' + ('b' * 64)
        Map<String, String> environment = [
            DELIVERY_GITOPS_REPO: 'team/environments',
            DELIVERY_GITOPS_MANIFEST: 'deployment.yaml',
            DELIVERY_GITOPS_SERVICE: 'api',
            DELIVERY_GITOPS_IMAGE: image,
            DELIVERY_GITOPS_REVISION: 'a' * 40,
            GH_TOKEN: 'stub-secret',
            PR_STATE: new File(temp.root, 'pr-state').absolutePath,
            REMOTE: remote.absolutePath,
            GIT_CONFIG_COUNT: '1',
            GIT_CONFIG_KEY_0: "url.${remoteUrl}.insteadOf".toString(),
            GIT_CONFIG_VALUE_0: 'https://github.com/team/environments.git'
        ]

        File first = temp.newFolder('first')
        shell(first, environment, 'checkout')
        new File(first, 'deployment.yaml').text = "image: ${image}\n"
        new File(first, 'service.yaml').text = 'unexpected local change\n'
        assertTrue(shell(first, environment, 'open').contains('/pull/1'))
        String branch = checked(remote, 'git', 'branch', '--list', 'delivery/api/*', '--format=%(refname:short)').trim()
        String head = checked(remote, 'git', 'rev-parse', branch)
        assertEquals('deployment.yaml', checked(remote, 'git', 'diff', '--name-only', 'main', branch).trim())
        assertEquals("image: ${image}\n".toString(), checked(remote, 'git', 'show', "${branch}:deployment.yaml"))

        File retry = temp.newFolder('retry')
        shell(retry, environment, 'checkout')
        new File(retry, 'deployment.yaml').text = "image: ${image}\n"
        assertTrue(shell(retry, environment, 'open').contains('Reusing GitOps pull request'))
        assertEquals(head, checked(remote, 'git', 'rev-parse', branch))
        assertEquals(main, checked(remote, 'git', 'rev-parse', 'main').trim())
    }

    private String shell(File directory, Map<String, String> environment, String action) {
        ProcessBuilder command = new ProcessBuilder('sh', new File('resources/dev/delivery/gitops-pr.sh').absolutePath)
        command.environment().putAll(environment)
        command.environment().put('PATH', temp.root.absolutePath + File.pathSeparator + command.environment().get('PATH'))
        command.environment().put('DELIVERY_GITOPS_ACTION', action)
        String output = run(directory, command)
        assertFalse(output.contains('stub-secret'))
        return output
    }

    private static String checked(File directory, String... command) {
        return run(directory, new ProcessBuilder(command))
    }

    private static String run(File directory, ProcessBuilder command) {
        Process process = command.directory(directory).redirectErrorStream(true).start()
        String output = process.inputStream.getText('UTF-8')
        assertEquals(output, 0, process.waitFor())
        return output
    }
}
