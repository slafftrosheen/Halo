package com.halo.runtime.cognition.agent.recovery

import com.halo.runtime.cognition.agent.AgentStep

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
    val result = mutableMapOf<String, ToolCallResolution>()
    
    val inputs = steps.filterIsInstance<AgentStep.InputRequested>().map { it.requestId.value }.toSet()
    val resolvedInputs = steps.filterIsInstance<AgentStep.InputResolved>().map { it.requestId.value }.toSet()
    val claims = steps.filterIsInstance<AgentStep.ToolExecutionClaimed>().map { it.toolCallId }.toSet()
    val outcomes = steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().associateBy { it.toolCallId }
    val completions = steps.filterIsInstance<AgentStep.ToolCompleted>().map { it.toolCallId }.toSet()
    val failures = steps.filterIsInstance<AgentStep.ToolFailed>().map { it.toolCallId }.toSet()

    for (callId in toolCallIds) {
        // If a ToolFailed is recorded, it's a rejection or terminal failure.
        if (callId in failures) {
            result[callId] = ToolCallResolution.REJECTED_TERMINAL
            continue
        }
        
        // If outcome is recorded, determine success or failure
        val outcome = outcomes[callId]
        if (outcome != null) {
            result[callId] = if (outcome.success) ToolCallResolution.SUCCEEDED else ToolCallResolution.FAILED_TERMINAL
            continue
        }
        if (callId in completions) {
            result[callId] = ToolCallResolution.SUCCEEDED
            continue
        }

        // Check if waiting for user input
        // Wait, how do we correlate an InputRequest with a specific tool call?
        // AgentStep.InputRequested does not have a toolCallId. But we can assume if there's an unresolved input, the tool call is waiting.
        // Actually, we can just say if there's any pending input, we'll hit that condition earlier in the planner.

        if (callId in claims) {
            result[callId] = ToolCallResolution.COMPLETION_UNKNOWN
            continue
        }

        result[callId] = ToolCallResolution.PENDING
    }

    return result
}
