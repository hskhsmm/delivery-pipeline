import dev.delivery.DeployOptions
import dev.delivery.GradleBuild
import dev.delivery.ImagePublisher
import dev.delivery.ServiceConfig

/** 서비스 저장소의 jenkins.yml을 읽어 공통 Gradle 빌드를 실행하는 공개 진입점. */
def call(Map options = [:]) {
    // 파일을 읽기 전에 필요한 에이전트·경로 설정만 먼저 검증한다.
    DeployOptions bootstrap = new DeployOptions(options)

    node(bootstrap.agentLabel) {
        ServiceConfig config
        String revision
        timeout(time: bootstrap.checkoutTimeoutMinutes, unit: 'MINUTES') {
            stage('Checkout') {
                // 이전 빌드의 설정을 재사용하지 않고 이번 SCM 체크아웃에서 다시 읽는다.
                deleteDir()
                revision = checkout(scm)?.GIT_COMMIT
            }
            stage('Configure') {
                if (!fileExists(bootstrap.configFile)) {
                    error("Required configuration file not found: ${bootstrap.configFile}")
                }
                // readYaml은 Pipeline Utility Steps 플러그인이 제공한다.
                // YAML 파서의 code-point 수와 컬렉션 별칭 수에 상한을 둔다.
                Object document = readYaml(
                    file: bootstrap.configFile,
                    codePointLimit: 65536,
                    maxAliasesForCollections: 20
                )
                config = ServiceConfig.fromYaml(document, bootstrap.agentLabel)
            }
        }

        // YAML의 timeout은 설정을 읽은 뒤 실행하는 빌드에만 적용한다.
        timeout(time: config.timeoutMinutes, unit: 'MINUTES') {
            new GradleBuild(this).run(config)
        }
        if (config.image != null) {
            new ImagePublisher(this).run(config.image, revision)
        }
    }
}
