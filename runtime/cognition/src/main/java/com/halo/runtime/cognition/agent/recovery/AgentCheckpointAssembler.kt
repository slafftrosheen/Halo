package com.halo.runtime.cognition.agent.recovery

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.tools.ToolId

/**
 * RUNTIME-018: derives a run checkpoint from the durable journal.
 *
 * The journal is the source of truth; no separate mutable checkpoint state
 * exists. Assembling is pure and deterministic.
 */
data class AgentCheckpoint(
    val runId: AgentRunId,
    val lastCompletedProviderTurn: AgentStep.ProviderTurnCompleted?,
    val openProviderTurn: AgentStep.ProviderTurnStarted?,
    val pendingInvocation: ToolInvocation?,
    val lastToolOutcome: AgentStep.ToolOutcomeRecorded?,
    val pendingInput: AgentInputRequest?,
    val recordedResponse: AgentStep.ResponseProduced?,
    val responseCommitted: Boolean,
    val exposedToolIds: Set<String>,
    val recoveryConfidence: RecoveryConfidence,
)

enum class RecoveryConfidence {
    /** Journal fully describes the run; deterministic recovery is possible. */
    HIGH,

    /** Journal is partially readable; recovery requires user confirmation. */
    MEDIUM,

    /** Legacy/unknown evidence; only conservative handling is permitted. */
    LOW,
}

class AgentCheckpointAssembler {

    fun assemble(run: AgentRun): AgentCheckpoint {
        val steps = run.steps

        val completedTurns = steps.filterIsInstance<AgentStep.ProviderTurnCompleted>()
        val openTurn = steps.filterIsInstance<AgentStep.ProviderTurnStarted>()
            .lastOrNull { started -> completedTurns.none { it.turnId == started.turnId } }

        val claimed = steps.filterIsInstance<AgentStep.ToolExecutionClaimed>()
        val prepared = steps.filterIsInstance<AgentStep.ToolInvocationPrepared>()
        val outcomes = steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>()
        val resolvedOutcomes = outcomes.map { it.toolCallId }.toSet() +
            steps.filterIsInstance<AgentStep.ToolFailed>().map { it.toolCallId }.toSet()

        val pendingInvocation = prepared
            .filter { preparedStep -> claimed.none { it.toolCallId == preparedStep.toolCallId } }
            .lastOrNull { preparedStep ->
                // Prepared and claimed but without outcome is the pending case; prepared
                // but never claimed is pending too. Only resolved outcomes remove pendency.
                preparedStep.toolCallId !in resolvedOutcomes
            }?.toInvocation()

        val claimedPending = claimed
            .filter { it.toolCallId !in resolvedOutcomes }
            .lastOrNull()?.let { claim ->
                prepared.firstOrNull { it.toolCallId == claim.toolCallId }?.toInvocation()
                    ?: ToolInvocation(
                        invocationId = claim.invocationId,
                        runId = claim.runId,
                        interactionId = InteractionId(""),
                        toolCallId = claim.toolCallId,
                        toolId = ToolId(claim.toolId),
                        argumentsJson = "",
                        argumentsDigest = "",
                        idempotencyKey = claim.idempotencyKey,
                        descriptorVersion = 0,
                        replaySafety = runCatching {
                            com.halo.runtime.cognition.tools.ReplaySafety.valueOf(claim.replaySafety)
                        }.getOrDefault(com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY),
                        createdAt = claim.createdAt,
                    )
            } ?: pendingInvocation

        val pendingInput = steps
            .filterIsInstance<AgentStep.InputRequested>()
            .lastOrNull { requested ->
                steps.none { it is AgentStep.InputResolved && it.requestId == requested.requestId }
            }?.input

        val recordedResponse = steps.filterIsInstance<AgentStep.ResponseProduced>().lastOrNull()
        val committed = steps.any { it is AgentStep.ResponseCommitted }

        val confidence = when {
            steps.none { it is AgentStep.RunPrepared } -> RecoveryConfidence.LOW
            openTurn != null || claimedPending != null -> RecoveryConfidence.MEDIUM
            else -> RecoveryConfidence.HIGH
        }

        return AgentCheckpoint(
            runId = run.id,
            lastCompletedProviderTurn = completedTurns.lastOrNull(),
            openProviderTurn = openTurn,
            pendingInvocation = claimedPending,
            lastToolOutcome = outcomes.lastOrNull(),
            pendingInput = pendingInput,
            recordedResponse = recordedResponse,
            responseCommitted = committed,
            exposedToolIds = (completedTurns.lastOrNull()?.toolCalls?.map { it.toolName } ?: emptyList())
                .toSet() + steps.filterIsInstance<AgentStep.ProviderTurnStarted>()
                .lastOrNull()?.exposedToolIds.orEmpty(),
            recoveryConfidence = confidence,
        )
    }

    private fun AgentStep.ToolInvocationPrepared.toInvocation() = ToolInvocation(
        invocationId = invocationId,
        runId = runId,
        interactionId = InteractionId(""),
        toolCallId = toolCallId,
        toolId = ToolId(toolId),
        argumentsJson = argumentsJson,
        argumentsDigest = argumentsDigest,
        idempotencyKey = idempotencyKey,
        descriptorVersion = descriptorVersion,
        replaySafety = runCatching {
            com.halo.runtime.cognition.tools.ReplaySafety.valueOf(replaySafety)
        }.getOrDefault(com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY),
        createdAt = createdAt,
    )
}
