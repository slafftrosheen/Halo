package com.halo.runtime.cognition.agent.recovery

import com.halo.runtime.cognition.agent.*

/**
 * RUNTIME-018 pure recovery model.
 *
 * A [RecoveryPlan] is a deterministic classification of one interrupted run,
 * derived ONLY from the durable journal. It never executes anything; callers
 * (CognitiveSession.resume, startup classification) decide what to do with it.
 * No Android or host imports are permitted in this package.
 */
sealed interface RecoveryPlan {

    /** Run is terminal; nothing to recover. */
    data object NothingToDo : RecoveryPlan

    /** A durable input request (approval/clarification) is still pending. */
    data class AwaitingInput(
        val runId: AgentRunId,
        val request: AgentInputRequest,
    ) : RecoveryPlan

    /** Provider inference started but never completed; safe to re-run the turn. */
    data class RetryProviderTurn(
        val runId: AgentRunId,
        val turnId: ProviderTurnId,
        val providerId: String?,
        val modelId: String?,
        val exposedToolIds: Set<String>,
    ) : RecoveryPlan

    /** Provider response recorded with tool calls, but no invocation was claimed. */
    data class ContinueAfterToolResult(
        val runId: AgentRunId,
        val turnId: ProviderTurnId?,
        val exposedToolIds: Set<String>,
    ) : RecoveryPlan

    /** Claimed tool with SAFE replay semantics; re-execution cannot change state. */
    data class RetryTool(
        val runId: AgentRunId,
        val invocation: ToolInvocation,
        val useOriginalIdempotencyKey: Boolean,
    ) : RecoveryPlan

    /** Claimed tool requires reconciliation of external state before any retry. */
    data class ReconcileTool(
        val runId: AgentRunId,
        val invocation: ToolInvocation,
    ) : RecoveryPlan

    /** Final response was recorded but the conversation commit never happened. */
    data class CompleteFromRecordedResponse(
        val runId: AgentRunId,
        val responseText: String,
        val commitId: String,
    ) : RecoveryPlan

    /** Transient context (screen/view/fresh capture) existed then; it no longer does. */
    data class MissingContext(
        val runId: AgentRunId,
        val requirement: String,
    ) : RecoveryPlan

    /** Capability/permission needed by the next step is unavailable at resume time. */
    data class MissingCapability(
        val runId: AgentRunId,
        val capability: String,
    ) : RecoveryPlan

    /** NEVER_REPLAY action was claimed and its completion is unknown. */
    data class UncertainExternalAction(
        val runId: AgentRunId,
        val invocation: ToolInvocation?,
        val toolId: String?,
    ) : RecoveryPlan

    /** Historical run without digest/replay metadata; never inferred safe. */
    data class UnsupportedLegacyState(
        val runId: AgentRunId,
        val reason: String,
    ) : RecoveryPlan
}

/** Human-inspectable explanation of a recovery classification. */
data class RecoveryExplanation(
    val summary: String,
    val blockingReason: String?,
    val safeToResume: Boolean,
    val requiresUserInput: Boolean,
)

/** Snapshot of what capabilities exist at resume time; built by the host/runtime wiring. */
data class CapabilitySnapshot(
    val availableCapabilities: Set<CapabilityIdRef> = emptySet(),
) {
    @JvmInline
    value class CapabilityIdRef(val value: String) {
        override fun toString(): String = value
    }

    fun has(capabilityId: String): Boolean = availableCapabilities.any { it.value == capabilityId }
}
