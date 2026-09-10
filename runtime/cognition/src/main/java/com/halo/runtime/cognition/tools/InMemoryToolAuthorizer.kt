package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall

class InMemoryToolAuthorizer(
    private val allowedToolIds: Set<String> = setOf("halo.echo", "halo.current_time")
) : ToolAuthorizer {

    override suspend fun authorize(toolCall: ToolCall): ToolAuthorization {
        return if (allowedToolIds.contains(toolCall.functionName)) {
            ToolAuthorization.Authorized(toolCall)
        } else {
            ToolAuthorization.Rejected(
                toolCall = toolCall,
                reason = "Tool is not authorized: ${toolCall.functionName}"
            )
        }
    }
}
