package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

interface ToolExecutor {

    fun definitions(): List<ToolDefinition>

    suspend fun execute(
        toolCall: ToolCall
    ): ToolExecutionResult
}
