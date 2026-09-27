package com.halo.app.di.tools.host

import android.content.Context
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor

enum class HostActionReceipt {
    REQUESTED,
    VALIDATED,
    DISPATCHED,
    CONFIRMED,
    UNKNOWN,
    FAILED,
    CANCELLED;

    fun toActionTruth(): ActionTruth = when (this) {
        REQUESTED -> ActionTruth.PROPOSED
        VALIDATED -> ActionTruth.AUTHORIZED
        DISPATCHED -> ActionTruth.DISPATCHED
        CONFIRMED -> ActionTruth.CONFIRMED
        UNKNOWN -> ActionTruth.UNKNOWN
        FAILED -> ActionTruth.FAILED
        CANCELLED -> ActionTruth.FAILED
    }
}

class HostToolRegistry(
    private val executors: List<ToolExecutor>
) : ToolExecutor {
    override fun definitions(): List<ToolDefinition> = executors.flatMap { it.definitions() }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val executor = executors.find { it.definitions().any { def -> def.id == toolCall.functionName } }
        return executor?.execute(toolCall) 
            ?: ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Unknown host action")
    }
}
