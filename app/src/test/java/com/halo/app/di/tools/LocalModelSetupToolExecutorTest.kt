package com.halo.app.di.tools

import com.halo.app.ai.*
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LocalModelSetupToolExecutorTest {
    @Test fun `registered setup feature has complete contracts and required rollback approval`() {
        val tools = LocalModelSetupToolExecutor(Owner())
        val descriptors = tools.definitions().map { requireNotNull(HaloToolDescriptors.modelSetup(it)) }
        val registry = ToolRegistry(listOf(tools), descriptors)
        assertEquals(emptyList<ToolOperabilityProblem>(), registry.operabilityReport(
            listOf(ToolFeatureOperability("local-setup", descriptors.map { it.name }.toSet()))))
        assertEquals(ConfirmationPolicy.ALWAYS, registry.descriptor("halo_local_setup_rollback")!!.security.confirmation)
        assertEquals(ConfirmationPolicy.ALWAYS, registry.descriptor("halo_local_setup_start")!!.security.confirmation)
        assertEquals("sourceReviewSummary", registry.descriptor("halo_local_setup_start")!!.presentation!!.reviewArgument)
        descriptors.filter { it.name != "halo_local_setup_status" }.forEach {
            assertEquals(ReplaySafety.NEVER_REPLAY, it.security.replaySafety)
        }
        assertTrue(ToolDiscoveryService(registry).expand(listOf("halo_local_setup_start"))
            .single { it.name == "halo_local_setup_start" }.description.contains("Operation contract v1"))
    }
    private class Owner(override var sourceReviewAcknowledged: Boolean = true) : LocalRolePackControl {
        var reviewWrites = 0
        override fun acknowledgeSourceReview(manifestFingerprint: String, acknowledged: Boolean): Boolean {
            if (manifestFingerprint != PrivateOfflinePack.fingerprint) return false
            reviewWrites++
            sourceReviewAcknowledged = acknowledged
            return true
        }
        val extensionRequests = mutableListOf<Pair<LocalRolePackCommand, String>>()
        override fun requestConversationExtension(command: LocalRolePackCommand, expectedTransactionId: String): Boolean {
            extensionRequests += command to expectedTransactionId
            return accepted
        }
        override val state = MutableStateFlow(LocalRolePackInstallState())
        override var available = true
        var accepted = true
        var preview: LocalRolePackDownloadPreview? = null
        override suspend fun downloadPreview() = preview
        val requests = mutableListOf<Pair<LocalRolePackCommand, String>>()
        override fun request(command: LocalRolePackCommand, expectedTransactionId: String): Boolean {
            requests += command to expectedTransactionId
            return accepted
        }
    }
    @Test fun `download review separates network from per volume storage without starting setup`() = runBlocking {
        val owner = Owner().apply { preview = LocalRolePackDownloadPreview(
            listOf(LocalRolePackDownloadEstimate(LocalRolePackStage.INTENT, 100L),
                LocalRolePackDownloadEstimate(LocalRolePackStage.SPEECH_OUTPUT, 200L)),
            listOf(LocalRolePackStorageEstimate(400L, 399L), LocalRolePackStorageEstimate(50L, 1000L))) }
        val result = LocalModelSetupToolExecutor(owner).execute(
            ToolCall("review", "halo_local_setup_status", emptyMap())) as ToolExecutionResult.Success
        val json = org.json.JSONObject(result.output)
        val estimate = json.getJSONObject("downloadPreview")
        assertEquals(300L, estimate.getLong("estimatedRemainingDownloadBytes"))
        assertEquals(450L, estimate.getLong("estimatedRequiredStorageBytes"))
        // Spare space on a second volume cannot make the first volume safe.
        assertFalse(estimate.getBoolean("enoughStorageAtObservation"))
        assertFalse(estimate.getBoolean("chatModelIncluded"))
        assertFalse(json.getBoolean("qualificationGranted"))
        assertFalse(json.getBoolean("roleSelectionChanged"))
        assertTrue(owner.requests.isEmpty())
        assertEquals(LocalRolePackPhase.IDLE, owner.state.value.phase)
    }

    @Test fun `unknown download estimate stays unknown instead of zero bytes or ready`() = runBlocking {
        val owner = Owner()
        val result = LocalModelSetupToolExecutor(owner).execute(
            ToolCall("review", "halo_local_setup_status", emptyMap())) as ToolExecutionResult.Success
        val json = org.json.JSONObject(result.output)
        assertTrue(json.isNull("downloadPreview"))
        assertFalse(json.getBoolean("qualificationGranted"))
        assertTrue(owner.requests.isEmpty())
    }

    @Test fun `invalid arguments never reach installer`() = runBlocking {
        val owner = Owner()
        val tools = LocalModelSetupToolExecutor(owner)
        for (args in listOf(emptyMap(), mapOf("transactionId" to "../unknown"), mapOf("transactionId" to "NONE", "url" to "https://example.com"))) {
            assertTrue(tools.execute(ToolCall("test", "halo_local_setup_start", args)) is ToolExecutionResult.Rejected)
        }
        assertTrue(owner.requests.isEmpty())
    }
    @Test fun `unavailable setup never starts and changed receipt rejects`() = runBlocking {
        val owner = Owner().apply { available = false }
        val tools = LocalModelSetupToolExecutor(owner)
        val call = ToolCall("test", "halo_local_setup_start", mapOf("transactionId" to "NONE"))
        assertTrue(tools.execute(call) is ToolExecutionResult.Rejected)
        assertTrue(owner.requests.isEmpty())
        owner.available = true
        owner.accepted = false
        assertTrue(tools.execute(call) is ToolExecutionResult.Rejected)
        assertEquals(listOf(LocalRolePackCommand.START_OR_RESUME to "NONE"), owner.requests)
    }
    @Test fun `every mutation dispatches through same owner with exact observed token`() = runBlocking {
        val owner = Owner()
        val tools = LocalModelSetupToolExecutor(owner)
        val token = "b88fe002-14e0-48dc-85bd-6dbcd038f709"
        listOf("start", "pause", "rollback", "keep_files").forEach {
            assertTrue(tools.execute(ToolCall("test", "halo_local_setup_$it", mapOf("transactionId" to token))) is ToolExecutionResult.Success)
        }
        assertEquals(LocalRolePackCommand.entries.toList(), owner.requests.map { it.first })
        assertTrue(owner.requests.all { it.second == token })
        assertEquals(LocalRolePackInstallState(), owner.state.value)
    }
    @Test fun `fingerprint alone cannot acknowledge review or reach either acquisition scope`() = runBlocking {
        for (scope in listOf("STARTER", "CONVERSATION_EXTENSION")) {
            val owner = Owner(false)
            val result = LocalModelSetupToolExecutor(owner).execute(ToolCall("start", "halo_local_setup_start", mapOf(
                "transactionId" to "NONE", "scope" to scope,
                "reviewedManifestFingerprint" to PrivateOfflinePack.fingerprint)))
            assertTrue(result is ToolExecutionResult.Rejected)
            assertFalse(owner.sourceReviewAcknowledged)
            assertEquals(0, owner.reviewWrites)
            assertTrue(owner.requests.isEmpty()); assertTrue(owner.extensionRequests.isEmpty())
        }
    }

    @Test fun `review text is complete bounded localized and bound to concrete scope`() = runBlocking {
        for (language in listOf("en", "ru", "lv", "pl")) {
            val owner = Owner(false)
            val tools = LocalModelSetupToolExecutor(owner, reviewLanguage = { language })
            val summaries = listOf("STARTER", "CONVERSATION_EXTENSION").map { scope ->
                val result = tools.execute(ToolCall("review", "halo_local_setup_status",
                    mapOf("view" to "SOURCE_REVIEW", "scope" to scope))) as ToolExecutionResult.Success
                assertTrue(result.output.length <= 3800)
                val json = org.json.JSONObject(result.output)
                assertEquals(scope, json.getString("scope"))
                assertFalse(json.getBoolean("actionStatusIncluded"))
                assertFalse(json.getBoolean("licenseAuditComplete"))
                val summary = json.getString("sourceReviewSummary")
                assertTrue(summary.length in 1..2000)
                assertTrue(summary.contains("[$scope]"))
                assertTrue(summary.contains(PrivateOfflinePack.fingerprint))
                (PrivateOfflinePack.artifacts + PrivateOfflinePack.optionalArtifacts).forEach { artifact ->
                    assertTrue(summary.contains(artifact.id)); assertTrue(summary.contains(artifact.source))
                }
                assertEquals(PrivateOfflinePack.sourceReviewSummary(scope, language), summary)
                summary
            }
            assertNotEquals(summaries[0], summaries[1])
            assertFalse(owner.sourceReviewAcknowledged); assertEquals(0, owner.reviewWrites)
        }
    }

    @Test fun `wrong scope or modified approval never persists review and exact scope dispatches`() = runBlocking {
        val owner = Owner(false)
        val tools = LocalModelSetupToolExecutor(owner, reviewLanguage = { "lv" })
        val starter = requireNotNull(PrivateOfflinePack.sourceReviewSummary("STARTER", "lv"))
        val extension = requireNotNull(PrivateOfflinePack.sourceReviewSummary("CONVERSATION_EXTENSION", "lv"))
        val base = mapOf("transactionId" to "NONE", "scope" to "CONVERSATION_EXTENSION",
            "reviewedManifestFingerprint" to PrivateOfflinePack.fingerprint)
        for (summary in listOf(starter, extension + " changed")) {
            assertTrue(tools.execute(ToolCall("start", "halo_local_setup_start",
                base + ("sourceReviewSummary" to summary))) is ToolExecutionResult.Rejected)
        }
        assertEquals(0, owner.reviewWrites); assertTrue(owner.extensionRequests.isEmpty())
        assertTrue(tools.execute(ToolCall("start", "halo_local_setup_start",
            base + ("sourceReviewSummary" to extension))) is ToolExecutionResult.Success)
        assertTrue(owner.sourceReviewAcknowledged); assertEquals(1, owner.reviewWrites)
        assertEquals(listOf(LocalRolePackCommand.START_OR_RESUME to "NONE"), owner.extensionRequests)
        assertTrue(owner.requests.isEmpty())
    }

}
