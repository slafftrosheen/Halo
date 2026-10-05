package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

interface ToolExecutor {

    fun definitions(): List<ToolDefinition>

    suspend fun execute(
        toolCall: ToolCall
    ): ToolExecutionResult

    suspend fun execute(toolCall: ToolCall, context: ToolExecutionContext): ToolExecutionResult = execute(toolCall)
}

/** Supplied only by the runtime after exact, fresh durable authority validation. */
data class ToolExecutionContext(
    val interactionId: String? = null,
    val directUserAuthority: Boolean = false,
)
