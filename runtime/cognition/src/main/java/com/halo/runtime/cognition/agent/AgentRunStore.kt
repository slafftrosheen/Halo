package com.halo.runtime.cognition.agent

import kotlinx.coroutines.flow.Flow

/** Runtime contract; the host owns durable storage and atomic append/deduplication. */
interface AgentRunStore {
    fun observeRuns(): Flow<List<AgentRun>>
    suspend fun get(runId: AgentRunId): AgentRun?
    suspend fun create(run: AgentRun)
    /** False means this step or dispatch identity already exists. Never dispatch in that case. */
    suspend fun appendStep(step: AgentStep): Boolean
    suspend fun update(run: AgentRun)

    /** Every run that has not reached a terminal state. */
    suspend fun nonTerminalRuns(): List<AgentRun>

    /** @deprecated Misleading name; returns every non-terminal run. Use [nonTerminalRuns]. */
    @Deprecated("Returns every non-terminal run; renamed for honesty", ReplaceWith("nonTerminalRuns()"))
    suspend fun interruptedRuns(): List<AgentRun> = nonTerminalRuns()

    /**
     * Atomic resume claim (RUNTIME-018). Exactly one caller wins when several
     * surfaces try to resume the same run. Host implementations use a conditional
     * update (state as the CAS field) and must return false when the run is not in
     * [expectedState] (or is terminal).
     */
    suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean

    /**
     * RUNTIME-019: atomic input resolution (first valid resolution wins).
     * Appends exactly one `InputResolved` step when — and only when — the
     * request is journaled on a non-terminal run and still pending. Returns
     * false otherwise (unknown request, already resolved, terminal run).
     */
    suspend fun resolveInputIfPending(
        requestId: AgentInputRequestId,
        resolution: AgentInputResolution,
    ): Boolean
}

val AgentRunState.isTerminal: Boolean
    get() = this in setOf(AgentRunState.COMPLETED, AgentRunState.FAILED,
        AgentRunState.CANCELLED, AgentRunState.INTERRUPTED)

/** A dispatch/claim without a matching definitive result is unknown, including after cancellation. */
val AgentRun.hasUnknownAction: Boolean
    get() {
        val claimedCallIds = steps.filterIsInstance<AgentStep.ToolDispatched>().map { it.toolCallId } +
            steps.filterIsInstance<AgentStep.ToolExecutionClaimed>().map { it.toolCallId }
        return claimedCallIds.any { callId ->
            steps.none { step ->
                (step is AgentStep.ToolCompleted && step.toolCallId == callId &&
                    step.actionTruth in setOf(ActionTruth.CONFIRMED, ActionTruth.FAILED)) ||
                    (step is AgentStep.ToolOutcomeRecorded && step.toolCallId == callId &&
                        step.actionTruth != ActionTruth.UNKNOWN) ||
                    (step is AgentStep.ToolFailed && step.toolCallId == callId)
            }
        }
    }
