package com.halo.runtime.cognition.native

import com.halo.cognition.language.NativeScreenActionIntent
import com.halo.cognition.language.NativeScreenVerb
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.ui.UiOperation
import com.halo.runtime.cognition.ui.UiSettleStatus
import com.halo.runtime.cognition.ui.UiVerification
import com.halo.runtime.cognition.ui.UiWorkflowPhase
import com.halo.runtime.cognition.ui.UiSearchStatus

/**
 * Separate, typed, durable UI evidence for provider-free script advancement.
 * Generic ToolOutcomeRecorded(CONFIRMED) is not proof of a screen postcondition.
 * Rejects orphaned receipts, mismatched operations and unsettled/same-generation
 * observations. Never trusts model-visible tool prose or executes an action.
 */
object NativeScreenEffectEvidence {
    fun confirmed(run: AgentRun, intent: NativeScreenActionIntent): Boolean {
        val outcome = run.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>()
            .lastOrNull() ?: return false
        if (!outcome.success || outcome.actionTruth != ActionTruth.CONFIRMED) return false
        val transactions = run.steps.filterIsInstance<AgentStep.UiTransactionRecorded>()
            .map { it.transaction }.filter { it.toolCallId == outcome.toolCallId }
        val last = transactions.lastOrNull() ?: return false
        if (last.phase != UiWorkflowPhase.WAITING_FOR_MODEL) return false
        if (intent.verb == NativeScreenVerb.FIND) {
            return outcome.toolId == "halo_find_on_screen" &&
                last.searchStatus == UiSearchStatus.FOUND &&
                last.observation != null
        }
        val expectedTool = when (intent.verb) {
            NativeScreenVerb.TAP -> "halo_ui_click"
            NativeScreenVerb.ENTER_TEXT -> "halo_ui_set_text"
            NativeScreenVerb.SCROLL_FORWARD, NativeScreenVerb.SCROLL_BACKWARD -> "halo_ui_scroll"
            NativeScreenVerb.BACK, NativeScreenVerb.HOME -> "halo_ui_global_action"
            NativeScreenVerb.FIND -> return false
        }
        if (outcome.toolId != expectedTool) return false
        val expectedOp = when (intent.verb) {
            NativeScreenVerb.TAP -> UiOperation.ACTIVATE
            NativeScreenVerb.ENTER_TEXT -> UiOperation.SET_TEXT
            NativeScreenVerb.SCROLL_FORWARD, NativeScreenVerb.SCROLL_BACKWARD -> UiOperation.SCROLL
            NativeScreenVerb.BACK -> UiOperation.BACK
            NativeScreenVerb.HOME -> UiOperation.HOME
            NativeScreenVerb.FIND -> return false
        }
        val receipt = last.receipt ?: return false
        return receipt.actionId == outcome.toolCallId &&
            receipt.operation == expectedOp &&
            receipt.actionTruth == ActionTruth.CONFIRMED &&
            receipt.verificationStatus == UiVerification.CONFIRMED &&
            receipt.settleStatus == UiSettleStatus.SETTLED &&
            receipt.afterSnapshotId != null &&
            receipt.afterSnapshotId != receipt.beforeSnapshotId &&
            receipt.failureCode == null
    }
}
