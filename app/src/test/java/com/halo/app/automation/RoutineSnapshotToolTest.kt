package com.halo.app.automation

import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineSnapshotToolTest {
    @Test fun onlyTheReviewedSectionIsReadAndReturned() = runTest {
        var read: RoutineSnapshotSection? = null
        val tool = RoutineSnapshotTool { section -> read = section; "Tomorrow has one event." }
        val invalid = tool.execute(ToolCall("invalid", RoutineSnapshotTool.ID, mapOf("section" to "TOMORROW", "send" to true)))
        assertEquals(ToolRejectionKind.INVALID_ARGUMENTS, (invalid as ToolExecutionResult.Rejected).kind)
        assertNull(read)
        val result = tool.execute(ToolCall("read", RoutineSnapshotTool.ID, mapOf("section" to "TOMORROW")))
        assertEquals(RoutineSnapshotSection.TOMORROW, read)
        assertEquals("Tomorrow has one event.", (result as ToolExecutionResult.Success).output)
    }

    @Test fun reviewedFallbackReportsMissingDataButNeverCoversAnAccessFailure() = runTest {
        val call = ToolCall("read", RoutineSnapshotTool.ID, mapOf("section" to "WEATHER", "allow_unavailable" to true))
        val missing = RoutineSnapshotTool(unavailable = { "Saved weather unavailable" }) { null }
        val denied = RoutineSnapshotTool { error("Adult session changed") }
        assertEquals("Saved weather unavailable", (missing.execute(call) as ToolExecutionResult.Success).output)
        assertEquals(ToolRejectionKind.RESOURCE_UNAVAILABLE, (denied.execute(call) as ToolExecutionResult.Rejected).kind)
    }

    @Test fun unavailableExternalSnapshotIsBlockedInsteadOfReportedAsCompleted() = runTest {
        val tool = RoutineSnapshotTool { null }
        val result = tool.execute(ToolCall("read", RoutineSnapshotTool.ID, mapOf("section" to "WEATHER")))
        assertEquals(ToolRejectionKind.RESOURCE_UNAVAILABLE, (result as ToolExecutionResult.Rejected).kind)
    }
}
