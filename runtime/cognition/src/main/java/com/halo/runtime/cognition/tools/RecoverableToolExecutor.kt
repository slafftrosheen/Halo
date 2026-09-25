package com.halo.runtime.cognition.tools

/**
 * RUNTIME-018 optional tool reconciliation contract.
 *
 * Executors whose external effects can be verified (or completed) after process
 * death implement [reconcile]. Runtime never assumes an executor supports this;
 * the default answer for an unreconcilable tool is [ToolReconciliation.Unknown],
 * which forces conservative (user-driven) recovery.
 *
 * Reconciliation must never itself re-execute an unconfirmed side effect; it may
 * only inspect state or perform the tool's own idempotent completion.
 */
interface RecoverableToolExecutor : ToolExecutor {

    suspend fun reconcile(
        invocation: com.halo.runtime.cognition.agent.ToolInvocation,
    ): ToolReconciliation
}

sealed interface ToolReconciliation {
    /** External state verified; the recorded result is authoritative. */
    data class Completed(val result: ToolExecutionResult) : ToolReconciliation

    /** The tool never executed (verified); safe to re-run under normal policy. */
    data object NotExecuted : ToolReconciliation

    /** The tool is still running externally; do not retry. */
    data object StillRunning : ToolReconciliation

    /** Completion cannot be proven; treat the outcome as unknown. */
    data class Unknown(val reason: String) : ToolReconciliation
}
