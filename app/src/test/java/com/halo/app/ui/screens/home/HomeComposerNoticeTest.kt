package com.halo.app.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class HomeComposerNoticeTest {
    @Test fun activeVoiceSessionOrReviewSuppressesSetupAndRecoveryNotice() {
        val failed = TextSubmissionState(TextSubmissionPhase.FAILED, conversationId = "a")
        assertNull(homeComposerNotice(true, failed, "a", active = true))
        assertNull(homeComposerNotice(false, failed, "a", active = true))
        assertNull(homeComposerNotice(true, failed.copy(phase = TextSubmissionPhase.SUBMITTING), "a"))
        assertNull(homeComposerNotice(true, failed.copy(phase = TextSubmissionPhase.WAITING_FOR_INPUT), "a"))
    }
    @Test fun setupDoesNotInventAdmissionFailure() {
        assertEquals("Answers need setup. Choose Set up answers below.",
            homeComposerNotice(true, TextSubmissionState(), "a"))
    }
    @Test fun recoveryRequiresCurrentConversationAndNeverLeaksDiagnostic() {
        val receipt = TextSubmissionState(TextSubmissionPhase.TIMED_OUT, conversationId = "a", message = "SECRET_DIAGNOSTIC")
        assertEquals("Your message wasn’t accepted. You can try again.", homeComposerNotice(false, receipt, "a"))
        assertNull(homeComposerNotice(false, receipt, "b"))
        assertEquals("Review the recorded outcome in Activity before trying again.",
            homeComposerNotice(false, receipt.copy(requestId = "native:1"), "a"))
    }
    @Test fun ordinaryBusyAndReviewStatesDoNotCreateAnotherStatusStrip() {
        listOf(TextSubmissionPhase.READY, TextSubmissionPhase.SUBMITTING, TextSubmissionPhase.ACCEPTED,
            TextSubmissionPhase.PROCESSING, TextSubmissionPhase.WAITING_FOR_INPUT, TextSubmissionPhase.COMPLETED).forEach {
            assertNull(homeComposerNotice(false, TextSubmissionState(it, conversationId = "a"), "a"))
        }
    }
}
