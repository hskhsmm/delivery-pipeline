package dev.delivery

/** Jenkins 스텝 없이 검증한 서비스 설정. 체크포인트에 보관할 수 있도록 직렬화한다. */
final class ServiceConfig implements Serializable {
    private static final long serialVersionUID = 1L
    private static final Set<String> KEYS = ['serviceName', 'agentLabel', 'timeoutMinutes', 'buildEnv'] as Set<String>

    final String serviceName
    final String agentLabel
    final int timeoutMinutes
    final Map<String, String> buildEnv
    final ImageConfig image

    ServiceConfig(String serviceName, String agentLabel, int timeoutMinutes, Map<String, String> buildEnv, ImageConfig image) {
        this.serviceName = serviceName
        this.agentLabel = agentLabel
        this.timeoutMinutes = timeoutMinutes
        this.buildEnv = buildEnv
        this.image = image
    }

    // Jenkins는 생성자 본문을 CPS로 변환하지 않으므로 검증은 생성 전에 끝낸다.
    static ServiceConfig fromOptions(Map<String, ?> options, ImageConfig image = null) {
        ConfigChecks.knownKeys(options, KEYS)
        String serviceName = ConfigChecks.text(options, 'serviceName', null)
        if (!(serviceName ==~ /[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?/)) {
            throw new IllegalArgumentException('serviceName must be a lowercase DNS label of 1–63 characters')
        }
        String agentLabel = ConfigChecks.text(options, 'agentLabel', 'java')
        int timeoutMinutes = ConfigChecks.minutes(options, 'timeoutMinutes', 30)
        Map<String, String> buildEnv = environment(options.containsKey('buildEnv') ? options.get('buildEnv') : [:])
        return new ServiceConfig(serviceName, agentLabel, timeoutMinutes, buildEnv, image)
    }

    static ServiceConfig fromYaml(Object document, String agentLabel) {
        // readYaml은 빈 파일·리스트·스칼라도 반환할 수 있으므로 루트 타입부터 확인한다.
        if (!(document instanceof Map)) {
            throw new IllegalArgumentException('Configuration YAML must contain a single mapping')
        }
        Map<?, ?> raw = (Map<?, ?>) document
        if (raw.containsKey('agentLabel')) {
            throw new IllegalArgumentException('Set agentLabel in deploy(...), not YAML: the agent is allocated before reading the file')
        }
        Map<String, Object> normalized = new LinkedHashMap<String, Object>()
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw new IllegalArgumentException('Configuration keys must be strings')
            }
            normalized.put((String) entry.getKey(), entry.getValue())
        }
        normalized.put('agentLabel', agentLabel)
        ImageConfig image = normalized.containsKey('image') ? ImageConfig.fromOptions(normalized.remove('image')) : null
        return fromOptions(normalized, image)
    }

    List<String> environmentOverrides() {
        List<String> result = new ArrayList<String>()
        for (Map.Entry<String, String> entry : buildEnv.entrySet()) {
            result.add(entry.getKey() + '=' + entry.getValue())
        }
        return result
    }

    private static Map<String, String> environment(Object raw) {
        if (!(raw instanceof Map)) {
            throw new IllegalArgumentException('buildEnv must be a mapping of environment variable names to strings')
        }
        Map<String, String> result = new LinkedHashMap<String, String>()
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
}
