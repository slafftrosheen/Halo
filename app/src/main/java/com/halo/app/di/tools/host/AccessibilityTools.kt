package com.halo.app.di.tools.host

import com.halo.host.android.accessibility.AccessibilityActions
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class AccessibilityTools : ToolExecutor {

    override fun definitions(): List<ToolDefinition> {
        val service = HaloAccessibilityService.instance ?: return emptyList()
        return listOf(
            ToolDefinition(
                id = "halo_ui_click",
                name = "halo_ui_click",
                description = "Click an element by view ID or visible text as a fallback UI action.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "viewId" to mapOf("type" to "string"),
                        "text" to mapOf("type" to "string")
                    ),
                    "additionalProperties" to false
                )
            ),
            ToolDefinition(
                id = "halo_ui_scroll",
                name = "halo_ui_scroll",
                description = "Scroll the current screen forward or backward.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "direction" to mapOf("type" to "string", "enum" to listOf("forward", "backward"))
                    ),
                    "required" to listOf("direction"),
                    "additionalProperties" to false
                )
            ),
            ToolDefinition(
                id = "halo_ui_global_action",
                name = "halo_ui_global_action",
                description = "Perform a global system action like back or home.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "action" to mapOf("type" to "string", "enum" to listOf("back", "home"))
                    ),
                    "required" to listOf("action"),
                    "additionalProperties" to false
                )
            )
        )
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        val service = HaloAccessibilityService.instance ?: return reject("Accessibility Service is not enabled by user")
        val actions = AccessibilityActions(service)

        return when (toolCall.functionName) {
            "halo_ui_click" -> {
                val viewId = toolCall.arguments["viewId"] as? String
                val text = toolCall.arguments["text"] as? String
                val success = when {
                    viewId != null -> actions.clickNode(viewId)
                    text != null -> actions.clickText(text)
                    else -> return reject("Must specify either viewId or text")
                }
                if (success) ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.CONFIRMED.name)
                else ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Failed to click element")
            }
            "halo_ui_scroll" -> {
                val direction = toolCall.arguments["direction"] as? String ?: return reject("Missing direction")
                val success = actions.scroll(forward = direction == "forward")
                if (success) ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.CONFIRMED.name)
                else ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Failed to scroll")
            }
            "halo_ui_global_action" -> {
                val action = toolCall.arguments["action"] as? String ?: return reject("Missing action")
                val success = when (action) {
                    "back" -> actions.globalBack()
                    "home" -> actions.globalHome()
                    else -> false
                }
                if (success) ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.CONFIRMED.name)
                else ToolExecutionResult.Failed(toolCall.id, toolCall.functionName, "Failed to perform global action")
            }
            else -> reject("Unknown tool")
        }
    }
}
