package dev.delivery

import groovy.json.JsonSlurperClassic
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/** Jenkins 스텝은 기록하되 YAML은 실제 파싱한다. 실제 CPS·에이전트 실행은 재현하지 않는다. */
class PipelineHarness {
    List<Map> events = []
    final Map<String, String> environment = [CI: 'outside']
    final Map<String, String> sourceFiles
    final Map<String, String> workspace = [:]
    boolean failCheckout = false
    boolean failBuild = false
    boolean failPublish = false
    String revision = 'a' * 40
    String gitBranch = 'origin/main'
    final Map<String, String> jobEnvironment = [:]
    final Map<String, String> gitOpsFiles = [:]
    String publishMetadata = '{"containerimage.digest":"sha256:' + ('b' * 64) + '"}'

    PipelineHarness(Map<String, String> files = ['jenkins.yml': new File('test/fixtures/jenkins.yml').text]) {
        sourceFiles = new LinkedHashMap<String, String>(files)
    }

    Script load(String entrypoint) {
        Binding binding = new Binding([
            scm: [name: 'service-repository'],
            env: jobEnvironment,
            node: { String label, Closure body ->
                events << [type: 'node', label: label]
                body.call()
            },
            timeout: { Map options, Closure body ->
                events << [type: 'timeout', options: options]
                body.call()
            },
            stage: { String name, Closure body ->
                events << [type: 'stage', name: name]
                body.call()
            },
            deleteDir: { ->
                events << [type: 'clean']
                workspace.clear()
            },
            checkout: { Object scm ->
                events << [type: 'checkout', scm: scm]
                if (failCheckout) throw new IllegalStateException('checkout failed')
                workspace.putAll(sourceFiles)
                [GIT_COMMIT: revision, GIT_BRANCH: gitBranch]
            },
            fileExists: { String path ->
                events << [type: 'fileExists', path: path]
                workspace.containsKey(path)
            },
            readYaml: { Map options ->
                events << [type: 'readYaml', options: options]
                LoaderOptions loader = new LoaderOptions()
                loader.codePointLimit = options.codePointLimit
                loader.maxAliasesForCollections = options.maxAliasesForCollections
                String source = options.containsKey('text') ? options.text : workspace[options.file]
                List documents = new Yaml(new SafeConstructor(loader)).loadAll(source).toList()
                documents.size() == 1 ? documents[0] : documents
            },
            pwd: { Map options -> 'workspace@tmp' },
            dir: { String path, Closure body -> body.call() },
            readFile: { Map options -> workspace[options.file] },
            writeFile: { Map options ->
                workspace[options.file] = options.text
                events << [type: 'writeFile', options: options]
            },
            withEnv: { List<String> overrides, Closure body ->
                events << [type: 'withEnv', overrides: overrides]
                Map<String, String> previous = new LinkedHashMap<String, String>(environment)
                try {
                    overrides.each { String value ->
                        int separator = value.indexOf('=')
                        environment[value.substring(0, separator)] = value.substring(separator + 1)
                    }
                    body.call()
                } finally {
                    // Jenkins withEnv의 단계 범위를 대체한다. 오류가 나도 원래 환경으로 복구한다.
                    environment.clear()
                    environment.putAll(previous)
                }
            },
            echo: { String message -> events << [type: 'echo', message: message] },
            sh: { Map options ->
                events << [type: 'sh', options: options, environment: new LinkedHashMap(environment)]
                if (environment.DELIVERY_GITOPS_ACTION == 'checkout') {
                    workspace.putAll(gitOpsFiles)
                    return
                }
                if (options.returnStdout) {
                    if (failPublish) throw new IllegalStateException('publish failed')
                    return publishMetadata
                }
                if (failBuild) throw new IllegalStateException('build failed')
            },
            libraryResource: { String path -> new File("resources/${path}").text },
            usernamePassword: { Map options -> options },
            string: { Map options -> options },
            withCredentials: { List bindings, Closure body ->
                events << [type: 'withCredentials', bindings: bindings]
                body.call()
            },
            readJSON: { Map options -> new JsonSlurperClassic().parseText(options.text) },
            writeJSON: { Map options -> events << [type: 'writeJSON', options: options] },
            archiveArtifacts: { Map options -> events << [type: 'archive', options: options] },
            error: { String message -> throw new IllegalStateException(message) }
        ])
        GroovyShell shell = new GroovyShell(getClass().classLoader, binding)
        if (entrypoint == 'servicePipeline') {
            binding.setVariable('deploy', shell.parse(new File('vars/deploy.groovy')))
        }
        return shell.parse(new File("vars/${entrypoint}.groovy"))
    }
}
