package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall

/**
 * Default authorization implementation for the initial phone-first runtime.
 *
 * Authorization is based on registered tools and argument validation.
 *
 * This is intentionally conservative:
 * - unknown tool -> reject
 * - malformed/oversized arguments -> reject
 * - registered tool -> authorize
 *
 * Physical/device-sensitive authorization can replace this later without
 * changing CognitiveSession.
 */
class InMemoryToolAuthorizer(
    private val toolRegistry: ToolRegistry
) : ToolAuthorizer {

    override suspend fun authorize(
        toolCall: ToolCall
    ): ToolAuthorization {
        val validation = toolRegistry.validate(toolCall)

        if (validation != null) {
            return ToolAuthorization.Rejected(
                toolCall = toolCall,
                reason = validation.reason
            )
        }

        if (!toolRegistry.contains(toolCall.functionName)) {
            return ToolAuthorization.Rejected(
                toolCall = toolCall,
                reason = "Unknown tool: ${toolCall.functionName}"
            )
        }

        val definition = toolRegistry.definitions().first { it.id == toolCall.functionName || it.name == toolCall.functionName }
        if (definition.risk != com.halo.sdk.provider.ToolRisk.SAFE) {
            return ToolAuthorization.Rejected(toolCall, "This action requires confirmation and is not enabled for automatic execution")
        }
        return ToolAuthorization.Authorized(toolCall)
    }
}
