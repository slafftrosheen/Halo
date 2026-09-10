package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

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

        val argumentViolation = validateArgumentValue(toolCall.arguments, depth = 0)
        if (argumentViolation != null) {
            return ToolExecutionResult.Rejected(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                reason = argumentViolation
            )
        }

        if (estimateArgumentChars(toolCall.arguments) > ToolExecutionLimits.MAX_TOOL_ARGUMENT_TOTAL_CHARS) {
            return ToolExecutionResult.Rejected(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                reason = "Tool arguments exceed maximum total size"
            )
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
            withTimeout(ToolExecutionLimits.MAX_TOOL_EXECUTION_TIMEOUT_MS) {
                executor.execute(toolCall)
            }
        } catch (e: TimeoutCancellationException) {
            ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = "Tool execution timed out"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = t.message ?: "Tool execution failed"
            )
        }
    }

    private fun validateArgumentValue(value: Any?, depth: Int): String? {
        if (depth > ToolExecutionLimits.MAX_TOOL_ARGUMENT_DEPTH) {
            return "Tool arguments exceed maximum nesting depth"
        }

        return when (value) {
            is String -> if (value.length > ToolExecutionLimits.MAX_TOOL_ARGUMENT_STRING_CHARS) {
                "Tool argument exceeds maximum string length"
            } else {
                null
            }
            is Map<*, *> -> {
                if (value.size > ToolExecutionLimits.MAX_TOOL_ARGUMENT_COLLECTION_ITEMS) {
                    "Tool arguments exceed maximum collection size"
                } else {
                    value.entries.firstNotNullOfOrNull { (key, nested) ->
                        validateArgumentValue(key, depth + 1) ?: validateArgumentValue(nested, depth + 1)
                    }
                }
            }
            is Iterable<*> -> {
                val values = value.toList()
                if (values.size > ToolExecutionLimits.MAX_TOOL_ARGUMENT_COLLECTION_ITEMS) {
                    "Tool arguments exceed maximum collection size"
                } else {
                    values.firstNotNullOfOrNull { validateArgumentValue(it, depth + 1) }
                }
            }
            is Array<*> -> validateArgumentValue(value.asList(), depth)
            else -> null
        }
    }

    private fun estimateArgumentChars(value: Any?): Int = when (value) {
        null -> 0
        is String -> value.length
        is Map<*, *> -> value.entries.sumOf { estimateArgumentChars(it.key) + estimateArgumentChars(it.value) }
        is Iterable<*> -> value.sumOf(::estimateArgumentChars)
        is Array<*> -> value.sumOf(::estimateArgumentChars)
        else -> value.toString().length
    }
}
