package dev.delivery

import org.junit.Test
import static org.junit.Assert.*

class ImageConfigTest {
    @Test
    void yamlEnablesPublishingOnlyWhenImageIsConfigured() {
        assertNull(ServiceConfig.fromYaml([serviceName: 'api'], 'java').image)
        ImageConfig image = ServiceConfig.fromYaml([
            serviceName: 'api', image: [repository: 'team/my-api', credentialsId: 'hub']
        ], 'java').image
        assertEquals('docker.io/team/my-api', image.repository)
        assertEquals('hub', image.credentialsId)
        assertEquals(image.repository, new ImageConfig([
            repository: image.repository, credentialsId: 'hub'
        ]).repository)
    }

    @Test
    void rejectsInvalidPublishConfiguration() {
        [
            null, [], [repository: 'team/api'], [repository: 'team/api', credentialsId: ' '],
            [repository: 'team/api', credentialsId: 'hub', password: 'placeholder'],
            [repository: 'registry.example.com/team/api', credentialsId: 'hub'],
            [repository: 'team/api:latest', credentialsId: 'hub'],
            [repository: 'team/api@sha256:abcd', credentialsId: 'hub'],
            [repository: 'Team/api', credentialsId: 'hub']
        ].each { value ->
            try {
                ServiceConfig.fromYaml([serviceName: 'api', image: value], 'java')
                fail('Expected invalid image configuration')
            } catch (IllegalArgumentException expected) {
                assertFalse(expected.message.contains('placeholder'))
            }
        }
    }
}
