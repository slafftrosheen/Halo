package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EchoToolExecutor : ToolExecutor {

    override fun definitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                id = "halo.echo",
                name = "halo.echo",
                description = "Echoes back the provided text argument.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "text" to mapOf("type" to "string", "description" to "The text to echo back")
                    ),
                    "required" to listOf("text")
                )
            )
        )
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val textValue = toolCall.arguments["text"] as? String
            ?: return ToolExecutionResult.Rejected(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                reason = "Missing or invalid 'text' argument"
            )

        val output = "Echo: ${textValue.take(ToolExecutionLimits.MAX_TOOL_OUTPUT_CHARS)}"
        return ToolExecutionResult.Success(
            toolCallId = toolCall.id,
            toolId = toolCall.functionName,
            output = output
        )
    }
}

class CurrentTimeToolExecutor(
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> {
        return listOf(
            ToolDefinition(
                id = "halo_current_time",
                name = "halo_current_time",
                description = "Returns the current system date and time.",
                parameters = mapOf("type" to "object", "properties" to emptyMap<String, Any>(), "additionalProperties" to false)
            )
        )
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.arguments.isNotEmpty()) return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "No arguments expected")
        val formatted = java.time.Instant.ofEpochMilli(clock()).atZone(java.time.ZoneId.systemDefault()).toString()
        return ToolExecutionResult.Success(
            toolCallId = toolCall.id,
            toolId = toolCall.functionName,
            output = "Current time: $formatted"
        )
    }
}
