package dev.delivery

/** 발행 대상과 Jenkins 자격증명 참조만 보관한다. */
final class ImageConfig implements Serializable {
    private static final long serialVersionUID = 1L
    private static final Set<String> KEYS = ['repository', 'credentialsId'] as Set<String>

    final String repository
    final String credentialsId

    ImageConfig(String repository, String credentialsId) {
        this.repository = repository
        this.credentialsId = credentialsId
    }

    static ImageConfig fromOptions(Object raw) {
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException('image must be a mapping')
        }
        Map<?, ?> options = (Map<?, ?>) raw
        ConfigChecks.knownKeys(options, KEYS)
        String name = ConfigChecks.text(options, 'repository', null)
        // 태그와 digest는 실행 결과에서 만들기 때문에 설정에는 Docker Hub 저장소 이름만 받는다.
        if (name.length() > 255 || !(name ==~ /(?:docker[.]io\/)?[a-z0-9]+(?:[._-][a-z0-9]+)*\/[a-z0-9]+(?:[._-][a-z0-9]+)*/)) {
            throw new IllegalArgumentException('image.repository must be a Docker Hub namespace/repository without a tag or digest')
        }
        String repository = name.startsWith('docker.io/') ? name : 'docker.io/' + name
        return new ImageConfig(repository, ConfigChecks.text(options, 'credentialsId', null))
    }
}
