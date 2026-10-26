package com.halo.app.ui.screens.home

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HomeTaskAndSpeechPresentationTest {
    private fun run(id: String, conversation: String?, state: AgentRunState): AgentRun {
        val runId = AgentRunId(id); val now = Instant.EPOCH
        return AgentRun(runId, InteractionId(id), "fixture", state, createdAt = now, updatedAt = now,
            steps = conversation?.let { listOf(AgentStep.ContinuityBound(AgentStepId("binding-$id"), runId, now, it)) }.orEmpty())
    }
    @Test fun newConversationDoesNotInheritAnOldPausedHeroButTheRunRemainsAvailableGlobally() {
        val old = run("old", "old-conversation", AgentRunState.SUSPENDED)
        val unscoped = run("unknown", null, AgentRunState.WAITING_FOR_APPROVAL)
        val global = listOf(old, unscoped)
        val home = homeConversationTaskSummary(global, "new-conversation")
        assertEquals(0, home.waitingCount); assertNull(home.primaryWaitingRun); assertNull(home.statusText)
        assertEquals("Ready when you are", homeFlowPresentation(HomeUiState(runtimeWaitingState = home.primaryWaitingRun?.state),
            TextSubmissionState(), "new-conversation", false, false).status)
        assertEquals(AgentRunState.SUSPENDED, global.first().state)
        assertEquals(2, global.size)
    }
    @Test fun currentConversationWaitingTaskRemainsVisibleAndReviewable() {
        val old = run("old", "elsewhere", AgentRunState.SUSPENDED)
        val current = run("here", "current", AgentRunState.WAITING_FOR_APPROVAL)
        val summary = homeConversationTaskSummary(listOf(old, current), "current")
        assertEquals(current, summary.primaryWaitingRun); assertEquals(1, summary.waitingCount)
        val hero = homeFlowPresentation(HomeUiState(runtimeWaitingState = summary.primaryWaitingRun?.state),
            TextSubmissionState(), "current", false, false)
        assertEquals("Needs your approval", hero.status); assertTrue(hero.needsReview)
        assertEquals(0, homeConversationTaskSummary(listOf(current), "").waitingCount)
    }
    @Test fun resourceFailureHasActionableCopyWithoutChangingCanonicalReason() {
        val reason = "RESOURCE_ACQUIRE: Local speech recognition deferred: another heavyweight native model is busy"
        val notice = SpeechRejectionNotice(reason)
        val copy = requireNotNull(homeSpeechFailurePresentation(notice))
        assertEquals("Voice input is busy", copy.title); assertTrue(copy.offerTyping)
        assertTrue(copy.body.contains("Type your message")); assertFalse(copy.body.contains("RESOURCE_ACQUIRE"))
        assertFalse(copy.body.contains("heavyweight")); assertEquals(reason, notice.reasonText)
        assertNull(homeSpeechFailurePresentation(SpeechRejectionNotice("DECODE: unavailable")))
        assertNull(homeSpeechFailurePresentation(SpeechRejectionNotice("unrelated RESOURCE_ACQUIRE detail")))
        assertNull(homeSpeechFailurePresentation(notice.copy(outputFailure = true)))
    }
}
