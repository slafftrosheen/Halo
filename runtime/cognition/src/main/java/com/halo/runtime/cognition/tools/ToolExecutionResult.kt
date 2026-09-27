package com.halo.runtime.cognition.tools

sealed interface ToolExecutionResult {

    data class Success(
        val toolCallId: String,
        val toolId: String,
        val output: String,
        val actionTruth: com.halo.runtime.cognition.agent.ActionTruth = com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED,
        val outputMetadata: ToolOutputMetadata? = null,
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
        val code: String = failureKind.name,
        val hint: String? = null,
        val payload: String? = null,
    ) : ToolExecutionResult
}

data class ToolOutputMetadata(
    val truncated: Boolean,
    val originalChars: Int,
    val originalUtf8Bytes: Int,
)
