package dev.delivery

/** Verify를 통과한 체크아웃으로 이미지를 발행하고 다음 단계에 넘길 digest를 기록한다. */
final class ImagePublisher implements Serializable {
    private static final long serialVersionUID = 1L
    private final Script steps

    ImagePublisher(Script steps) {
        this.steps = steps
    }

    void run(ImageConfig config, String revision) {
        steps.stage('Publish Image') {
            steps.timeout(time: 30, unit: 'MINUTES') {
                if (!(revision ==~ /[a-f0-9]{40}/)) {
                    steps.error('Image publishing requires GIT_COMMIT from the Git checkout')
                }
                if (!steps.fileExists('Dockerfile')) {
                    steps.error('Image publishing requires a Dockerfile in the service repository root')
                }
                String tag = config.repository + ':sha-' + revision
                String script = steps.libraryResource('dev/delivery/publish-image.sh')
                String metadata
                steps.withEnv(['DELIVERY_IMAGE_TAG=' + tag, 'DELIVERY_IMAGE_REVISION=' + revision]) {
                    // Credentials Binding 범위는 발행 셸 호출에만 적용한다.
                    steps.withCredentials([steps.usernamePassword(
                        credentialsId: config.credentialsId,
                        usernameVariable: 'DELIVERY_HUB_USER',
                        passwordVariable: 'DELIVERY_HUB_TOKEN'
                    )]) {
                        metadata = steps.sh(label: 'Build and push Docker image', script: script, returnStdout: true)
                    }
                }
                Map result = steps.readJSON(text: metadata, returnPojo: true)
                Object digest = result['containerimage.digest']
                if (!(digest instanceof String) || !(digest ==~ /sha256:[a-f0-9]{64}/)) {
                    steps.error('Docker Buildx did not return a valid published image digest')
                }
                String reference = config.repository + '@' + digest
                // 푸시와 digest 검증이 모두 끝난 뒤에만 발행 결과를 보관한다.
                steps.writeJSON(file: 'published-image.json', json: [
                    image: reference, tag: tag, digest: digest, sourceRevision: revision
                ], pretty: 2)
                steps.archiveArtifacts(artifacts: 'published-image.json', followSymlinks: false)
                steps.echo "Published image: ${reference}"
            }
        }
    }
}
