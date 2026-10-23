package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import java.time.Instant

/**
 * RUNTIME-018: execution class and recovery policy of an agent run.
 * Portable concepts; origin details (phone/glasses/routine name) stay separate.
 */
enum class AgentRunKind {
    INTERACTIVE,
    NATIVE,
    ROUTINE,
    SYSTEM,
}

enum class RunRecoveryPolicy {
    /** Recovery waits for the user to resume; startup never auto-executes. */
    USER_RESUME,

    /** Automatic continuation permitted for provably safe (read-only) work. Not enabled by default. */
    SAFE_AUTOMATIC,

    /** Unattended executions are never resumed (current routine semantics). */
    NO_RESUME,
}

/** Run metadata beyond the run row; persisted in the versioned run metadata envelope. */
data class AgentRunMetadata(
    val kind: AgentRunKind = AgentRunKind.INTERACTIVE,
    val recoveryPolicy: RunRecoveryPolicy = when (kind) {
        AgentRunKind.INTERACTIVE, AgentRunKind.SYSTEM, AgentRunKind.NATIVE -> RunRecoveryPolicy.USER_RESUME
        AgentRunKind.ROUTINE -> RunRecoveryPolicy.NO_RESUME
    },
    val providerId: String? = null,
    val modelId: String? = null,
    val contextRequirements: Set<String> = emptySet(),
)

enum class ToolRejectionCode {
    TOOLS_DISABLED,
    TOOL_UNKNOWN,
    TOOL_REGISTRY_UNAVAILABLE,
    TOOL_DISABLED,
    INVALID_ARGUMENTS,
    USER_REJECTED,
    AUTHORIZATION_DENIED,
    PRIVATE_CONTEXT_BLOCKED,
    CAPABILITY_UNAVAILABLE,
    RESOURCE_UNAVAILABLE,
    INTERACTION_CANCELLED,

    /**
     * AGENTIC-001 taxonomy. A disabled AccessibilityService is not an invalid
     * argument, and a missing contacts permission is not an invalid argument
     * either. Keep the meaningful category or the model cannot recover.
     */
    PERMISSION_REQUIRED,
    UNSUPPORTED_OPERATION,
    TRANSIENT_FAILURE,
    TOOL_NOT_EXPOSED,
    REPEATED_TOOL_CALL,
}

/**
 * RUNTIME-018 durable execution journal.
 *
 * The journal is append-only evidence. New variants record what was intended,
 * what definitely happened, and what remains unknown:
 *
 * - [ProviderTurnStarted]  provider request journaled before inference.
 * - [ProviderTurnCompleted] provider-neutral response persisted before any
 *   tool execution or answer publication.
 * - [ToolInvocationPrepared] intended invocation with canonical arguments,
 *   digest and replay policy, persisted before authorization finishes.
 * - [ToolExecutionClaimed] Halo durably claimed responsibility for this exact
 *   invocation; persisted BEFORE the executor runs. Replaces the misleading
 *   "dispatched" claim; legacy `ToolDispatched` decodes as a claim.
 * - [ToolOutcomeRecorded] exact model-facing result persisted before the result
 *   is given back to the provider.
 * - [InputRequested]/[InputResolved] durable approval/clarification waiting.
 * - [ResponseCommitted] conversation commit completed.
 */
data class AgentEvidenceRecord(val sourceType: String, val sourceRef: String, val title: String,
    val retrievedAtMs: Long, val payloadRefId: String? = null, val sha256: String? = null,
    val excerpt: String = "", val revision: String? = null)

sealed interface AgentStep {
    val id: AgentStepId
    val runId: AgentRunId
    val createdAt: Instant
    data class SemanticAppOperationRecorded(override val id: AgentStepId, override val runId: AgentRunId,
        override val createdAt: Instant, val receipt: com.halo.runtime.cognition.apps.SemanticOperationReceipt,
        val argumentShapes: List<com.halo.runtime.cognition.apps.SemanticArgumentShape> = emptyList()) : AgentStep

