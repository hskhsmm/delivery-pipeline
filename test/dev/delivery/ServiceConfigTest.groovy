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
        ServiceConfig original = new ServiceConfig([
            serviceName: 'api', agentLabel: 'java21', buildEnv: [JAVA_TOOL_OPTIONS: '-Xmx1g']
        ])
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(original) }
        ServiceConfig restored = (ServiceConfig) new ObjectInputStream(
            new ByteArrayInputStream(bytes.toByteArray())
        ).readObject()
        assertEquals(original.serviceName, restored.serviceName)
        assertEquals(original.agentLabel, restored.agentLabel)
        assertEquals(original.buildEnv, restored.buildEnv)
    }

    @Test
    void createsServiceConfigurationFromYamlWithoutMutatingInput() {
        Map document = [serviceName: 'api', buildEnv: [CI: 'true']]
        ServiceConfig config = ServiceConfig.fromYaml(document, 'java21')
        assertEquals('java21', config.agentLabel)
        assertEquals(['CI=true'], config.environmentOverrides())
        assertFalse(document.containsKey('agentLabel'))
        document.buildEnv.CI = 'changed'
        assertEquals('true', config.buildEnv.CI)
    }

    @Test
    void rejectsNonMappingYamlAndAgentSelectionInsideYaml() {
        [null, [], 'api', 123, [[serviceName: 'api'], [serviceName: 'other']]].each { document ->
            try {
                ServiceConfig.fromYaml(document, 'java')
                fail('Expected YAML root validation to fail')
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.message.contains('mapping'))
            }
        }
        try {
            ServiceConfig.fromYaml([serviceName: 'api', agentLabel: 'another-agent'], 'java')
            fail('Expected late agent selection to fail')
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.message.contains('deploy('))
        }
    }

    @Test
    void rejectsInvalidEnvironmentNamesTypesAndNulValuesWithoutLeakingValues() {
        [null, [], 'CI=true', [BAD: true], [BAD: 123], [BAD: null], ['BAD-NAME': 'x'], [BAD: 'private-value' + ((char) 0)]].each {
            rejects([serviceName: 'api', buildEnv: it])
        }
        try {
            new ServiceConfig([serviceName: 'api', buildEnv: [TOKEN: ['private-value']]])
            fail('Expected invalid environment value to fail')
        } catch (IllegalArgumentException expected) {
            assertFalse(expected.message.contains('private-value'))
        }
    }

    @Test
    void environmentSnapshotIsImmutableAndPreservesStringValues() {
        ServiceConfig config = new ServiceConfig([
            serviceName: 'api', buildEnv: [EMPTY: '', OPTIONS: '-Dname=api $(echo literal)']
        ])
        assertEquals(['EMPTY=', 'OPTIONS=-Dname=api $(echo literal)'], config.environmentOverrides())
        try {
            config.buildEnv.put('OTHER', 'value')
            fail('Expected immutable environment settings')
        } catch (UnsupportedOperationException expected) {
            assertEquals(2, config.buildEnv.size())
        }
    }

    @Test
    void rejectsNonStringConfigurationKeys() {
        rejects([(1): 'value', serviceName: 'api'])
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
