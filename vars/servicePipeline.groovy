import dev.delivery.ServiceConfig

/** Public entry point. Validation finishes before allocating a Jenkins agent. */
def call(Map options = [:]) {
    ServiceConfig config = new ServiceConfig(options)

    node(config.agentLabel) {
        timeout(time: config.timeoutMinutes, unit: 'MINUTES') {
            stage('Checkout') {
                deleteDir()
                checkout scm
            }
            stage('Verify') {
                echo "Verifying ${config.serviceName} with Gradle"
                sh(label: "Build and test ${config.serviceName}", script: './gradlew --no-daemon build')
            }
        }
    }
}
