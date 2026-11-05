package dev.delivery

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

    PipelineHarness(Map<String, String> files = ['jenkins.yml': new File('test/fixtures/jenkins.yml').text]) {
        sourceFiles = new LinkedHashMap<String, String>(files)
    }

    Script load(String entrypoint) {
        Binding binding = new Binding([
            scm: [name: 'service-repository'],
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
                List documents = new Yaml(new SafeConstructor(loader)).loadAll(workspace[options.file]).toList()
                documents.size() == 1 ? documents[0] : documents
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
                if (failBuild) throw new IllegalStateException('build failed')
            },
            error: { String message -> throw new IllegalStateException(message) }
        ])
        return new GroovyShell(getClass().classLoader, binding).parse(new File("vars/${entrypoint}.groovy"))
    }
}
