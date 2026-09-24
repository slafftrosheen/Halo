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

        val definition = definitions().firstOrNull { it.id == toolCall.functionName || it.name == toolCall.functionName }
            ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Tool is no longer available")
        val schemaError = validateSchema(toolCall.arguments, definition.parameters, "arguments")
        return schemaError?.let { ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, it) }
    }

    private fun validateSchema(value: Any?, schema: Map<*, *>, path: String): String? {
        val allowed = schema["enum"] as? List<*>
        if (allowed != null && value !in allowed) return "$path has an unsupported value"
        when (schema["type"]) {
            "object" -> {
                val objectValue = value as? Map<*, *> ?: return "$path must be an object"
                val properties = schema["properties"] as? Map<*, *> ?: emptyMap<Any, Any>()
                val required = schema["required"] as? List<*> ?: emptyList<Any>()
                if (required.any { it !in objectValue }) return "$path is missing a required argument"
                if (schema["additionalProperties"] == false && objectValue.keys.any { it !in properties })
                    return "$path contains an unexpected argument"
                for ((key, nested) in objectValue) {
                    val childSchema = properties[key] as? Map<*, *> ?: continue
                    validateSchema(nested, childSchema, "$path.$key")?.let { return it }
                }
            }
            "string" -> {
                val text = value as? String ?: return "$path must be text"
                val min = (schema["minLength"] as? Number)?.toInt() ?: 0
                val max = (schema["maxLength"] as? Number)?.toInt() ?: Int.MAX_VALUE
                if (text.length !in min..max) return "$path has an invalid length"
            }
            "integer", "number" -> {
                val number = (value as? Number)?.toDouble() ?: return "$path must be a number"
                if (!number.isFinite() || (schema["type"] == "integer" && number % 1.0 != 0.0)) return "$path must be a finite ${schema["type"]}"
                val min = (schema["minimum"] as? Number)?.toDouble()
                val max = (schema["maximum"] as? Number)?.toDouble()
                if ((min != null && number < min) || (max != null && number > max)) return "$path is out of range"
            }
            "boolean" -> if (value !is Boolean) return "$path must be true or false"
            "array" -> {
                val list = value as? List<*> ?: return "$path must be a list"
                val min = (schema["minItems"] as? Number)?.toInt() ?: 0
                val max = (schema["maxItems"] as? Number)?.toInt() ?: Int.MAX_VALUE
                if (list.size !in min..max) return "$path has an invalid number of items"
                val itemSchema = schema["items"] as? Map<*, *>
                if (itemSchema != null) list.forEachIndexed { index, item ->
                    validateSchema(item, itemSchema, "$path[$index]")?.let { return it }
                }
            }
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
                message = "Tool execution timed out; completion unknown",
                completionUnknown = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = "Tool executor threw; completion unknown",
                completionUnknown = true
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
