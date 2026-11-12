/** 서비스 저장소의 jenkins.yml을 읽어 공통 Gradle 빌드를 실행하는 공개 진입점. */
def call(Map options = [:]) {
    // 파일을 읽기 전에 필요한 에이전트·경로 설정만 먼저 검증한다.
    Map bootstrap = deployOptions(options)

    node(bootstrap.agentLabel) {
        Map config
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
                config = serviceConfigFromYaml(document, bootstrap.agentLabel)
            }
        }

        // YAML의 timeout은 설정을 읽은 뒤 실행하는 빌드에만 적용한다.
        timeout(time: config.timeoutMinutes, unit: 'MINUTES') {
            runGradle(config)
        }
        if (config.image != null) {
            publishImage(config.image, revision)
        }
    }
}

/** Map을 직접 전달하는 기존 진입점. YAML 기반 deploy()와 동일한 빌드 단계를 사용한다. */
def runLegacy(Map options) {
    Map config = serviceConfig(options)

    node(config.agentLabel) {
        timeout(time: config.timeoutMinutes, unit: 'MINUTES') {
            stage('Checkout') {
                deleteDir()
                checkout scm
            }
            runGradle(config)
        }
    }
}

private void runGradle(Map config) {
    stage('Verify') {
        // 전역 env를 수정하지 않아 성공·실패 후에도 다른 단계에 값이 남지 않는다.
        withEnv(environmentOverrides(config.buildEnv)) {
            echo "Verifying ${config.serviceName} with Gradle"
            // 환경변수 값은 셸 명령에 삽입하지 않는다. 라이브러리도 값을 직접 출력하지 않는다.
            sh(label: "Build and test ${config.serviceName}", script: './gradlew --no-daemon build')
        }
    }
}

private void publishImage(Map config, String revision) {
    stage('Publish Image') {
        timeout(time: 30, unit: 'MINUTES') {
            if (!(revision ==~ /[a-f0-9]{40}/)) {
                error('Image publishing requires GIT_COMMIT from the Git checkout')
            }
            if (!fileExists('Dockerfile')) {
                error('Image publishing requires a Dockerfile in the service repository root')
            }
            String tag = config.repository + ':sha-' + revision
            String script = libraryResource('dev/delivery/publish-image.sh')
            String metadata
            withEnv(['DELIVERY_IMAGE_TAG=' + tag, 'DELIVERY_IMAGE_REVISION=' + revision]) {
                // Credentials Binding 범위는 발행 셸 호출에만 적용한다.
                withCredentials([usernamePassword(
                    credentialsId: config.credentialsId,
                    usernameVariable: 'DELIVERY_HUB_USER',
                    passwordVariable: 'DELIVERY_HUB_TOKEN'
                )]) {
                    metadata = sh(label: 'Build and push Docker image', script: script, returnStdout: true)
                }
            }
            Map result = readJSON(text: metadata, returnPojo: true)
            Object digest = result['containerimage.digest']
            if (!(digest instanceof String) || !(digest ==~ /sha256:[a-f0-9]{64}/)) {
                error('Docker Buildx did not return a valid published image digest')
            }
            String reference = config.repository + '@' + digest
            // 푸시와 digest 검증이 모두 끝난 뒤에만 발행 결과를 보관한다.
            writeJSON(file: 'published-image.json', json: [
                image: reference, tag: tag, digest: digest, sourceRevision: revision
            ], pretty: 2)
            archiveArtifacts(artifacts: 'published-image.json', followSymlinks: false)
            echo "Published image: ${reference}"
        }
    }
}

private Map deployOptions(Map<String, ?> options) {
    knownKeys(options, ['agentLabel', 'configFile', 'checkoutTimeoutMinutes'])
    String agentLabel = text(options, 'agentLabel', 'java')
    String configFile = text(options, 'configFile', 'jenkins.yml')
    int checkoutTimeoutMinutes = minutes(options, 'checkoutTimeoutMinutes', 10)

    // 파일 경로는 체크아웃한 저장소를 기준으로 한 상대 경로만 받는다.
    for (String segment : configFile.split('/', -1)) {
        if (!(segment ==~ /[A-Za-z0-9._-]+/) || segment == '.' || segment == '..') {
            throw new IllegalArgumentException('configFile must be a relative repository path without . or .. segments')
        }
    }
    return [agentLabel: agentLabel, configFile: configFile, checkoutTimeoutMinutes: checkoutTimeoutMinutes]
}