    data class UiTransactionRecorded(override val id: AgentStepId, override val runId: AgentRunId,
        override val createdAt: Instant, val transaction: com.halo.runtime.cognition.ui.UiTransactionRecord) : AgentStep

    data class DelegationResultRecorded(override val id: AgentStepId, override val runId: AgentRunId,
        override val createdAt: Instant, val status: String, val summary: String,
        val evidence: List<AgentEvidenceRecord>, val payloadRefId: String? = null) : AgentStep

    /** Opaque IDs admitted to provider context, without reference bodies or private reasoning. */
    data class ContextUsed(override val id: AgentStepId, override val runId: AgentRunId,
        override val createdAt: Instant, val objectIds: List<String>, val memoryIds: List<String>) : AgentStep {
        init { require(objectIds.size <= 20 && memoryIds.size <= 12) }
    }

    // ---- historical variants (unchanged shape) -----------------------------

    data class PlanCreated(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val planId: String,
        val summary: String
    ) : AgentStep

    data class ClarificationRequested(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val question: String,
        val field: String
    ) : AgentStep

    data class ApprovalRequested(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val action: String,
        val risk: String
    ) : AgentStep

    data class ToolRequested(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val toolCallId: String,
        val toolId: String,
        val argumentsJson: String
    ) : AgentStep

    /** Legacy dispatch record; interpreted conservatively as an execution claim. */
    data class ToolDispatched(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val toolCallId: String,
        val toolId: String,
        val idempotencyKey: String?
    ) : AgentStep

    data class ToolCompleted(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val toolCallId: String,
        val toolId: String,
        val resultSummary: String,
        val success: Boolean,
        val actionTruth: ActionTruth = if (success) ActionTruth.CONFIRMED else ActionTruth.FAILED,
    ) : AgentStep

    data class ToolFailed(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val toolCallId: String,
        val toolId: String,
        val reason: String
    ) : AgentStep

    data class ResponseProduced(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val responseId: String,
        val text: String,
        val payloadRefId: String? = null,
    ) : AgentStep

    // ---- RUNTIME-018 durable journal variants ------------------------------

    data class RunPrepared(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val kind: AgentRunKind,
        val recoveryPolicy: RunRecoveryPolicy,
        val conversationId: String? = null,
        val responseDelivery: com.halo.sdk.runtime.ResponseDelivery? = null,
    ) : AgentStep

    data class ContinuityBound(
        override val id: AgentStepId, override val runId: AgentRunId, override val createdAt: Instant,
        val conversationId: String,
    ) : AgentStep

    data class InteractionHandoffRecorded(
        override val id: AgentStepId, override val runId: AgentRunId, override val createdAt: Instant,
        val from: com.halo.runtime.cognition.continuity.InteractionEndpoint,
        val to: com.halo.runtime.cognition.continuity.InteractionEndpoint,
        val reason: com.halo.runtime.cognition.continuity.InteractionHandoffReason,
        val channel: com.halo.runtime.cognition.continuity.InteractionHandoffChannel = com.halo.runtime.cognition.continuity.InteractionHandoffChannel.AUDIO_OUTPUT,
    ) : AgentStep

    data class ProviderTurnStarted(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val turnId: ProviderTurnId,
        val providerId: String,
        val modelId: String?,
        val requestIdentity: String,
        val exposedToolIds: Set<String>,
        val requiredCapabilities: Set<String>,
        /**
         * Metadata-only requirements such as "VISUAL_IMAGE:CURRENT_VIEW".
         * Never persist captured screen/image contents in the execution journal.
         */
        val contextRequirements: Set<String> = emptySet(),
        val toolUseMode: String? = null,
        val workloadKind: String? = null,
    ) : AgentStep

    data class ProviderTurnCompleted(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val turnId: ProviderTurnId,
        val assistantText: String,
        val toolCalls: List<ProviderToolCallRecord>,
        val finishReason: String,
        val payloadRefId: String? = null,
    ) : AgentStep

