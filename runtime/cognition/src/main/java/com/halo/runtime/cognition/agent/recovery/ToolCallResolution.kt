package com.halo.runtime.cognition.agent.recovery

import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.tools.ToolFailureKind

enum class ToolCallResolution {
    PENDING,
    WAITING_FOR_USER,
    SUCCEEDED,
    REJECTED_TERMINAL,
    FAILED_RETRYABLE,
    FAILED_TERMINAL,
    COMPLETION_UNKNOWN,
}

fun resolveToolCalls(toolCallIds: List<String>, steps: List<AgentStep>): Map<String, ToolCallResolution> {
    val preparedByInvocation = steps.filterIsInstance<AgentStep.ToolInvocationPrepared>()
        .associateBy { it.invocationId.value }
    val resolvedInputIds = steps.filterIsInstance<AgentStep.InputResolved>()
        .map { it.requestId.value }
        .toSet()
    val waitingCallIds = steps.filterIsInstance<AgentStep.InputRequested>()
        .mapNotNull { requested ->
            val approval = requested.input as? AgentInputRequest.Approval ?: return@mapNotNull null
            if (requested.requestId.value in resolvedInputIds) return@mapNotNull null
            preparedByInvocation[approval.invocationId.value]?.toolCallId
        }
        .toSet()
    val claims = steps.filterIsInstance<AgentStep.ToolExecutionClaimed>()
        .map { it.toolCallId }
        .toSet()

    return toolCallIds.associateWith { callId ->
        val latest = steps.asReversed().firstOrNull { step ->
            when (step) {
                is AgentStep.ToolRejected -> step.toolCallId == callId
                is AgentStep.ToolOutcomeRecorded -> step.toolCallId == callId
                is AgentStep.ToolCompleted -> step.toolCallId == callId
                is AgentStep.ToolFailed -> step.toolCallId == callId
                else -> false
            }
        }
        when (latest) {
            is AgentStep.ToolRejected -> ToolCallResolution.REJECTED_TERMINAL
            is AgentStep.ToolOutcomeRecorded -> when {
                latest.success -> ToolCallResolution.SUCCEEDED
                latest.actionTruth == ActionTruth.UNKNOWN -> ToolCallResolution.COMPLETION_UNKNOWN
                latest.failureKind in RETRYABLE_FAILURES -> ToolCallResolution.FAILED_RETRYABLE
                else -> ToolCallResolution.FAILED_TERMINAL
            }
            is AgentStep.ToolCompleted -> when {
                latest.success -> ToolCallResolution.SUCCEEDED
                latest.actionTruth == ActionTruth.UNKNOWN -> ToolCallResolution.COMPLETION_UNKNOWN
                else -> ToolCallResolution.FAILED_TERMINAL
            }
            is AgentStep.ToolFailed -> ToolCallResolution.FAILED_TERMINAL
            else -> when {
                callId in waitingCallIds -> ToolCallResolution.WAITING_FOR_USER
                callId in claims -> ToolCallResolution.COMPLETION_UNKNOWN
                else -> ToolCallResolution.PENDING
            }
        }
    }
}

private val RETRYABLE_FAILURES = setOf(
    ToolFailureKind.TIMEOUT.name,
    ToolFailureKind.RESOURCE_BUSY.name,
    ToolFailureKind.RESOURCE_UNAVAILABLE.name,
    ToolFailureKind.TRANSIENT.name,
)
