package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunKind
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.conversation.ConversationTurn

/** Host copy derived from an exact native receipt, never from matching response prose. */
enum class HomeNativeReceiptCopy(val english: String) {
    DISPATCHED_UNCONFIRMED("Action dispatched; completion is unconfirmed.")
}

internal fun projectHomeNativeReceipt(
    response: PresentedAssistantResponse?,
    turns: List<ConversationTurn>,
    runs: List<AgentRun>,
): HomeNativeReceiptCopy? {
    if (response?.source != ResponsePresentationSource.COMMITTED_CONVERSATION || response.providerId != "native") return null
    val turn = turns.singleOrNull { it.id == response.turnId && it.conversationId == response.conversationId } ?: return null
    if (response.text != turn.assistantText) return null
    if (turn.metadata["providerId"] != "native" || turn.metadata["interactionId"].isNullOrBlank() ||
        turn.metadata["interactionId"] != response.interactionId || turn.metadata["requestId"].isNullOrBlank() ||
        turn.metadata["requestId"] != response.requestId) return null
    val runId = turn.metadata["runId"]?.takeIf(String::isNotBlank) ?: return null
    val run = runs.singleOrNull { it.id.value == runId && it.interactionId.value == response.interactionId } ?: return null
    if (run.state != AgentRunState.COMPLETED || run.parentRunId != null || run.steps.any { it.runId != run.id }) return null
    val prepared = run.steps.filterIsInstance<AgentStep.RunPrepared>().singleOrNull() ?: return null
    if (prepared.kind != AgentRunKind.NATIVE || prepared.conversationId != response.conversationId) return null
    // Script/screen/settings responses have more specific guarantees and recovery copy.
    // Missing or ambiguous historical evidence keeps the original response literal.
    if (run.steps.any { it is AgentStep.PlanCreated || it is AgentStep.ProviderTurnStarted ||
            it is AgentStep.ToolRejected || it is AgentStep.ToolFailed }) return null
    val invocation = run.steps.filterIsInstance<AgentStep.ToolInvocationPrepared>().singleOrNull() ?: return null
    if (invocation.toolId.startsWith("halo_ui_") || invocation.toolId in setOf("halo_find_on_screen", "halo_open_settings")) return null
    val receipt = run.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().singleOrNull() ?: return null
    if (receipt.invocationId != invocation.invocationId || receipt.toolCallId != invocation.toolCallId ||
        receipt.toolId != invocation.toolId || !receipt.success ||
        receipt.actionTruth !in setOf(ActionTruth.DISPATCHED, ActionTruth.ACKNOWLEDGED)) return null
    return HomeNativeReceiptCopy.DISPATCHED_UNCONFIRMED
}
