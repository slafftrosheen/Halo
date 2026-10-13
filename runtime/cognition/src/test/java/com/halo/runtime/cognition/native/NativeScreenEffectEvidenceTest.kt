package com.halo.runtime.cognition.native

import com.halo.cognition.language.InteractionId
import com.halo.cognition.language.NativeScreenActionIntent
import com.halo.cognition.language.NativeScreenVerb
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.ui.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class NativeScreenEffectEvidenceTest {
    private val at = Instant.parse("2026-10-13T00:00:00Z")
    private val runId = AgentRunId("typed-postcondition")

    private fun run(receipt: UiActionReceipt?, truth: ActionTruth = ActionTruth.CONFIRMED,
        phase: UiWorkflowPhase = UiWorkflowPhase.WAITING_FOR_MODEL,
        call: String = "call", tool: String = "halo_ui_click"
    ): AgentRun = AgentRun(runId, InteractionId("interaction"), "Native screen control",
        AgentRunState.COMPLETED, createdAt = at, updatedAt = at, steps = listOf(
            AgentStep.UiTransactionRecorded(AgentStepId("ui"), runId, at,
                UiTransactionRecord(call, phase, receipt = receipt)),
            AgentStep.ToolOutcomeRecorded(AgentStepId("outcome"), runId, at,
                ToolInvocationId("invocation"), call, tool, true, "success", truth, null),
        ))

    private fun receipt(
        actionId: String = "call", operation: UiOperation = UiOperation.ACTIVATE,
        truth: ActionTruth = ActionTruth.CONFIRMED,
        verification: UiVerification = UiVerification.CONFIRMED,
        settle: UiSettleStatus = UiSettleStatus.SETTLED,
        after: String? = "new-generation"
    ) = UiActionReceipt(actionId, operation, "semantic-fingerprint",
        UiDispatchStatus.DISPATCHED, verification, truth,
        "old-generation", after, "navigation_changed", settleStatus = settle)

    private val tap = NativeScreenActionIntent(NativeScreenVerb.TAP, "Search")


    @Test fun homeRequiresItsOwnVerifiedGlobalActionPostcondition() {
        val home = NativeScreenActionIntent(NativeScreenVerb.HOME, "current screen")
        assertTrue(NativeScreenEffectEvidence.confirmed(
            run(receipt(operation = UiOperation.HOME), tool = "halo_ui_global_action"), home))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(operation = UiOperation.BACK), tool = "halo_ui_global_action"), home))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(operation = UiOperation.HOME), tool = "halo_ui_global_action",
                truth = ActionTruth.DISPATCHED), home))
    }

    @Test fun matchingSettledReceiptConfirmsAnObservedScreenEffect() {
        assertTrue(NativeScreenEffectEvidence.confirmed(run(receipt()), tap))
    }

    @Test fun textOrToolSuccessWithoutUiReceiptIsNotAConfirmedEffect() {
        assertFalse(NativeScreenEffectEvidence.confirmed(run(null), tap))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(), truth = ActionTruth.DISPATCHED), tap))
    }

    @Test fun receiptMustMatchExactActionAndOperation() {
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(actionId = "other-action")), tap))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(operation = UiOperation.SCROLL)), tap))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(), tool = "halo_ui_scroll"), tap))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(), phase = UiWorkflowPhase.SETTLING), tap))
    }

    @Test fun unverifiedOrUnsettledEffectCannotUnlockAnotherAction() {
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(verification = UiVerification.NOT_CONFIRMED)), tap))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(truth = ActionTruth.DISPATCHED)), tap))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(settle = UiSettleStatus.SETTLE_TIMEOUT)), tap))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(after = null)), tap))
        assertFalse(NativeScreenEffectEvidence.confirmed(
            run(receipt(after = "old-generation")), tap))
    }

    @Test fun nextScreenSearchNeedsAnExactFoundReceipt() {
        val find = NativeScreenActionIntent(NativeScreenVerb.FIND, "Search")
        val recorded = run(null, tool = "halo_find_on_screen")
        assertFalse(NativeScreenEffectEvidence.confirmed(recorded, find))
        val found = recorded.copy(steps = recorded.steps.map {
            if (it is AgentStep.UiTransactionRecorded) it.copy(transaction = it.transaction.copy(
                searchStatus = UiSearchStatus.FOUND,
                observation = UiObservation("fresh", 1L, "com.example.mail", false, false, false, false)))
            else it
        })
        assertTrue(NativeScreenEffectEvidence.confirmed(found, find))
        val ambiguous = found.copy(steps = found.steps.map {
            if (it is AgentStep.UiTransactionRecorded) it.copy(transaction =
                it.transaction.copy(searchStatus = UiSearchStatus.AMBIGUOUS))
            else it
        })
        assertFalse(NativeScreenEffectEvidence.confirmed(ambiguous, find))
    }
}
