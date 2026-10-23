package com.halo.app.ui.screens.home

import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.sdk.provider.CognitiveSessionState
import org.junit.Assert.*
import org.junit.Test

class HomeFlowPresentationTest {
    private fun present(state: HomeUiState = HomeUiState(), receipt: TextSubmissionState = TextSubmissionState()) =
        homeFlowPresentation(state, receipt, "here", listeningRequested = false, setupNeeded = false)

    @Test fun pendingApprovalOutranksThinkingAndRemainsReviewable() {
        val result = present(HomeUiState(sessionState = CognitiveSessionState.Reasoning("provider"),
            runtimeWaitingState = AgentRunState.WAITING_FOR_APPROVAL))
        assertEquals("Needs your approval", result.status)
        assertTrue(result.needsReview)
    }

    @Test fun nativeActionFailureNeverSuggestsBlindRetry() {
        val result = present(receipt = TextSubmissionState(phase = TextSubmissionPhase.FAILED,
            conversationId = "here", requestId = "native:action", receivedOutput = false))
        assertEquals("Review the recorded outcome in Activity before trying again.", result.status)
    }

    @Test fun anotherConversationFailureDoesNotReplaceCurrentReadyState() {
        assertEquals("Ready when you are", present(receipt = TextSubmissionState(
            phase = TextSubmissionPhase.FAILED, conversationId = "elsewhere")).status)
    }

    @Test fun busyElsewhereExplainsWhySendIsBlocked() {
        assertEquals("Another conversation is busy. Stop it to send here.", present(receipt = TextSubmissionState(
            phase = TextSubmissionPhase.PROCESSING, conversationId = "elsewhere")).status)
    }

    @Test fun speakingAndListeningHaveDistinctFeedback() {
        assertEquals("Speaking…", present(HomeUiState(runtimeState = HaloRuntimeState.Speaking)).status)
        assertEquals("Getting ready…", present(HomeUiState(speechActive = true)).status)
        assertEquals("Listening…", present(HomeUiState(inputStatus = com.halo.sdk.runtime.SpeechInputStatus(capturing = true))).status)
        assertEquals("Turning your voice into text…", present(HomeUiState(speechProcessing = true)).status)
    }

    @Test fun recognitionAfterCaptureClosesIsTranscriptionRatherThanMicrophoneStartup() {
        val result = present(HomeUiState(speechActive = true, runtimeState = HaloRuntimeState.Recognizing))
        assertEquals("Turning your voice into text…", result.status)
    }

    @Test fun waitingReceiptSurvivesAnIdleSessionProjection() {
        val result = present(receipt = TextSubmissionState(phase = TextSubmissionPhase.WAITING_FOR_INPUT,
            conversationId = "here"))
        assertTrue(result.needsReview)
        assertEquals("Needs your review", result.status)
    }

    @Test fun automaticResourceWaitDoesNotOfferAnApprovalThatDoesNotExist() {
        val result = present(HomeUiState(runtimeWaitingState = AgentRunState.WAITING_FOR_RESOURCE))
        assertEquals("Waiting for another task", result.status)
        assertFalse(result.needsReview)
    }
}
