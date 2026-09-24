package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import com.halo.sdk.runtime.ObservationOrigin
import java.time.Instant

@JvmInline
value class AgentRunId(val value: String)

@JvmInline
value class AgentStepId(val value: String)

enum class AgentRunState {
    CREATED,
    PLANNING,

    WAITING_FOR_CLARIFICATION,
    WAITING_FOR_APPROVAL,
    WAITING_FOR_CAPABILITY,

    EXECUTING,
    SUSPENDED,

    COMPLETED,
    FAILED,
    CANCELLED,
    INTERRUPTED,
}

enum class ActionTruth {
    PROPOSED,
    AUTHORIZED,
    DISPATCHED,
    ACKNOWLEDGED,
    CONFIRMED,
    UNKNOWN,
    FAILED,
}

data class AgentRun(
    val id: AgentRunId,
    val interactionId: InteractionId,
    val request: String,
    val state: AgentRunState,
    val origin: ObservationOrigin? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val currentStepId: AgentStepId? = null,
    val failureReason: String? = null,
    val steps: List<AgentStep> = emptyList(),
    val speechOutputTarget: com.halo.sdk.runtime.SpeechOutputTarget? = null,
    val speechOutputFallback: com.halo.sdk.runtime.SpeechOutputFallback = com.halo.sdk.runtime.SpeechOutputFallback.DISALLOW_PHONE,
    val sourceCaptureId: String? = null,
)

sealed interface AgentStep {
    val id: AgentStepId
    val runId: AgentRunId
    val createdAt: Instant

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
        val text: String
    ) : AgentStep
}
