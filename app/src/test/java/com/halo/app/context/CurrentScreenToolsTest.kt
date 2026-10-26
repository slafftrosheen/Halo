package com.halo.app.context

import android.app.Application
import com.halo.app.di.tools.host.CurrentScreenTools
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.RunToolScope
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CurrentScreenToolsTest {
    @Test fun onlyExplicitScopedLocalForegroundReadsReturnMetadata() = runBlocking {
        val owner = CurrentScreenContext(); val lease = owner.attach("entry")
        owner.publish(lease, CurrentScreenMetadata("notebook/note/{noteId}", CurrentScreenSuite.OFFICE,
            "note", "note-1", "7", listOf("block-1"), listOf("page.note.find")))
        var allowed = true
        val tool = CurrentScreenTools(owner) { allowed }
        val call = ToolCall("read", CurrentScreenTools.NAME, emptyMap())
        assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        withContext(RunToolScope(AgentRunId("run"), targetIsLocal = false)) {
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        }
        withContext(RunToolScope(AgentRunId("run"), targetIsLocal = true)) {
            val result = tool.execute(call) as ToolExecutionResult.Success
            val json = JSONObject(result.output)
            assertEquals("note-1", json.getString("objectId"))
            assertFalse(json.has("title")); assertFalse(json.has("text"))
            assertFalse(json.getBoolean("contentRead")); assertFalse(json.getBoolean("authorityGranted"))
            assertTrue(tool.execute(call.copy(arguments = mapOf("objectId" to "other"))) is ToolExecutionResult.Rejected)
            assertTrue(tool.execute(call.copy(arguments = mapOf("expectedSnapshot" to "stale"))) is ToolExecutionResult.Rejected)
            allowed = false
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
            allowed = true; owner.detach(lease)
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        }
    }
    @Test fun registeredReadContractIsClosedAndDoesNotDeclareAnActionExecutor() {
        val tool = CurrentScreenTools(CurrentScreenContext()) { true }
        val descriptors = tool.definitions().map { requireNotNull(com.halo.app.di.tools.HaloToolDescriptors.assistant(it)) }
        val problems = com.halo.runtime.cognition.tools.ToolRegistry(listOf(tool), descriptors).operabilityReport(
            listOf(com.halo.runtime.cognition.tools.ToolFeatureOperability("current-screen-metadata", setOf(CurrentScreenTools.NAME))))
        assertTrue(problems.toString(), problems.isEmpty())
        assertEquals(com.halo.runtime.cognition.tools.ToolOperation.QUERY, descriptors.single().operability?.operation)
        assertEquals(false, tool.definitions().single().parameters["additionalProperties"])
    }

    @Test fun permissionRevocationBeforeReturnWithholdsMetadata() = runBlocking {
        val owner = CurrentScreenContext(); val lease = owner.attach("entry")
        owner.publish(lease, CurrentScreenMetadata("home", CurrentScreenSuite.HOME, "chat"))
        var reads = 0
        val tool = CurrentScreenTools(owner) { ++reads == 1 }
        withContext(RunToolScope(AgentRunId("run"), targetIsLocal = true)) {
            assertTrue(tool.execute(ToolCall("read", CurrentScreenTools.NAME, emptyMap())) is ToolExecutionResult.Rejected)
        }
    }

    @Test fun oversizedSelectionReadReportsCountAndTruncationWithoutContent() = runBlocking {
        val owner = CurrentScreenContext(); val lease = owner.attach("canvas")
        val ids = List(130) { "node-$it" }
        owner.publish(lease, CurrentScreenMetadata("notebook/canvas/{canvasId}", CurrentScreenSuite.STUDIO,
            "canvas", "canvas-1", selectionIds = ids.take(100), selectionCount = ids.size, selectionTruncated = true), ids)
        val tool = CurrentScreenTools(owner) { true }
        withContext(RunToolScope(AgentRunId("large-selection"), targetIsLocal = true)) {
            val result = tool.execute(ToolCall("read", CurrentScreenTools.NAME, emptyMap())) as ToolExecutionResult.Success
            val json = JSONObject(result.output)
            assertEquals(130, json.getInt("selectionCount"))
            assertTrue(json.getBoolean("selectionTruncated"))
            assertEquals(100, json.getJSONArray("selectionIds").length())
            assertFalse(json.has("text")); assertFalse(json.has("title")); assertFalse(json.getBoolean("contentRead"))
            assertFalse(result.output.contains("node-129"))
        }
    }

}
