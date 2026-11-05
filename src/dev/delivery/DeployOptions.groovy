package dev.delivery

/** YAML을 읽기 전에 필요한 체크아웃 설정. 서비스 설정과 별도로 먼저 검증한다. */
final class DeployOptions implements Serializable {
    private static final long serialVersionUID = 1L
    private static final Set<String> KEYS = ['agentLabel', 'configFile', 'checkoutTimeoutMinutes'] as Set<String>

    final String agentLabel
    final String configFile
    final int checkoutTimeoutMinutes

    DeployOptions(String agentLabel, String configFile, int checkoutTimeoutMinutes) {
        this.agentLabel = agentLabel
        this.configFile = configFile
        this.checkoutTimeoutMinutes = checkoutTimeoutMinutes
    }

    static DeployOptions fromOptions(Map<String, ?> options) {
        ConfigChecks.knownKeys(options, KEYS)
        String agentLabel = ConfigChecks.text(options, 'agentLabel', 'java')
        String configFile = ConfigChecks.text(options, 'configFile', 'jenkins.yml')
        int checkoutTimeoutMinutes = ConfigChecks.minutes(options, 'checkoutTimeoutMinutes', 10)

        // 파일 경로는 체크아웃한 저장소를 기준으로 한 상대 경로만 받는다.
        for (String segment : configFile.split('/', -1)) {
            if (!(segment ==~ /[A-Za-z0-9._-]+/) || segment == '.' || segment == '..') {
                throw new IllegalArgumentException('configFile must be a relative repository path without . or .. segments')
            }
        }
        return new DeployOptions(agentLabel, configFile, checkoutTimeoutMinutes)
    }
}
