package dev.delivery

import org.junit.Test
import static org.junit.Assert.*

class ServiceConfigTest {
    @Test
    void appliesDefaults() {
        ServiceConfig config = new ServiceConfig([serviceName: 'orders-api'])
        assertEquals('orders-api', config.serviceName)
        assertEquals('java', config.agentLabel)
        assertEquals(30, config.timeoutMinutes)
    }

    @Test
    void acceptsExplicitAgentSettings() {
        ServiceConfig config = new ServiceConfig([
            serviceName: 'payments-api',
            agentLabel: 'java21 && linux', timeoutMinutes: 60
        ])
        assertEquals('java21 && linux', config.agentLabel)
        assertEquals(60, config.timeoutMinutes)
    }

    @Test
    void rejectsMissingAndMalformedServiceNames() {
        rejects([:])
        [null, '', 'MyService', '../escape', 'trailing-', 'x' * 64, 123].each {
            rejects([serviceName: it])
        }
    }

    @Test
    void rejectsUnknownKeys() {
        rejects([serviceName: 'api', agentLable: 'java'])
        rejects([serviceName: 'api', buildTool: 'gradle'])
    }

    @Test
    void rejectsNonIntegralAndUnboundedTimeouts() {
        [0, -1, 121, 30.5, '30', null, Long.MAX_VALUE].each {
            rejects([serviceName: 'api', timeoutMinutes: it])
        }
    }

    @Test
    void acceptsTimeoutBoundaryValues() {
        assertEquals(1, new ServiceConfig([serviceName: 'api', timeoutMinutes: 1L]).timeoutMinutes)
        assertEquals(120, new ServiceConfig([serviceName: 'api', timeoutMinutes: 120]).timeoutMinutes)
    }

    @Test
    void rejectsEmptyLabelsAndNullConfiguration() {
        rejects([serviceName: 'api', agentLabel: '  '])
        rejects(null)
    }

    @Test
    void configurationSurvivesSerializationForPipelineState() {
        ServiceConfig original = new ServiceConfig([serviceName: 'api', agentLabel: 'java21'])
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(original) }
        ServiceConfig restored = (ServiceConfig) new ObjectInputStream(
            new ByteArrayInputStream(bytes.toByteArray())
        ).readObject()
        assertEquals(original.serviceName, restored.serviceName)
        assertEquals(original.agentLabel, restored.agentLabel)
    }

    private static void rejects(Map options) {
        try {
            new ServiceConfig(options)
            fail('Expected invalid configuration to fail')
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.message.isEmpty())
        }
    }
}
