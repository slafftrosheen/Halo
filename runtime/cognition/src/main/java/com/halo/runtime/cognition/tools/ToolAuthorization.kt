package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall

sealed interface ToolAuthorization {

    data class Authorized(
        val toolCall: ToolCall
    ) : ToolAuthorization

    data class Rejected(
        val toolCall: ToolCall,
        val reason: String
    ) : ToolAuthorization
}
