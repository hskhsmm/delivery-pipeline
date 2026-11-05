import dev.delivery.GradleBuild
import dev.delivery.ServiceConfig

/** Map을 직접 전달하는 기존 진입점. YAML 기반 deploy()와 동일한 빌드 단계를 사용한다. */
def call(Map options = [:]) {
    ServiceConfig config = ServiceConfig.fromOptions(options)

    node(config.agentLabel) {
        timeout(time: config.timeoutMinutes, unit: 'MINUTES') {
            stage('Checkout') {
                deleteDir()
                checkout scm
            }
            new GradleBuild(this).run(config)
        }
    }
}