    data class ToolInvocationPrepared(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val invocationId: ToolInvocationId,
        val toolCallId: String,
        val toolId: String,
        val argumentsJson: String,
        val argumentsDigest: String,
        val idempotencyKey: String,
        val descriptorVersion: Int,
        val replaySafety: String,
        /** Enum names only. Null denotes historical unknown provenance, never empty authority. */
        val remoteDataCategories: Set<String>? = null,
        val remoteDispatchRevision: Long? = null,
    ) : AgentStep

    /** Persisted before the executor runs. No proof of external dispatch. */
    data class ToolExecutionClaimed(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val invocationId: ToolInvocationId,
        val toolCallId: String,
        val toolId: String,
        val idempotencyKey: String,
        val replaySafety: String,
    ) : AgentStep

    data class ResourceBlocked(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val invocationId: ToolInvocationId,
        val toolId: String,
        val resourceIds: List<String>,
        val endpointIds: List<String>,
        val blockingLeaseIds: List<String>,
    ) : AgentStep

    data class ToolOutcomeRecorded(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val invocationId: ToolInvocationId,
        val toolCallId: String,
        val toolId: String,
        val success: Boolean,
        val output: String,
        val actionTruth: ActionTruth,
        val failureKind: String?,
        val payloadRefId: String? = null,
        val payloadSha256: String? = null,
        val payloadBytes: Long? = null,
    ) : AgentStep

    data class ToolRejected(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val toolCallId: String,
        val toolId: String,
        val reasonCode: ToolRejectionCode,
        val reason: String,
        val retryable: Boolean,
        val requiresUserAction: Boolean,
        val modelResult: String,
    ) : AgentStep

    data class InputRequested(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val requestId: AgentInputRequestId,
        val input: AgentInputRequest,
    ) : AgentStep

    data class InputResolved(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val requestId: AgentInputRequestId,
        val resolution: String,
    ) : AgentStep

    data class ContinuationPrepared(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val turnId: ProviderTurnId,
        val messageCount: Int,
        val exposedToolIds: Set<String>,
    ) : AgentStep

    data class ResponseCommitted(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val commitId: String,
        val interactionId: String,
    ) : AgentStep

    data class RecoveryClaimed(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val planKind: String,
        val evidenceStepId: String?,
    ) : AgentStep

    /**
     * MINING-P1-001 bounded-run guard.
     *
     * Durable evidence that Halo stopped a run on purpose, and why. Content-free by
     * construction: tool ids, counts, digests and timings only. Activity renders this as
     * "Task stopped" with a human explanation instead of "Runtime failure".
     */
    data class RunGuardTriggered(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val reason: RunGuardReason,
        val round: Int,
        val toolId: String?,
        /** Digest over the pattern/evidence that tripped the guard. Never user content. */
        val evidenceHash: String?,
        val elapsedMs: Long,
        /** Bounded, content-free explanation safe to show and log. */
        val summary: String,
    ) : AgentStep

    data class DirectCommandAuthorityRecorded(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val authority: DirectUserCommandAuthority
    ) : AgentStep

    data class ExternalConditionRegistered(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val watchId: String,
        val conditionType: String,
        val targetId: String? = null,
        val presentationText: String? = null,
    ) : AgentStep

    data class ExternalConditionSatisfied(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val watchId: String,
        val conditionType: String,
        val targetId: String? = null,
    ) : AgentStep

    data class ExternalConditionExpired(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val watchId: String,
        val conditionType: String,
    ) : AgentStep

    data class ExternalConditionCancelled(
        override val id: AgentStepId,
        override val runId: AgentRunId,
        override val createdAt: Instant,
        val watchId: String,
        val reason: String? = null,
    ) : AgentStep
}

/** Provider-neutral record of one model tool call. */
data class ProviderToolCallRecord(
    val toolCallId: String,
    val toolName: String,
    val argumentsJson: String,
)
