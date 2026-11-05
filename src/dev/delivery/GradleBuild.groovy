package dev.delivery

/** 두 공개 진입점에서 재사용하는 Verify 단계. Jenkins 실행 문맥은 호출자가 전달한다. */
final class GradleBuild implements Serializable {
    private static final long serialVersionUID = 1L
    private final Script steps

    GradleBuild(Script steps) {
        this.steps = steps
    }

    void run(ServiceConfig config) {
        steps.stage('Verify') {
            // 전역 env를 수정하지 않아 성공·실패 후에도 다른 단계에 값이 남지 않는다.
            steps.withEnv(config.environmentOverrides()) {
                steps.echo "Verifying ${config.serviceName} with Gradle"
                // 환경변수 값은 셸 명령에 삽입하지 않는다. 라이브러리도 값을 직접 출력하지 않는다.
                steps.sh(label: "Build and test ${config.serviceName}", script: './gradlew --no-daemon build')
            }
        }
    }
}
