package dev.delivery

/** 두 진입점이 동일한 타입·기본값 규칙을 사용하도록 순수 검증만 모은다. */
final class ConfigChecks {
    static void knownKeys(Map<?, ?> options, Set<String> allowed) {
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

    static String text(Map<?, ?> options, String key, String fallback) {
        Object value = options.containsKey(key) ? options.get(key) : fallback
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("${key} must be a non-empty string")
        }
        return (String) value
    }

    static int minutes(Map<?, ?> options, String key, int fallback) {
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
}
