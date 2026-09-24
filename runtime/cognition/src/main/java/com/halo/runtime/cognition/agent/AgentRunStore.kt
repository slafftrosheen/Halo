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
    suspend fun interruptedRuns(): List<AgentRun>
}

val AgentRunState.isTerminal: Boolean
    get() = this in setOf(AgentRunState.COMPLETED, AgentRunState.FAILED,
        AgentRunState.CANCELLED, AgentRunState.INTERRUPTED)

/** A dispatch without a matching result is unknown, including after cancellation. */
val AgentRun.hasUnknownAction: Boolean
    get() = steps.filterIsInstance<AgentStep.ToolDispatched>().any { dispatch ->
        steps.none { step ->
            (step is AgentStep.ToolCompleted && step.toolCallId == dispatch.toolCallId && step.actionTruth in setOf(ActionTruth.CONFIRMED, ActionTruth.FAILED)) ||
                (step is AgentStep.ToolFailed && step.toolCallId == dispatch.toolCallId)
        }
    }
