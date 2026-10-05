package com.halo.runtime.cognition.native

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.memory.*
import com.halo.runtime.cognition.tools.CapabilityId
import com.halo.runtime.cognition.turn.ToolTurnResult
import com.halo.sdk.provider.AiResponse
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class NativeActionTruthTest {
    private val now = Instant.parse("2026-10-04T10:00:00Z")
    private val id = AgentRunId("run")
    private val container = ToolTurnResult.Completed(AiResponse(text = "", providerId = "host"), false, emptyList())
    private fun run(step: AgentStep?, state: AgentRunState = AgentRunState.COMPLETED) =
        AgentRun(id, InteractionId("interaction"), "native", state, createdAt = now, updatedAt = now, steps = listOfNotNull(step))
    private fun receipt(success: Boolean, truth: ActionTruth) = AgentStep.ToolOutcomeRecorded(
        AgentStepId("outcome"), id, now, ToolInvocationId("invocation"), "call", "halo_open_app", success, "", truth, null)

    @Test fun rejectedCompletedContainerMatchesBlockedEpisodeAndCannotSpeakSuccess() {
        val rejected = AgentStep.ToolRejected(AgentStepId("reject"), id, now, "call", "halo_open_app",
            ToolRejectionCode.CAPABILITY_UNAVAILABLE, "No launchable app", false, true, "")
        val run = run(rejected, AgentRunState.FAILED)
        val outcome = resolveNativeActionOutcome(run, "call", container)
        assertEquals(NativeActionOutcome.REJECTED, outcome)
        assertEquals(EpisodeOutcome.BLOCKED, EpisodeBuilder().build(run)!!.outcome)
        val response = NativeCapabilityResult(NativeCapabilityRequest("interaction", CapabilityId("host.application.launch"),
            "halo_open_app", emptyMap(), "Open"), id, container, outcome).responseText()
        assertFalse(response.contains("dispatched", true))
        assertFalse(response.contains("confirmed", true))
    }

    @Test fun durableReceiptMatrix() {
        for ((truth, expected) in listOf(ActionTruth.CONFIRMED to NativeActionOutcome.CONFIRMED,
            ActionTruth.DISPATCHED to NativeActionOutcome.DISPATCHED, ActionTruth.ACKNOWLEDGED to NativeActionOutcome.DISPATCHED,
            ActionTruth.UNKNOWN to NativeActionOutcome.UNKNOWN, ActionTruth.FAILED to NativeActionOutcome.FAILED)) {
            assertEquals(expected, resolveNativeActionOutcome(run(receipt(truth != ActionTruth.FAILED, truth)), "call", container))
        }
        assertEquals(NativeActionOutcome.FAILED, resolveNativeActionOutcome(run(receipt(false, ActionTruth.CONFIRMED)), "call", container))
        assertEquals(NativeActionOutcome.UNKNOWN, resolveNativeActionOutcome(run(receipt(true, ActionTruth.CONFIRMED)), "another-call", container))
        assertEquals(NativeActionOutcome.FAILED, resolveNativeActionOutcome(run(receipt(true, ActionTruth.CONFIRMED), AgentRunState.FAILED), "call", container))
        assertEquals(NativeActionOutcome.FAILED, resolveNativeActionOutcome(run(null, AgentRunState.FAILED), "call", container))
    }

    @Test fun waitStatesAndCurrentRejectionOutrankOldSuccess() {
        assertEquals(NativeActionOutcome.WAITING_FOR_RESOURCE, resolveNativeActionOutcome(run(null, AgentRunState.EXECUTING), "call", ToolTurnResult.WaitingForResource(id)))
        assertEquals(NativeActionOutcome.WAITING_FOR_CAPABILITY, resolveNativeActionOutcome(run(null, AgentRunState.EXECUTING), "call", ToolTurnResult.WaitingForCapability(id)))
        val approval = AgentInputRequest.Approval(AgentInputRequestId("approval"), id, now, invocationId = ToolInvocationId("inv"),
            toolId = com.halo.runtime.cognition.tools.ToolId("halo_open_app"), action = "Open", risk = "REVERSIBLE", argumentsDigest = "digest")
        assertEquals(NativeActionOutcome.WAITING_FOR_INPUT, resolveNativeActionOutcome(run(null, AgentRunState.WAITING_FOR_APPROVAL), "call", ToolTurnResult.WaitingForInput(approval)))
        val rejection = AgentStep.ToolRejected(AgentStepId("reject"), id, now, "call", "halo_open_app", ToolRejectionCode.PERMISSION_REQUIRED, "Permission", false, true, "")
        val journal = run(null).copy(steps = listOf(receipt(true, ActionTruth.CONFIRMED).copy(toolCallId = "older"), rejection))
        assertEquals(NativeActionOutcome.REJECTED, resolveNativeActionOutcome(journal, "call", container))
    }

}
