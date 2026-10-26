package com.halo.app.ui.screens.home

import com.halo.app.ui.state.RuntimeTaskSummary
import com.halo.app.ui.state.RuntimeUiFormatters
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.isTerminal
import com.halo.runtime.cognition.continuity.RunContinuityResolver
import com.halo.sdk.runtime.SpeechDiagnosticStage

/** Home only: the Bell/Activity continue to use the untouched global task projection. */
internal fun homeConversationTaskSummary(runs: List<AgentRun>, conversationId: String): RuntimeTaskSummary {
    val current = runs.filter { conversationId.isNotBlank() &&
        RunContinuityResolver.conversationId(it) == conversationId && !it.state.isTerminal }
    val waiting = current.filter { it.state in setOf(AgentRunState.WAITING_FOR_APPROVAL,
        AgentRunState.WAITING_FOR_CLARIFICATION, AgentRunState.WAITING_FOR_CAPABILITY,
        AgentRunState.WAITING_FOR_RESOURCE, AgentRunState.SUSPENDED) }
    return RuntimeTaskSummary(current.count { it.state == AgentRunState.EXECUTING || it.state == AgentRunState.PLANNING },
        waiting.size, waiting.firstOrNull(), waiting.firstOrNull()?.let { RuntimeUiFormatters.formatRunState(it.state) })
}

internal data class HomeSpeechFailurePresentation(val title: String, val body: String, val offerTyping: Boolean)

/** RecognitionFailed retains the canonical reason. Its current event contract encodes the typed stage
 * as a prefix, so only that exact existing diagnostic stage is translated at the UI boundary. */
internal fun homeSpeechFailurePresentation(notice: SpeechRejectionNotice): HomeSpeechFailurePresentation? {
    if (notice.outputFailure || notice.reasonText.substringBefore(':') != SpeechDiagnosticStage.RESOURCE_ACQUIRE.name ||
        !notice.reasonText.contains("another heavyweight native model is busy")) return null
    return HomeSpeechFailurePresentation("Voice input is busy",
        "Another task is using local voice resources. Type your message now, or try voice again after that task finishes.", true)
}
