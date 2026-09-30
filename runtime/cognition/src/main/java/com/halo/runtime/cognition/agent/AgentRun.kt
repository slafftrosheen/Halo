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
    WAITING_FOR_RESOURCE,

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
    val parentRunId: AgentRunId? = null,
    val rootRunId: AgentRunId? = null,
    val delegatedRole: String? = null,
    val delegationDepth: Int = 0,
    val pinnedProviderId: String? = null,
    val pinnedModelId: String? = null,
    val pinnedTargetLocal: Boolean? = null,
    /** Host-approved references for reconstructing child context; never inferred from global history. */
    val delegatedInputRefs: String? = null,
)
