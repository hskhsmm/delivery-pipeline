package dev.delivery

import org.junit.Test
import static org.junit.Assert.*

class GitOpsTest {
    private static final String IMAGE = 'docker.io/team/api@sha256:' + ('b' * 64)
    private static final String MANIFEST = 'apps/api/deployment.yaml'

    @Test
    void passesTheArchivedDigestToGitOpsAndPreservesTheManifestFormatting() {
        PipelineHarness harness = service()
        harness.load('deploy').call()

        assertEquals(['Checkout', 'Configure', 'Verify', 'Publish Image', 'Open GitOps PR'],
            harness.events.findAll { it.type == 'stage' }*.name)
        assertEquals([[credentialsId: 'gitops-writer', variable: 'GH_TOKEN']],
            harness.events.findAll { it.type == 'withCredentials' }.last().bindings)
        Map open = harness.events.find { it.environment?.DELIVERY_GITOPS_ACTION == 'open' }
        assertTrue(harness.events.indexOf(open) > harness.events.findIndexOf { it.type == 'archive' })
        assertEquals(IMAGE, open.environment.DELIVERY_GITOPS_IMAGE)
        assertEquals(harness.revision, open.environment.DELIVERY_GITOPS_REVISION)
        assertEquals([file: MANIFEST, text: deployment(IMAGE)],
            harness.events.find { it.type == 'writeFile' }.options)
    }

    @Test
    void doesNotWriteOrOpenAPullRequestForAnAlreadyDeployedDigest() {
        PipelineHarness harness = service()
        harness.gitOpsFiles[MANIFEST] = deployment(IMAGE)
        harness.load('deploy').call()

        assertFalse(harness.events.any { it.type == 'writeFile' || it.environment?.DELIVERY_GITOPS_ACTION == 'open' })
        assertTrue(harness.events.any { it.message == 'GitOps main already references the published image' })
    }

    @Test
    void leavesFeatureBranchesAndPullRequestsAtVerify() {
        PipelineHarness feature = service()
        feature.gitBranch = 'origin/feature'
        PipelineHarness pullRequest = service()
        pullRequest.jobEnvironment.putAll([BRANCH_NAME: 'main', CHANGE_ID: '42'])

        for (PipelineHarness harness : [feature, pullRequest]) {
            harness.load('deploy').call()
            assertEquals(['Checkout', 'Configure', 'Verify'], harness.events.findAll { it.type == 'stage' }*.name)
            assertFalse(harness.events.any { it.type == 'withCredentials' })
        }
    }

    @Test
    void failedPushDoesNotAcquireTheGitHubToken() {
        PipelineHarness harness = service()
        harness.failPublish = true
        expectFailure(harness, 'publish failed')
        assertEquals(['hub-publisher'], harness.events.findAll { it.type == 'withCredentials' }.collect {
            it.bindings[0].credentialsId
        })
    }

    @Test
    void rejectsADeploymentForAnotherServiceBeforeCreatingAPullRequest() {
        PipelineHarness harness = service()
        harness.gitOpsFiles[MANIFEST] = deployment('team/api:old').replace('name: api', 'name: another-api')
        expectFailure(harness, 'Deployment named serviceName')
        assertFalse(harness.events.any { it.type == 'writeFile' || it.environment?.DELIVERY_GITOPS_ACTION == 'open' })
    }

    @Test
    void rejectsInvalidGitOpsConfigurationBeforeBuilding() {
        PipelineHarness unsafePath = service()
        unsafePath.sourceFiles['jenkins.yml'] = unsafePath.sourceFiles['jenkins.yml'].replace(MANIFEST, '../deployment.yaml')
        expectFailure(unsafePath, 'gitOps.manifest must be a relative repository path')
        PipelineHarness noImage = service()
        noImage.sourceFiles['jenkins.yml'] = 'serviceName: api\n' +
            noImage.sourceFiles['jenkins.yml'].split('(?=gitOps:)')[1]
        expectFailure(noImage, 'gitOps requires image publishing')
        for (PipelineHarness harness : [unsafePath, noImage]) {
            assertFalse(harness.events.any { it.type == 'sh' })
        }
    }

    private static PipelineHarness service() {
        PipelineHarness harness = new PipelineHarness([
            'Dockerfile': 'FROM scratch',
            'jenkins.yml': '''serviceName: api
image:
  repository: team/api
  credentialsId: hub-publisher
gitOps:
  repository: team/environments
  manifest: apps/api/deployment.yaml
  credentialsId: gitops-writer
'''
        ])
        harness.gitOpsFiles[MANIFEST] = deployment('team/api:old')
        return harness
    }

    private static String deployment(String image) {
        """apiVersion: apps/v1
kind: Deployment
metadata:
  name: api
spec:
  template:
    spec:
      containers:
        - name: api
          image: '${image}' # deployment reference
        - name: sidecar
          image: team/sidecar:v1
"""
    }

    private static void expectFailure(PipelineHarness harness, String message) {
        try {
            harness.load('deploy').call()
            fail('Expected GitOps flow to fail')
        } catch (RuntimeException failure) {
            assertTrue(failure.message, failure.message.contains(message))
        }
    }
}
