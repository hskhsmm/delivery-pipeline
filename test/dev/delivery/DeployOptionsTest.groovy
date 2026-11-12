package dev.delivery

import org.junit.Test
import static org.junit.Assert.*

class DeployOptionsTest {
    private final Script pipeline = new PipelineHarness().load('deploy')

    @Test
    void providesBootstrapDefaults() {
        Map options = pipeline.deployOptions([:])
        assertEquals('java', options.agentLabel)
        assertEquals('jenkins.yml', options.configFile)
        assertEquals(10, options.checkoutTimeoutMinutes)
    }

    @Test
    void acceptsRepositoryRelativeConfigAndAgentLabelExpression() {
        Map options = pipeline.deployOptions([
            configFile: '.ci/jenkins.yaml', agentLabel: 'java21 && linux', checkoutTimeoutMinutes: 15
        ])
        assertEquals('.ci/jenkins.yaml', options.configFile)
        assertEquals('java21 && linux', options.agentLabel)
        assertEquals(15, options.checkoutTimeoutMinutes)
    }

    @Test
    void rejectsPathsOutsideTheCheckout() {
        ['', '/tmp/config.yml', '../config.yml', 'ci/../config.yml', './jenkins.yml', 'ci//config.yml', 'C:\\config.yml'].each {
            rejects([configFile: it])
        }
    }

    @Test
    void rejectsUnknownKeysAndBadAgentLabels() {
        rejects([serviceName: 'api'])
        rejects([agentLable: 'java'])
        rejects([agentLabel: '  '])
        rejects(null)
    }

    @Test
    void rejectsInvalidCheckoutTimeouts() {
        [0, 121, 1.5, '10', null, Long.MAX_VALUE].each {
            rejects([checkoutTimeoutMinutes: it])
        }
    }

    private void rejects(Map options) {
        try {
            pipeline.deployOptions(options)
            fail('Expected invalid deploy options to fail')
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.message.isEmpty())
        }
    }
}
