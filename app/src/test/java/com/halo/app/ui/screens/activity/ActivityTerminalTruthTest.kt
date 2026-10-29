package com.halo.app.ui.screens.activity

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ActivityTerminalTruthTest {
    private val runId = AgentRunId("run")
    private val at = Instant.EPOCH
    private val inputId = AgentInputRequestId("input")
    private val completed = AgentStep.ToolCompleted(AgentStepId("done"), runId, at, "call", "halo_note_get", "Recorded", true)
    private val pending = AgentStep.InputRequested(AgentStepId("input"), runId, at, inputId,
        AgentInputRequest.Clarification(inputId, runId, at, question = "Continue?", field = "decision"))
    private val guard = AgentStep.RunGuardTriggered(AgentStepId("guard"), runId, at,
        RunGuardReason.DEADLINE_EXCEEDED, 1, null, null, 1000, "Reached the limit")
    private fun run(state: AgentRunState, steps: List<AgentStep>) = AgentRun(runId, InteractionId("interaction"),
        "Historical task", state, createdAt = at, updatedAt = at, steps = steps)

    @Test fun completedJournalWithTypedGuardIsStoppedWithoutRewritingRun() {
        val source = run(AgentRunState.COMPLETED, listOf(completed, pending, guard))
        val story = source.toStory()
        assertEquals("Stopped", activityStatusLabel(story))
        assertEquals(AgentRunState.COMPLETED, source.state)
        assertTrue(story.summary.contains("Stopped safely"))
        assertEquals(false, story.typedPlanProgress?.waiting)
        assertNull(story.reviewRunId)
    }
    @Test fun cancelledAndInterruptedKeepTheirOutcomeAndNoLiveWaitingSuffix() {
        listOf(AgentRunState.CANCELLED to "Stopped", AgentRunState.INTERRUPTED to "Interrupted").forEach { (state, label) ->
            val story = run(state, listOf(completed, pending)).toStory()
            assertEquals(label, activityStatusLabel(story))
            assertEquals(false, story.typedPlanProgress?.waiting)
            assertFalse(story.summary.contains("Waiting for your answer"))
        }
    }
    @Test fun actualWaitingRemainsReviewAndNormalCompletionRemainsCompleted() {
        val waiting = run(AgentRunState.WAITING_FOR_CLARIFICATION, listOf(completed, pending)).toStory()
        assertEquals("Needs your review", activityStatusLabel(waiting))
        assertEquals("run", waiting.reviewRunId)
        assertEquals(true, waiting.typedPlanProgress?.waiting)
        assertEquals("Completed", activityStatusLabel(run(AgentRunState.COMPLETED, listOf(completed)).toStory()))
    }
    @Test fun unknownDispatchKeepsUnknownPrecedenceOverGuardStop() {
        val dispatch = AgentStep.ToolDispatched(AgentStepId("dispatch"), runId, at, "unconfirmed", "halo_note_get", null)
        assertEquals("Result not confirmed", activityStatusLabel(run(AgentRunState.COMPLETED, listOf(dispatch, guard)).toStory()))
    }
    @Test fun laterRecoveryMakesAnEarlierGuardHistorical() {
        val resumed = AgentStep.RecoveryClaimed(AgentStepId("recovery"), runId, at, "RetryProviderTurn", "guard")
        val story = run(AgentRunState.COMPLETED, listOf(guard, resumed, completed)).toStory()
        assertEquals("Completed", activityStatusLabel(story))
        assertFalse(story.summary.contains("Stopped safely"))
        assertNull(activityCurrentGuardStop(listOf(guard, resumed, completed)))
        // A late outcome alone does not establish a new execution attempt.
        assertEquals(guard, activityCurrentGuardStop(listOf(guard, completed)))
        assertEquals(guard, activityCurrentGuardStop(listOf(resumed, completed, guard)))
    }

}
