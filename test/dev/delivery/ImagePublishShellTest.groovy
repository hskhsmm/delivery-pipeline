package dev.delivery

import groovy.json.JsonSlurperClassic
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import static org.junit.Assert.*

class ImagePublishShellTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder()

    @Test
    void shellPublishesWithTemporaryAuthAndReturnsOnlyMetadata() {
        Map result = runWithDockerStub('0')
        assertEquals(result.stderr, 0, result.status)
        assertEquals('sha256:' + ('c' * 64), new JsonSlurperClassic().parseText(result.stdout)['containerimage.digest'])
        assertTrue(result.stderr.contains('build progress'))
        assertFalse((result.stdout + result.stderr).contains('stub-token'))
    }

    @Test
    void shellRemovesAuthEvenWhenBuildxFails() {
        Map result = runWithDockerStub('42')
        assertEquals(42, result.status)
        assertEquals('', result.stdout)
    }

    private Map runWithDockerStub(String exitCode) {
        File stub = temp.newFile('docker')
        stub.text = '''#!/bin/sh
set -eu
printf '%s' "$DOCKER_CONFIG" > "$STUB_CONFIG_LOCATION"
test -z "${DELIVERY_HUB_USER+x}${DELIVERY_HUB_TOKEN+x}"
test "$1 $2 $3" = "buildx build --push"
test "$5" = "$DELIVERY_IMAGE_TAG"
test "$7" = "org.opencontainers.image.revision=$DELIVERY_IMAGE_REVISION"
test "$8" = "--metadata-file"
cp "$DOCKER_CONFIG/config.json" "$STUB_AUTH_COPY"
printf 'build progress\\n'
test "$STUB_EXIT_CODE" = 0 || exit "$STUB_EXIT_CODE"
printf '{"containerimage.digest":"%s"}\\n' "$STUB_DIGEST" > "$9"
'''
        assertTrue(stub.setExecutable(true))
        File configLocation = temp.newFile('config-location')
        File authCopy = temp.newFile('auth-copy')
        File stderr = temp.newFile('stderr')
        File service = temp.newFolder('service')
        ProcessBuilder command = new ProcessBuilder('sh', new File('resources/dev/delivery/publish-image.sh').absolutePath)
            .directory(service)
            .redirectError(stderr)
        command.environment().putAll([
            PATH: temp.root.absolutePath + File.pathSeparator + command.environment().get('PATH'),
            DELIVERY_HUB_USER: 'stub-user',
            DELIVERY_HUB_TOKEN: 'stub-token',
            DELIVERY_IMAGE_TAG: 'docker.io/team/api:sha-' + ('a' * 40),
            DELIVERY_IMAGE_REVISION: 'a' * 40,
            STUB_CONFIG_LOCATION: configLocation.absolutePath,
            STUB_AUTH_COPY: authCopy.absolutePath,
            STUB_EXIT_CODE: exitCode,
            STUB_DIGEST: 'sha256:' + ('c' * 64)
        ])
        Process process = command.start()
        String output = process.inputStream.getText('UTF-8')
        int status = process.waitFor()

        assertFalse(configLocation.text.isEmpty())
        assertFalse(new File(configLocation.text).exists())
        assertFalse(configLocation.text.startsWith(service.absolutePath + File.separator))
        Map auth = new JsonSlurperClassic().parse(authCopy)
        String encoded = auth.auths['https://index.docker.io/v1/'].auth
        assertEquals('stub-user:stub-token', new String(encoded.decodeBase64(), 'UTF-8'))
        assertEquals(0, service.list().length)
        [stdout: output, stderr: stderr.text, status: status]
    }
}
