package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class ToolRegistry(
    private val executors: List<ToolExecutor>
) {

    fun definitions(): List<ToolDefinition> =
        executors.flatMap { it.definitions() }

    fun validate(
        toolCall: ToolCall
    ): ToolExecutionResult.Rejected? {
        for ((key, value) in toolCall.arguments) {
            if (value is String && value.length > ToolExecutionLimits.MAX_TOOL_ARGUMENT_STRING_CHARS) {
                return ToolExecutionResult.Rejected(
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName,
                    reason = "Argument '$key' exceeds maximum length limit"
                )
            }
        }

        val executor = executors.firstOrNull { executor ->
            executor.definitions().any { definition ->
                definition.name == toolCall.functionName || definition.id == toolCall.functionName
            }
        }

        if (executor == null) {
            return ToolExecutionResult.Rejected(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                reason = "Unknown tool: ${toolCall.functionName}"
            )
        }

        return null
    }

    suspend fun execute(
        toolCall: ToolCall
    ): ToolExecutionResult {
        val validationFailure = validate(toolCall)
        if (validationFailure != null) {
            return validationFailure
        }

        val executor = executors.first { executor ->
            executor.definitions().any { definition ->
                definition.name == toolCall.functionName || definition.id == toolCall.functionName
            }
        }

        return executor.execute(toolCall)
    }
}
