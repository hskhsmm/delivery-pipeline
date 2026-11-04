package dev.delivery

import org.junit.Test
import static org.junit.Assert.*

/** A small step harness: no Jenkins server, shell execution, or network access. */
class ServicePipelineTest {
    @Test
    void checksOutBeforeRunningGradleWithinTimeout() {
        List events = []
        Script pipeline = loadPipeline(events)
        pipeline.call([serviceName: 'orders-api'])

        assertEquals(['node', 'timeout', 'stage', 'clean', 'checkout', 'stage', 'echo', 'sh'], events*.type)
        assertEquals('java', events[0].label)
        assertEquals([time: 30, unit: 'MINUTES'], events[1].options)
        assertEquals(['Checkout', 'Verify'], events.findAll { it.type == 'stage' }*.name)
        assertEquals('./gradlew --no-daemon build', events.last().options.script)
    }

    @Test
    void respectsExplicitAgentAndTimeout() {
        List events = []
        loadPipeline(events).call([serviceName: 'payments-api', agentLabel: 'java21', timeoutMinutes: 60])
        assertEquals('java21', events[0].label)
        assertEquals([time: 60, unit: 'MINUTES'], events[1].options)
        assertEquals('./gradlew --no-daemon build', events.last().options.script)
    }

    @Test
    void rejectsBadConfigurationBeforeAllocatingAgent() {
        List events = []
        try {
            loadPipeline(events).call([serviceName: 'api', agentLable: 'java'])
            fail('Expected configuration validation to fail')
        } catch (IllegalArgumentException expected) {
            assertTrue(events.isEmpty())
        }
    }

    @Test
    void propagatesBuildFailure() {
        List events = []
        try {
            loadPipeline(events, false, true).call([serviceName: 'api'])
            fail('Expected build failure')
        } catch (IllegalStateException expected) {
            assertEquals('build failed', expected.message)
        }
    }

    @Test
    void checkoutFailurePreventsBuildExecution() {
        List events = []
        try {
            loadPipeline(events, true).call([serviceName: 'api'])
            fail('Expected checkout failure')
        } catch (IllegalStateException expected) {
            assertEquals('checkout failed', expected.message)
            assertFalse(events.any { it.type == 'sh' })
        }
    }

    private Script loadPipeline(List events, boolean failCheckout = false, boolean failBuild = false) {
        Binding binding = new Binding([
            scm: [name: 'service-repository'],
            node: { String label, Closure body ->
                events << [type: 'node', label: label]
                body.call()
            },
            timeout: { Map options, Closure body ->
                events << [type: 'timeout', options: options]
                body.call()
            },
            stage: { String name, Closure body ->
                events << [type: 'stage', name: name]
                body.call()
            },
            deleteDir: { -> events << [type: 'clean'] },
            checkout: { Object scm ->
                events << [type: 'checkout', scm: scm]
                if (failCheckout) throw new IllegalStateException('checkout failed')
            },
            echo: { String message -> events << [type: 'echo', message: message] },
            sh: { Map options ->
                events << [type: 'sh', options: options]
                if (failBuild) throw new IllegalStateException('build failed')
            }
        ])
        return new GroovyShell(getClass().classLoader, binding).parse(new File('vars/servicePipeline.groovy'))
    }
}
