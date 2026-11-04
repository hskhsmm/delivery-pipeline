package dev.delivery

import groovy.transform.CompileStatic

/** Validated configuration: no Jenkins steps or external effects here. */
@CompileStatic
final class ServiceConfig implements Serializable {
    private static final long serialVersionUID = 1L
    private static final Set<String> KEYS = ['serviceName', 'agentLabel', 'timeoutMinutes'] as Set<String>

    final String serviceName
    final String agentLabel
    final int timeoutMinutes

    ServiceConfig(Map<String, ?> options) {
        if (options == null) {
            throw new IllegalArgumentException('Configuration must not be null')
        }
        for (String key : options.keySet()) {
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown configuration key: ${key}")
            }
        }
        this.serviceName = text(options, 'serviceName', null)
        if (!(serviceName ==~ /[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?/)) {
            throw new IllegalArgumentException('serviceName must be a lowercase DNS label of 1–63 characters')
        }
        this.agentLabel = text(options, 'agentLabel', 'java')
        Object timeout = options.containsKey('timeoutMinutes') ? options.get('timeoutMinutes') : 30
        if (!(timeout instanceof Integer || timeout instanceof Long)) {
            throw new IllegalArgumentException('timeoutMinutes must be an integer from 1 to 120')
        }
        long minutes = ((Number) timeout).longValue()
        if (minutes < 1 || minutes > 120) {
            throw new IllegalArgumentException('timeoutMinutes must be an integer from 1 to 120')
        }
        this.timeoutMinutes = (int) minutes
    }

    private static String text(Map<String, ?> options, String key, String fallback) {
        Object value = options.containsKey(key) ? options.get(key) : fallback
        if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
            throw new IllegalArgumentException("${key} must be a non-empty string")
        }
        return (String) value
    }
}
