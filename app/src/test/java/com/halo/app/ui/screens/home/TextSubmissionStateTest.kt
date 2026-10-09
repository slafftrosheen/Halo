package com.halo.app.ui.screens.home

import com.halo.sdk.runtime.CognitiveRequestStarted
import org.junit.Assert.*
import org.junit.Test

class TextSubmissionStateTest {
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
