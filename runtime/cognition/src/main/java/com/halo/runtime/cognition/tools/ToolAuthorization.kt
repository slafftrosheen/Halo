package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall

/**
 * Result of the runtime authorization boundary for a tool call.
 *
 * Authorization is deliberately separate from execution.
 */
sealed interface ToolAuthorization {

    data class Authorized(
        val toolCall: ToolCall
    ) : ToolAuthorization

    data class Rejected(
        val toolCall: ToolCall,
        val reason: String
    ) : ToolAuthorization
}
