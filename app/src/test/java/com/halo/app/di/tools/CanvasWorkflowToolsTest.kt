package com.halo.app.di.tools

import android.app.Application
import com.halo.app.context.*
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutionContext
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CanvasWorkflowToolsTest {
    @Test fun sevenDefinitionsHaveBoundedActualOwnerContractsAndNoBodyResults() {
        val definitions = CanvasWorkflowTools(CanvasWorkflowBridge()).definitions()
        assertEquals(7, definitions.size)
        definitions.forEach { definition ->
            val contract = requireNotNull(CanvasWorkflowOperability.forDefinition(definition))
            assertEquals("CanvasEditorViewModel", contract.owner)
            val fields = contract.resultSchema["properties"] as Map<*, *>
            assertFalse(fields.containsKey("text")); assertFalse(fields.containsKey("body")); assertFalse(fields.containsKey("bytes"))
            assertEquals(false, contract.resultSchema["additionalProperties"])
            if (definition.name.contains("reviewed_text")) assertTrue(contract.sourceVersionArguments.containsAll(setOf("version", "reviewedDraftToken", "draftHash")))
            if (definition.name == "halo_canvas_export_note") assertTrue(fields.containsKey("navigationTruth"))
        }
    }
    @Test fun draftMetadataIsNotTranscriptAndSaveRequiresExactReviewedTokenAndHash() = runBlocking {
        val bridge = CanvasWorkflowBridge()
        val host = FakeHost()
        bridge.attach(host)
        val tools = CanvasWorkflowTools(bridge) { true }
        val status = tools.execute(ToolCall("status", "halo_canvas_workflow_status", mapOf("canvasId" to "board"))) as ToolExecutionResult.Success
        assertFalse(status.output.contains("raw transcript"))
        assertTrue(tools.execute(ToolCall("old", "halo_canvas_save_reviewed_text", mapOf("canvasId" to "board", "version" to "canvas:1",
            "reviewedDraftToken" to "old", "draftHash" to "a".repeat(64))), ToolExecutionContext("turn", true)) is ToolExecutionResult.Rejected)
        assertEquals(0, host.saves)
        val saved = tools.execute(ToolCall("save", "halo_canvas_save_reviewed_text", mapOf("canvasId" to "board", "version" to "canvas:1",
            "reviewedDraftToken" to "review", "draftHash" to "a".repeat(64))), ToolExecutionContext("turn", true)) as ToolExecutionResult.Success
        assertEquals("saved-card", JSONObject(saved.output).getString("nodeId"))
        assertEquals(1, host.saves)
    }
    @Test fun unscopedOrNoLongerGrownUpMutationCannotEnterCanonicalOwner() = runBlocking {
        val bridge = CanvasWorkflowBridge(); val host = FakeHost(); bridge.attach(host)
        val request = ToolCall("save", "halo_canvas_save_reviewed_text", mapOf("canvasId" to "board", "version" to "canvas:1",
            "reviewedDraftToken" to "review", "draftHash" to "a".repeat(64)))
        assertTrue(CanvasWorkflowTools(bridge) { true }.execute(request) is ToolExecutionResult.Rejected)
        assertTrue(CanvasWorkflowTools(bridge).execute(request, ToolExecutionContext("turn", true)) is ToolExecutionResult.Rejected)
        assertEquals(0, host.saves)
    }
    @Test fun detachedOrAmbiguousCanvasNeverExecutesAndDetachCancelsLongLocalWork() = runBlocking {
        val bridge = CanvasWorkflowBridge(); val host = FakeHost()
        assertTrue(runCatching { bridge.withHost("board") { _, _ -> fail("Missing host") } }.isFailure)
        val first = bridge.attach(host); val second = bridge.attach(FakeHost())
        assertTrue(runCatching { bridge.withHost("board") { _, _ -> fail("Ambiguous host") } }.isFailure)
        bridge.detach(second)
        val entered = CompletableDeferred<Unit>()
        val job = launch { bridge.withHost("board") { _, _ -> entered.complete(Unit); awaitCancellation() } }
        entered.await(); bridge.detach(first); job.join()
        assertTrue(job.isCancelled)
    }
    private class FakeHost : CanvasWorkflowHost {
        override val workflowCanvasId = "board"
        var saves = 0
        override suspend fun workflowStatus() = CanvasWorkflowStatus("board", "canvas:1", false, "review", "a".repeat(64), 14, "source", "file-v1")
        override suspend fun workflowPalette(version: String, nodeId: String, fileVersion: String, guard: () -> Unit) = error("unused")
        override suspend fun workflowTranscribe(version: String, nodeId: String, fileVersion: String, guard: () -> Unit) = error("unused")
        override suspend fun workflowReviewedText(version: String, token: String, draftHash: String, action: String, text: String?, guard: () -> Unit): CanvasWorkflowReceipt {
            check(token == "review" && draftHash == "a".repeat(64)); guard(); saves++
            return CanvasWorkflowReceipt("board", "canvas:2", "SAVED", nodeId = "saved-card")
        }
        override suspend fun workflowExport(version: String, startStudy: Boolean, guard: () -> Unit) = error("unused")
    }
}
