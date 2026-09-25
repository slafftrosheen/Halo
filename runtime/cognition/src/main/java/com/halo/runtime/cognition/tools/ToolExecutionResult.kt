package com.halo.runtime.cognition.tools

sealed interface ToolExecutionResult {

    data class Success(
        val toolCallId: String,
        val toolId: String,
        val output: String,
        val actionTruth: com.halo.runtime.cognition.agent.ActionTruth = com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED
    ) : ToolExecutionResult

    data class Rejected(
        val toolCallId: String,
        val toolId: String,
        val reason: String
    ) : ToolExecutionResult

    data class Failed(
        val toolCallId: String,
        val toolId: String,
        val message: String,
        val completionUnknown: Boolean = false,
        val failureKind: ToolFailureKind = ToolFailureKind.UNKNOWN,
    ) : ToolExecutionResult
}
