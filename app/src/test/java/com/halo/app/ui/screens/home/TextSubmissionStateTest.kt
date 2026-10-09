package com.halo.app.ui.screens.home

import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.CognitiveTokenReceived
import com.halo.sdk.runtime.CognitiveFailed
import org.junit.Assert.*
import org.junit.Test

class TextSubmissionStateTest {
    @Test fun partialOutputFailurePreservesDraftWithoutRetrySuggestion() {
        val tracker = TextSubmissionTracker()
        tracker.begin("a", "one", "question")
        tracker.onEvent(CognitiveTokenReceived(requestId = "r", interactionId = "a", token = "Partial"))
        tracker.onEvent(CognitiveFailed(requestId = "r", interactionId = "a", message = "Interrupted"))
        assertEquals("question", tracker.state.value.draft)
        assertFalse(tracker.state.value.retryable)
        assertFalse(tracker.state.value.busy)
    }
    @Test fun doubleSendKeepsFirstIdentityAndDraft() {
        val tracker = TextSubmissionTracker()
        assertTrue(tracker.begin("a", "conversation", "first"))
        assertFalse(tracker.begin("b", "conversation", "second"))
        assertEquals("a", tracker.state.value.interactionId)
        assertEquals("first", tracker.state.value.draft)
    }

    @Test fun lateFailureCannotChangeRetry() {
        val tracker = TextSubmissionTracker()
        tracker.begin("a", "one", "question")
        tracker.fail("a", "rejected")
        assertTrue(tracker.state.value.retryable)
        assertTrue(tracker.begin("b", "two", "retry"))
        tracker.fail("a", "late")
        assertEquals(TextSubmissionPhase.SUBMITTING, tracker.state.value.phase)
        assertEquals("two", tracker.state.value.conversationId)
    }

    @Test fun admissionExpiryPreservesPromptAndAllowsRetry() {
        val tracker = TextSubmissionTracker()
        tracker.begin("a", "one", "question")
        tracker.admissionExpired("a")
        assertEquals(TextSubmissionPhase.TIMED_OUT, tracker.state.value.phase)
        assertEquals("question", tracker.state.value.draft)
        assertTrue(tracker.begin("b", "one", "question"))
    }

    @Test fun acceptedRequestIsNotTimedOutByAdmissionDeadline() {
        val tracker = TextSubmissionTracker()
        tracker.begin("a", "one", "question")
        tracker.onEvent(CognitiveRequestStarted(requestId = "request", interactionId = "a"))
        tracker.admissionExpired("a")
        assertEquals(TextSubmissionPhase.ACCEPTED, tracker.state.value.phase)
        assertTrue(tracker.state.value.accepted)
    }

    @Test fun missingTerminalReceiptReconcilesCommittedTurn() {
        val tracker = TextSubmissionTracker()
        tracker.begin("a", "one", "question")
        tracker.ownerEnded("a", committed = true)
        assertEquals(TextSubmissionPhase.COMPLETED, tracker.state.value.phase)
        assertFalse(tracker.state.value.busy)
        tracker.cancel("a")
        assertEquals(TextSubmissionPhase.COMPLETED, tracker.state.value.phase)
    }

    @Test fun nativePendingAnswerRequiresReviewInsteadOfClaimingTaskCompleted() {
        val tracker = TextSubmissionTracker()
        tracker.begin("pending", "one", "send the message")
        tracker.onEvent(CognitiveRequestStarted(requestId = "native:pending", interactionId = "pending"))
        tracker.onEvent(com.halo.sdk.runtime.AssistantResponsePublished(
            text = "Please approve the message.", delivery = com.halo.sdk.runtime.ResponseDelivery.VISUAL_ONLY,
            transactionId = "native:pending", interactionId = "pending", continuationPending = true
        ))
        val state = tracker.state.value
        assertEquals(TextSubmissionPhase.WAITING_FOR_INPUT, state.phase)
        assertFalse(state.busy)
        assertFalse(state.retryable)
        assertTrue(state.message.orEmpty().contains("Activity"))
        // Pending durable work does not block an unrelated new user turn.
        assertTrue(tracker.begin("next", "one", "another question"))
    }

    @Test fun pendingNativeRunBecomesCompletedOnlyAfterARealTerminalReceipt() {
        val tracker = TextSubmissionTracker()
        tracker.begin("a", "one", "set an alarm")
        tracker.onEvent(CognitiveRequestStarted(requestId = "native:a", interactionId = "a"))
        tracker.onEvent(com.halo.sdk.runtime.AssistantResponsePublished(
            text = "Review the pending alarm.", delivery = com.halo.sdk.runtime.ResponseDelivery.VISUAL_ONLY,
            transactionId = "native:a", interactionId = "a", continuationPending = true
        ))
        tracker.ownerEnded("a", committed = false)
        assertEquals(TextSubmissionPhase.WAITING_FOR_INPUT, tracker.state.value.phase)
        tracker.onEvent(com.halo.sdk.runtime.CognitiveCompleted(requestId = "native:a", response = "Alarm created",
            interactionId = "a"))
        assertEquals(TextSubmissionPhase.COMPLETED, tracker.state.value.phase)
        assertNull(tracker.state.value.message)
        tracker.onEvent(CognitiveFailed(requestId = "native:a", message = "stale failure", interactionId = "a"))
        assertEquals(TextSubmissionPhase.COMPLETED, tracker.state.value.phase)
    }

    @Test fun pendingNativeRunCanFailOrBeCancelledWithoutSuggestingBlindRetry() {
        val tracker = TextSubmissionTracker()
        tracker.begin("a", "one", "send text")
        tracker.onEvent(CognitiveRequestStarted(requestId = "native:a", interactionId = "a"))
        tracker.onEvent(com.halo.sdk.runtime.AssistantResponsePublished(
            text = "Approval needed", delivery = com.halo.sdk.runtime.ResponseDelivery.VISUAL_ONLY,
            transactionId = "native:a", interactionId = "a", continuationPending = true
        ))
        tracker.onEvent(com.halo.sdk.runtime.InteractionTurnChanged(
            state = com.halo.sdk.runtime.InteractionTurnState.Cancelled("a", "Review canceled action")
        ))
        assertEquals(TextSubmissionPhase.CANCELLED, tracker.state.value.phase)
        assertFalse(tracker.state.value.retryable)
    }

    @Test fun acceptedNativeCancellationRestoresDraftWithoutRetrySuggestion() {
        val tracker = TextSubmissionTracker()
        tracker.begin("a", "one", "action")
        tracker.onEvent(CognitiveRequestStarted(requestId = "native:a", interactionId = "a"))
        tracker.cancel("a")
        assertTrue(tracker.state.value.needsRecovery)
        assertFalse(tracker.state.value.retryable)
        assertEquals("action",tracker.state.value.draft)
        assertTrue(tracker.state.value.message.orEmpty().contains("recorded outcome"))
    }
}
