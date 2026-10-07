package com.halo.app.automation

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

/** Plain text only. Delivery belongs to the reviewed routine, not this executor. */
class RoutineMessageTool : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition(ID, ID,
        "Produce fixed user-approved text for an automation result. The routine delivery policy decides how it is surfaced.",
        mapOf("type" to "object", "properties" to mapOf("text" to mapOf(
            "type" to "string", "minLength" to 1, "maxLength" to 240)),
            "required" to listOf("text"), "additionalProperties" to false)))

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        if (toolCall.functionName != ID) return ToolExecutionResult.Rejected(toolCall.id, ID, "Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        val text = (toolCall.arguments["text"] as? String)?.trim()
        if (toolCall.arguments.keys != setOf("text") || text == null || text.length !in 1..240)
            return ToolExecutionResult.Rejected(toolCall.id, ID, "Message must contain 1 to 240 characters")
        return ToolExecutionResult.Success(toolCall.id, ID, text)
    }

    companion object { const val ID = "halo_routine_message" }
}
