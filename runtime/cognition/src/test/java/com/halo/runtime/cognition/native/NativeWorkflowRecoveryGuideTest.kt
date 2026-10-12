package com.halo.runtime.cognition.native

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class NativeWorkflowRecoveryGuideTest {
    private val at = Instant.parse("2026-10-12T16:00:00Z")

    private fun parent(id: String, total: Int, state: AgentRunState) = AgentRun(
        AgentRunId("parent-$id"), InteractionId("request-$id"), "Phone workflow",
        state, createdAt = at, updatedAt = at,
        steps = listOf(NativeWorkflowJournal.parentCheckpoint(AgentRunId("parent-$id"), id, total, at))
    )

    private fun step(id: String, index: Int, total: Int, kind: String,
        state: AgentRunState, truth: ActionTruth? = null): AgentRun {
        val runId = AgentRunId("$id-$index")
        val steps = mutableListOf<AgentStep>(
            NativeWorkflowJournal.checkpoint(runId, id, index, total, kind, at))
        truth?.let {
            steps += AgentStep.ToolOutcomeRecorded(AgentStepId("$id:$index:outcome"),
                runId, at, ToolInvocationId("$id:$index:tool"), "$id:$index:call",
                "halo_ui_click", it != ActionTruth.FAILED, "redacted", it, null)
        }
        return AgentRun(runId, InteractionId("request-$id"), "Native script step",
            state, createdAt = at, updatedAt = at, steps = steps)
    }

    @Test fun contiguousPrefixStopsAtFirstUnknownRegardlessOfLaterReceipts() {
        val flow = NativeWorkflowJournal.progress(listOf(
            parent("unknown", 4, AgentRunState.INTERRUPTED),
            step("unknown", 1, 4, "OPEN_APP", AgentRunState.COMPLETED, ActionTruth.CONFIRMED),
            step("unknown", 2, 4, "TAP", AgentRunState.COMPLETED, ActionTruth.DISPATCHED),
            step("unknown", 3, 4, "ENTER_TEXT", AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        )).single()
        val advice = NativeWorkflowRecoveryGuide.assess(flow)
        assertEquals(1, advice.verifiedPrefix)
        assertEquals(2, advice.nextOrdinal)
        assertEquals("TAP", advice.kind)
        assertEquals(NativeWorkflowRecoveryGuide.Action.INSPECT_UNCERTAIN_EFFECT, advice.action)
        assertTrue(advice.requiresFreshUserCommand)
        assertFalse(advice.mayAutoResume)
    }

    @Test fun missingStepRequiresNewUserInstructionNotReconstructedArguments() {
        val flow = NativeWorkflowJournal.progress(listOf(
            parent("missing", 3, AgentRunState.INTERRUPTED),
            step("missing", 1, 3, "OPEN_APP", AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        )).single()
        val advice = NativeWorkflowRecoveryGuide.assess(flow)
        assertEquals(2, advice.nextOrdinal)
        assertNull(advice.kind)
        assertEquals(NativeWorkflowRecoveryGuide.Action.REQUEST_FRESH_INSTRUCTION, advice.action)
        assertEquals(1, advice.verifiedPrefix)
    }

    @Test fun activePendingInputCanBeReviewedButInterruptedParentCannot() {
        val step = step("review", 1, 2, "TAP", AgentRunState.WAITING_FOR_APPROVAL).let { original ->
            val input = AgentInputRequest.Clarification(AgentInputRequestId("approval"),
                original.id, at, question = "Continue?", field = "uiTakeover")
            original.copy(steps = original.steps + AgentStep.InputRequested(
                AgentStepId("input-request"), original.id, at, input.id, input))
        }
        val live = NativeWorkflowJournal.progress(listOf(
            parent("review", 2, AgentRunState.SUSPENDED), step)).single()
        assertEquals(NativeWorkflowRecoveryGuide.Action.REVIEW_INPUT,
            NativeWorkflowRecoveryGuide.assess(live).action)
        val orphaned = NativeWorkflowJournal.progress(listOf(
            parent("review", 2, AgentRunState.INTERRUPTED), step)).single()
        assertEquals(NativeWorkflowRecoveryGuide.Action.REQUEST_FRESH_INSTRUCTION,
            NativeWorkflowRecoveryGuide.assess(orphaned).action)
    }

    @Test fun fullyConfirmedAndStoppedNeverSuggestARepeat() {
        val complete = NativeWorkflowJournal.progress(listOf(
            parent("complete", 2, AgentRunState.COMPLETED),
            step("complete", 1, 2, "TAP", AgentRunState.COMPLETED, ActionTruth.CONFIRMED),
            step("complete", 2, 2, "BACK", AgentRunState.COMPLETED, ActionTruth.CONFIRMED)
        )).single()
        val finished = NativeWorkflowRecoveryGuide.assess(complete)
        assertEquals(NativeWorkflowRecoveryGuide.Action.COMPLETE, finished.action)
        assertNull(finished.nextOrdinal)
        assertFalse(finished.requiresFreshUserCommand)

        val stopped = NativeWorkflowJournal.progress(listOf(
            parent("stopped", 2, AgentRunState.CANCELLED))).single()
        val stopGuide = NativeWorkflowRecoveryGuide.assess(stopped)
        assertEquals(NativeWorkflowRecoveryGuide.Action.STOPPED, stopGuide.action)
        assertFalse(stopGuide.requiresFreshUserCommand)
    }
}