private Map serviceConfig(Map<String, ?> options, Map image = null) {
    knownKeys(options, ['serviceName', 'agentLabel', 'timeoutMinutes', 'buildEnv'])
    String serviceName = text(options, 'serviceName', null)
    if (!(serviceName ==~ /[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?/)) {
        throw new IllegalArgumentException('serviceName must be a lowercase DNS label of 1–63 characters')
    }
    String agentLabel = text(options, 'agentLabel', 'java')
    int timeoutMinutes = minutes(options, 'timeoutMinutes', 30)
    Map<String, String> buildEnv = environment(options.containsKey('buildEnv') ? options.get('buildEnv') : [:])
    return [serviceName: serviceName, agentLabel: agentLabel, timeoutMinutes: timeoutMinutes, buildEnv: buildEnv, image: image]
}

private Map serviceConfigFromYaml(Object document, String agentLabel) {
    // readYaml은 빈 파일·리스트·스칼라도 반환할 수 있으므로 루트 타입부터 확인한다.
    if (!(document instanceof Map)) {
        throw new IllegalArgumentException('Configuration YAML must contain a single mapping')
    }
    Map<?, ?> raw = (Map<?, ?>) document
    if (raw.containsKey('agentLabel')) {
        throw new IllegalArgumentException('Set agentLabel in deploy(...), not YAML: the agent is allocated before reading the file')
    }
    // Jenkins 샌드박스에서도 기본 허용되는 Groovy 컬렉션 리터럴을 사용한다.
    Map<String, Object> normalized = [:]
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
        if (!(entry.getKey() instanceof String)) {
            throw new IllegalArgumentException('Configuration keys must be strings')
        }
        normalized.put((String) entry.getKey(), entry.getValue())
    }
    normalized.put('agentLabel', agentLabel)
    Map image = normalized.containsKey('image') ? imageConfig(normalized.remove('image')) : null
    return serviceConfig(normalized, image)
}

private List<String> environmentOverrides(Map<String, String> buildEnv) {
    List<String> result = []
    for (Map.Entry<String, String> entry : buildEnv.entrySet()) {
        result.add(entry.getKey() + '=' + entry.getValue())
    }
    return result
}

private Map<String, String> environment(Object raw) {
    if (!(raw instanceof Map)) {
        throw new IllegalArgumentException('buildEnv must be a mapping of environment variable names to strings')
    }
    Map<String, String> result = [:]
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
        Object key = entry.getKey()
        Object value = entry.getValue()
        if (!(key instanceof String) || !(((String) key) ==~ /[A-Za-z_][A-Za-z0-9_]*/)) {
            throw new IllegalArgumentException('buildEnv keys must be valid environment variable names')
        }
        // 값 자체는 예외나 로그에 출력하지 않는다. YAML 숫자·boolean은 명시적으로 인용해야 한다.
        if (!(value instanceof String) || ((String) value).indexOf(0) >= 0) {
            throw new IllegalArgumentException("buildEnv.${key} must be a string without NUL characters")
        }
        result.put((String) key, (String) value)
    }
    // 호출자가 입력 Map을 나중에 바꿔도 실행 중인 설정은 변하지 않는다.
    return Collections.unmodifiableMap(result)
}

private Map imageConfig(Object raw) {
    if (!(raw instanceof Map)) {
        throw new IllegalArgumentException('image must be a mapping')
    }
    Map<?, ?> options = (Map<?, ?>) raw
    knownKeys(options, ['repository', 'credentialsId'])
    String name = text(options, 'repository', null)
    // 태그와 digest는 실행 결과에서 만들기 때문에 설정에는 Docker Hub 저장소 이름만 받는다.
    if (name.length() > 255 || !(name ==~ /(?:docker[.]io\/)?[a-z0-9]+(?:[._-][a-z0-9]+)*\/[a-z0-9]+(?:[._-][a-z0-9]+)*/)) {
        throw new IllegalArgumentException('image.repository must be a Docker Hub namespace/repository without a tag or digest')
    }
    String repository = name.startsWith('docker.io/') ? name : 'docker.io/' + name
    return [repository: repository, credentialsId: text(options, 'credentialsId', null)]
}

private void knownKeys(Map<?, ?> options, List<String> allowed) {
    if (options == null) {
        throw new IllegalArgumentException('Configuration must not be null')
    }
    for (Object key : options.keySet()) {
        if (!(key instanceof String)) {
            throw new IllegalArgumentException('Configuration keys must be strings')
        }
        if (!allowed.contains(key)) {
            throw new IllegalArgumentException("Unknown configuration key: ${key}")
        }
    }
}

private String text(Map<?, ?> options, String key, String fallback) {
    Object value = options.containsKey(key) ? options.get(key) : fallback
    if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
        throw new IllegalArgumentException("${key} must be a non-empty string")
    }
    return (String) value
}

private int minutes(Map<?, ?> options, String key, int fallback) {
    Object value = options.containsKey(key) ? options.get(key) : fallback
    // YAML의 문자열·소수와 큰 Long을 int로 강제 변환하지 않는다.
    if (!(value instanceof Integer || value instanceof Long)) {
        throw new IllegalArgumentException("${key} must be an integer from 1 to 120")
    }
    long result = ((Number) value).longValue()
    if (result < 1 || result > 120) {
        throw new IllegalArgumentException("${key} must be an integer from 1 to 120")
    }
    return (int) result
}
