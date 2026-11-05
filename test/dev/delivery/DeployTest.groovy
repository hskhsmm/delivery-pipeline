package dev.delivery

import org.junit.Test
import static org.junit.Assert.*

class DeployTest {
    @Test
    void checksOutReadsYamlAndRunsTheSharedGradleStage() {
        PipelineHarness harness = new PipelineHarness()
        harness.load('deploy').call()
        assertEquals(['Checkout', 'Configure', 'Verify'], harness.events.findAll { it.type == 'stage' }*.name)
        assertEquals(1, harness.events.count { it.type == 'checkout' })
        assertTrue(harness.events.findIndexOf { it.type == 'checkout' } < harness.events.findIndexOf { it.type == 'readYaml' })
        assertEquals([[time: 10, unit: 'MINUTES'], [time: 45, unit: 'MINUTES']], harness.events.findAll { it.type == 'timeout' }*.options)
        Map build = harness.events.find { it.type == 'sh' }
        assertEquals('./gradlew --no-daemon build', build.options.script)
        assertEquals('-Xmx1g', build.environment.JAVA_TOOL_OPTIONS)
        assertEquals('true', build.environment.CI)
        assertEquals('8080', build.environment.PORT)
        assertEquals([CI: 'outside'], harness.environment)
    }

    @Test
    void acceptsCustomConfigPathAndBootstrapSettings() {
        PipelineHarness harness = new PipelineHarness(['.ci/jenkins.yaml': 'serviceName: api\ntimeoutMinutes: 5\n'])
        harness.load('deploy').call([agentLabel: 'java21', configFile: '.ci/jenkins.yaml', checkoutTimeoutMinutes: 2])
        assertEquals('java21', harness.events[0].label)
        assertEquals('.ci/jenkins.yaml', harness.events.find { it.type == 'readYaml' }.options.file)
        assertEquals([[time: 2, unit: 'MINUTES'], [time: 5, unit: 'MINUTES']], harness.events.findAll { it.type == 'timeout' }*.options)
    }

    @Test
    void missingYamlCannotReuseAStaleWorkspaceConfiguration() {
        PipelineHarness harness = new PipelineHarness([:])
        harness.workspace['jenkins.yml'] = 'serviceName: stale-api'
        try {
            harness.load('deploy').call()
            fail('Expected missing configuration to fail')
        } catch (IllegalStateException expected) {
            assertTrue(expected.message.contains('jenkins.yml'))
            assertFalse(harness.events.any { it.type in ['readYaml', 'sh'] })
        }
    }

    @Test
    void malformedYamlStopsBeforeBuild() {
        assertBuildNeverRuns('serviceName: [unterminated')
    }

    @Test
    void invalidYamlShapesAndSettingsStopBeforeBuild() {
        [
            '', 'orders-api', '- serviceName: api',
            'serviceName: api\n---\nserviceName: other',
            'serviceName: api\nagentLabel: ignored-agent',
            'serviceName: api\ntimeoutMinutes: "30"',
            'serviceName: api\nbuildEnv:\n  CI: true',
            'serviceName: api\nbuildEnf: {}'
        ].each { assertBuildNeverRuns(it) }
    }

    @Test
    void badBootstrapOptionsFailBeforeAgentAllocation() {
        PipelineHarness harness = new PipelineHarness()
        try {
            harness.load('deploy').call([configFile: '../other.yml'])
            fail('Expected invalid path to fail')
        } catch (IllegalArgumentException expected) {
            assertTrue(harness.events.isEmpty())
        }
    }

    @Test
    void checkoutFailurePreventsConfigurationRead() {
        PipelineHarness harness = new PipelineHarness()
        harness.failCheckout = true
        try {
            harness.load('deploy').call()
            fail('Expected checkout failure')
        } catch (IllegalStateException expected) {
            assertEquals('checkout failed', expected.message)
            assertFalse(harness.events.any { it.type in ['readYaml', 'sh'] })
        }
    }

    @Test
    void buildFailurePropagatesAndDoesNotLeakEnvironment() {
        PipelineHarness harness = new PipelineHarness()
        harness.failBuild = true
        try {
            harness.load('deploy').call()
            fail('Expected build failure')
        } catch (IllegalStateException expected) {
            assertEquals('build failed', expected.message)
            assertEquals([CI: 'outside'], harness.environment)
        }
    }

    @Test
    void successiveInvocationsDoNotRetainPreviousYamlEnvironment() {
        PipelineHarness harness = new PipelineHarness()
        harness.load('deploy').call()
        harness.sourceFiles['jenkins.yml'] = 'serviceName: other-api'
        harness.load('deploy').call()
        List builds = harness.events.findAll { it.type == 'sh' }
        assertEquals(2, builds.size())
        assertFalse(builds[1].environment.containsKey('JAVA_TOOL_OPTIONS'))
        assertEquals('outside', builds[1].environment.CI)
    }

    @Test
    void oversizedYamlStopsBeforeBuild() {
        // 주석이 아닌 실제 설정 값을 파싱할 때 code-point 제한이 적용되는지 확인한다.
        assertBuildNeverRuns('serviceName: api\nbuildEnv:\n  LARGE: "' + ('x' * 65536) + '"\n')
    }

    @Test
    void environmentStringsAreNotInsertedIntoCommandsOrLibraryLogs() {
        PipelineHarness harness = new PipelineHarness([
            'jenkins.yml': 'serviceName: api\nbuildEnv:\n  OPTIONS: "$(echo literal) -Dname=value"\n'
        ])
        harness.load('deploy').call()
        Map build = harness.events.find { it.type == 'sh' }
        assertEquals('./gradlew --no-daemon build', build.options.script)
        assertEquals('$(echo literal) -Dname=value', build.environment.OPTIONS)
        assertFalse(harness.events.find { it.type == 'echo' }.message.contains('literal'))
        assertEquals([CI: 'outside'], harness.environment)
    }

    private static void assertBuildNeverRuns(String yaml) {
        PipelineHarness harness = new PipelineHarness(['jenkins.yml': yaml])
        try {
            harness.load('deploy').call()
            fail('Expected invalid YAML to fail')
        } catch (RuntimeException expected) {
            assertFalse(harness.events.any { it.type == 'sh' })
        }
    }
}
