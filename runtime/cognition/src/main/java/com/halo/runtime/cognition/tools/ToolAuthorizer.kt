package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall

interface ToolAuthorizer {

    suspend fun authorize(
        toolCall: ToolCall
    ): ToolAuthorization
}
