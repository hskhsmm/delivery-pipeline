package dev.delivery

import org.junit.Test
import static org.junit.Assert.*

class ImagePublishTest {
    @Test
    void verifyPrecedesPushAndThePushedDigestIsArchived() {
        PipelineHarness harness = service()
        harness.load('deploy').call()

        List commands = harness.events.findAll { it.type == 'sh' }
        assertEquals(2, commands.size())
        assertEquals('./gradlew --no-daemon build', commands[0].options.script)
        assertTrue(commands[1].options.returnStdout)
        assertEquals('docker.io/team/api:sha-' + harness.revision, commands[1].environment.DELIVERY_IMAGE_TAG)
        assertEquals('hub-publisher', harness.events.find { it.type == 'withCredentials' }.bindings[0].credentialsId)

        Map artifact = harness.events.find { it.type == 'writeJSON' }.options
        assertEquals('published-image.json', artifact.file)
        assertEquals('docker.io/team/api@sha256:' + ('b' * 64), artifact.json.image)
        assertEquals(harness.revision, artifact.json.sourceRevision)
        assertEquals(artifact.file, harness.events.find { it.type == 'archive' }.options.artifacts)
    }

    @Test
    void verifyFailureDoesNotAcquireRegistryCredentials() {
        PipelineHarness harness = service()
        harness.failBuild = true
        expectFailure(harness, 'build failed')
        assertFalse(harness.events.any { it.type == 'withCredentials' })
    }

    @Test
    void missingDockerfileOrRevisionFailsBeforeBindingCredentials() {
        PipelineHarness withoutDockerfile = service()
        withoutDockerfile.sourceFiles.remove('Dockerfile')
        expectFailure(withoutDockerfile, 'requires a Dockerfile')
        assertFalse(withoutDockerfile.events.any { it.type == 'withCredentials' })

        PipelineHarness withoutRevision = service()
        withoutRevision.revision = null
        expectFailure(withoutRevision, 'requires GIT_COMMIT')
        assertFalse(withoutRevision.events.any { it.type == 'withCredentials' })
    }

    @Test
    void unsuccessfulPushOrInvalidDigestDoesNotArchiveARelease() {
        PipelineHarness failed = service()
        failed.failPublish = true
        expectFailure(failed, 'publish failed')

        PipelineHarness invalid = service()
        invalid.publishMetadata = '{"containerimage.digest":"sha256:invalid"}'
        expectFailure(invalid, 'valid published image digest')
    }

    private static PipelineHarness service() {
        new PipelineHarness([
            'Dockerfile': 'FROM scratch',
            'jenkins.yml': '''serviceName: api
image:
  repository: team/api
  credentialsId: hub-publisher
'''
        ])
    }

    private static void expectFailure(PipelineHarness harness, String message) {
        try {
            harness.load('deploy').call()
            fail('Expected image publishing flow to fail')
        } catch (IllegalStateException failure) {
            assertTrue(failure.message, failure.message.contains(message))
            assertFalse(harness.events.any { it.type in ['writeJSON', 'archive'] })
        }
    }
}
