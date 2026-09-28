package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.agent.ActionTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AGENTIC-001 machine-readable results.
 *
 * A small local model decides what to do next from these fields, so their presence
 * and honesty matter more than the prose that follows.
 */
class ToolResultEnvelopeTest {

    @Test
    fun `success carries status truth and reason code before any prose`() {
        val result = ToolResultEnvelope.success(
            actionTruth = ActionTruth.DISPATCHED,
            details = "alarm_hour=7",
        )

        val lines = result.lines()
        assertEquals("tool_result:", lines[0])
        assertEquals("status=success", lines[1])
        assertEquals("action_truth=DISPATCHED", lines[2])
        assertEquals("reason_code=NONE", lines[3])
        assertTrue(result.contains("alarm_hour=7"))
    }

    @Test
    fun `dispatch is never upgraded to confirmation`() {
        val result = ToolResultEnvelope.success(actionTruth = ActionTruth.DISPATCHED)

        assertTrue(result.contains("action_truth=DISPATCHED"))
        assertTrue(!result.contains("action_truth=CONFIRMED"))
    }

    @Test
    fun `failure states retryability and user action explicitly`() {
        val result = ToolResultEnvelope.failure(
            reasonCode = "PERMISSION_REQUIRED",
            retryable = false,
            requiresUserAction = true,
            details = "contacts permission is missing",
        )

        assertTrue(result.contains("status=failed"))
        assertTrue(result.contains("reason_code=PERMISSION_REQUIRED"))
        assertTrue(result.contains("retryable=false"))
        assertTrue(result.contains("requires_user_action=true"))
    }

    @Test
    fun `rejection never claims the action happened`() {
        val result = ToolResultEnvelope.rejected(
            reasonCode = "UNSUPPORTED_OPERATION",
            requiresUserAction = true,
            details = "exact future dates are not supported",
        )

        assertTrue(result.contains("status=rejected"))
        assertTrue(result.contains("action_truth=PROPOSED"))
    }

    @Test
    fun `host tool output that predates the envelope gains a header`() {
        val enveloped = ToolResultEnvelope.ensureEnveloped("DISPATCHED", ActionTruth.DISPATCHED)

        assertTrue(enveloped.startsWith("tool_result:"))
        assertTrue(enveloped.contains("status=success"))
        assertTrue(enveloped.contains("DISPATCHED"))

        // Already-enveloped output is returned unchanged, never double-headed.
        assertEquals(enveloped, ToolResultEnvelope.ensureEnveloped(enveloped, ActionTruth.FAILED))
    }

    @Test
    fun `rejection kinds map to their own reason code instead of invalid arguments`() {
        val rejected = ToolExecutionResult.Rejected(
            toolCallId = "call-1",
            toolId = "halo_read_screen",
            reason = "Halo's accessibility service is not enabled",
            kind = ToolRejectionKind.PERMISSION_REQUIRED,
        )

        val enveloped = rejected.enveloped()
        assertTrue(enveloped.reason.contains("reason_code=PERMISSION_REQUIRED"))
        assertTrue(enveloped.reason.contains("requires_user_action=true"))

        val unsupported = rejected.copy(kind = ToolRejectionKind.UNSUPPORTED_OPERATION).enveloped()
        assertTrue(unsupported.reason.contains("reason_code=UNSUPPORTED_OPERATION"))
    }

    @Test
    fun `failed results keep the unknown-completion truth visible`() {
        val failed = ToolExecutionResult.Failed(
            toolCallId = "call-2",
            toolId = "halo_open_app",
            message = "Tool execution timed out; completion unknown",
            completionUnknown = true,
            failureKind = ToolFailureKind.TIMEOUT,
            code = "TIMEOUT",
        )

        val enveloped = failed.enveloped()
        assertTrue(enveloped.message.contains("action_truth=UNKNOWN"))
        assertTrue(enveloped.message.contains("reason_code=TIMEOUT"))
    }
}
