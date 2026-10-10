package com.halo.app.ui.onboarding

import com.halo.runtime.cognition.agent.*

/** A committed reply alone cannot prove a rejected, pending or merely dispatched action succeeded. */
object SetupInteractionEvidence {
    fun actionsConfirmed(runs: List<AgentRun>, requireAction: Boolean = false): Boolean {
        if(requireAction && runs.none { run -> run.steps.any { step ->
            when(step) {
                is AgentStep.ToolOutcomeRecorded -> step.success && step.actionTruth == ActionTruth.CONFIRMED
                is AgentStep.ToolCompleted -> step.success && step.actionTruth == ActionTruth.CONFIRMED
                else -> false
            }
        } }) return false
        return runs.all { run ->
        run.state == AgentRunState.COMPLETED && !run.hasUnknownAction && run.steps.none { step ->
            when(step) {
                is AgentStep.ToolRejected, is AgentStep.ToolFailed -> true
                is AgentStep.ToolOutcomeRecorded -> !step.success || step.actionTruth != ActionTruth.CONFIRMED
                is AgentStep.ToolCompleted -> !step.success || step.actionTruth != ActionTruth.CONFIRMED
                else -> false
            }
        }
        }
    }
}
