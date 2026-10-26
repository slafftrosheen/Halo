package com.halo.app.context

import android.app.Application
import com.halo.app.di.tools.host.CurrentScreenTools
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.*
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
class CurrentScreenRemotePolicyTest {
    private fun owner() = CurrentScreenContext().apply {
        val lease = attach("page")
        publish(lease, CurrentScreenMetadata("notebook/note/{noteId}", CurrentScreenSuite.OFFICE,
            "note", "note-1", "7", listOf("block-1"), listOf("page.note.find")))
    }
    private val call = ToolCall("read", CurrentScreenTools.NAME, emptyMap())

    @Test fun acknowledgedRemoteMetadataStillRequiresForegroundAccessAndReturnsNoContent() = runBlocking {
        var sharing = false; var foreground = true
        val tool = CurrentScreenTools(owner(), remoteReadAllowed = { sharing }) { foreground }
        withContext(RunToolScope(AgentRunId("remote"), targetIsLocal = false)) {
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
            sharing = true
            val value = tool.execute(call) as ToolExecutionResult.Success
            val json = JSONObject(value.output)
            assertEquals("note-1", json.getString("objectId"))
            assertFalse(json.has("text")); assertFalse(json.has("title"))
            assertFalse(json.getBoolean("contentRead")); assertFalse(json.getBoolean("authorityGranted"))
            foreground = false
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
            foreground = true; sharing = false
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        }
    }
    @Test fun remoteConsentRevokedBetweenAdmissionAndReturnWithholdsSnapshot() = runBlocking {
        var checks = 0
        val tool = CurrentScreenTools(owner(), remoteReadAllowed = { ++checks == 1 }) { true }
        withContext(RunToolScope(AgentRunId("remote"), targetIsLocal = false)) {
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
            assertEquals(2, checks)
        }
    }
    @Test fun localMetadataDoesNotRequireRemoteConsentAndDefaultRemoteRemainsDenied() = runBlocking {
        val tool = CurrentScreenTools(owner()) { true }
        withContext(RunToolScope(AgentRunId("local"), targetIsLocal = true)) {
            assertTrue(tool.execute(call) is ToolExecutionResult.Success)
        }
        withContext(RunToolScope(AgentRunId("remote"), targetIsLocal = false)) {
            assertTrue(tool.execute(call) is ToolExecutionResult.Rejected)
        }
    }
}
