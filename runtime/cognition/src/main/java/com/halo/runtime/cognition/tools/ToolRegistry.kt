package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

/**
 * Registry of executable runtime tools.
 *
 * ToolRegistry performs registration/lookup/validation.
 * Authorization remains a separate concern.
 */
class ToolRegistry(
    private val executors: List<ToolExecutor>
) {

    fun definitions(): List<ToolDefinition> =
        executors.flatMap { it.definitions() }

    fun contains(
        toolId: String
    ): Boolean =
        executors.any { executor ->
            executor.definitions().any { definition ->
                definition.name == toolId || definition.id == toolId
            }
        }

    /**
     * Validate a tool call without executing it.
     *
     * Returns null when validation succeeds.
     */
    fun validate(
        toolCall: ToolCall
    ): ToolExecutionResult.Rejected? {

        for ((key, value) in toolCall.arguments) {
            if (value is String &&
                value.length > ToolExecutionLimits.MAX_TOOL_ARGUMENT_STRING_CHARS
            ) {
                return ToolExecutionResult.Rejected(
                    toolCallId = toolCall.id,
                    toolId = toolCall.functionName,
                    reason = "Argument '$key' exceeds maximum length limit"
                )
            }
        }

        if (!contains(toolCall.functionName)) {
            return ToolExecutionResult.Rejected(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                reason = "Unknown tool: ${toolCall.functionName}"
            )
        }

        return null
    }

    /**
     * Execute only after authorization has already been granted.
     *
     * Keeping this method explicit prevents the cognitive layer from
     * conflating validation, authorization and execution.
     */
    suspend fun executeAuthorized(
        authorization: ToolAuthorization.Authorized
    ): ToolExecutionResult {

        val toolCall = authorization.toolCall

        val validation = validate(toolCall)
        if (validation != null) {
            return validation
        }

        val executor = executors.firstOrNull { candidate ->
            candidate.definitions().any { definition ->
                definition.name == toolCall.functionName ||
                    definition.id == toolCall.functionName
            }
        }

        if (executor == null) {
            return ToolExecutionResult.Rejected(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                reason = "Tool is no longer registered: ${toolCall.functionName}"
            )
        }

        return try {
            executor.execute(toolCall)
        } catch (t: Throwable) {
            ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = t.message ?: "Tool execution failed"
            )
        }
    }
}
