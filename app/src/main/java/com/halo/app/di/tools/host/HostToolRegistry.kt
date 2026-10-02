package com.halo.app.di.tools.host

import android.content.Context
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolPreflightProvider
import com.halo.runtime.cognition.tools.ToolPreflightResult

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
) : ToolExecutor, ToolPreflightProvider {

    private val toolIndex: Map<String, ToolExecutor>
    private val allDefinitions: List<ToolDefinition>

    init {
        val map = mutableMapOf<String, ToolExecutor>()
        val defs = mutableListOf<ToolDefinition>()
        for (executor in executors) {
            for (definition in executor.definitions()) {
                check(map.put(definition.id, executor) == null) { "Duplicate host tool executor: ${definition.id}" }
                defs.add(definition)
            }
        }
        toolIndex = map
        allDefinitions = defs
    }

    override fun definitions(): List<ToolDefinition> = allDefinitions

    override fun restoreTransientCall(toolCall: ToolCall): ToolCall =
        (toolIndex[toolCall.functionName] as? ToolPreflightProvider)?.restoreTransientCall(toolCall) ?: toolCall

    override suspend fun preflight(toolCall: ToolCall): ToolPreflightResult =
        (toolIndex[toolCall.functionName] as? ToolPreflightProvider)?.preflight(toolCall) ?: ToolPreflightResult.Validated(toolCall)

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult =
        toolIndex[toolCall.functionName]?.execute(toolCall)
            ?: ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Unknown host action")
}
