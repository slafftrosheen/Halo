package com.halo.app.ui.screens.home

import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.context.CognitiveOperatingMode
import com.halo.sdk.provider.CognitiveSessionState

/** Display copy only. Execution, submission admission and action truth stay with their owners. */
internal data class HomeFlowPresentation(val status: String, val needsReview: Boolean = false)

internal fun homeFlowPresentation(
    state: HomeUiState,
    submission: TextSubmissionState,
    conversationId: String,
    listeningRequested: Boolean,
    setupNeeded: Boolean,
): HomeFlowPresentation {
    val currentReceipt = submission.conversationId == conversationId
    val reviewStatus = when (state.runtimeWaitingState) {
        AgentRunState.WAITING_FOR_APPROVAL -> "Needs your approval"
        AgentRunState.WAITING_FOR_CLARIFICATION -> "Needs your answer"
        AgentRunState.WAITING_FOR_CAPABILITY -> "Waiting for a device or setup"
        AgentRunState.WAITING_FOR_RESOURCE -> "Waiting for another task"
        AgentRunState.SUSPENDED -> "Task paused"
        else -> null
    }
    return when {
        state.speechActive || listeningRequested -> HomeFlowPresentation("Listening…")
        state.speechProcessing -> HomeFlowPresentation("Turning your voice into text…")
        state.runtimeState == HaloRuntimeState.Speaking -> HomeFlowPresentation("Speaking…")
        submission.busy && !currentReceipt -> HomeFlowPresentation("Another conversation is busy. Stop it to send here.")
        reviewStatus != null -> HomeFlowPresentation(reviewStatus,
            needsReview = state.runtimeWaitingState in setOf(AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION))
        currentReceipt && submission.phase == TextSubmissionPhase.WAITING_FOR_INPUT ->
            HomeFlowPresentation("Needs your review", needsReview = true)
        currentReceipt && submission.phase == TextSubmissionPhase.SUBMITTING -> HomeFlowPresentation("Sending…")
        currentReceipt && submission.phase == TextSubmissionPhase.ACCEPTED -> HomeFlowPresentation("Message received")
        submission.busy || state.sessionState !is CognitiveSessionState.Idle &&
            state.sessionState !is CognitiveSessionState.Completed && state.sessionState !is CognitiveSessionState.Failed ->
            HomeFlowPresentation("Working on it…")
        currentReceipt && submission.needsRecovery && !submission.retryable ->
            HomeFlowPresentation("Review the recorded outcome in Activity before trying again.")
        currentReceipt && submission.phase == TextSubmissionPhase.CANCELLED -> HomeFlowPresentation("Stopped. Your draft is here when you need it.")
        currentReceipt && submission.needsRecovery || state.sessionState is CognitiveSessionState.Failed ->
            HomeFlowPresentation("Couldn’t finish that. Try again.")
        setupNeeded -> HomeFlowPresentation("Setup needs attention")
        state.runtimeState == HaloRuntimeState.Stopped -> HomeFlowPresentation("Halo is offline")
        state.runtimeState == HaloRuntimeState.Recovering -> HomeFlowPresentation("Getting ready again…")
        else -> HomeFlowPresentation("Ready when you are")
    }
}

internal fun homeModeLabel(mode: CognitiveOperatingMode): String = when (mode) {
    CognitiveOperatingMode.AUTO -> "Automatic"
    CognitiveOperatingMode.ASK -> "Ask"
    CognitiveOperatingMode.PLAN -> "Plan"
    CognitiveOperatingMode.AGENT -> "Act"
}
