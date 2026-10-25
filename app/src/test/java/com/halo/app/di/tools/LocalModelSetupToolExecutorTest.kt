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
        descriptors.filter { it.name != "halo_local_setup_status" }.forEach {
            assertEquals(ReplaySafety.NEVER_REPLAY, it.security.replaySafety)
        }
        assertTrue(ToolDiscoveryService(registry).expand(listOf("halo_local_setup_start"))
            .single { it.name == "halo_local_setup_start" }.description.contains("Operation contract v1"))
    }
    private class Owner : LocalRolePackControl {
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
}
