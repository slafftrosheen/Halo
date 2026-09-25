package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class ToolDiscoveryExecutor(
    private val discoveryProvider: () -> ToolDiscoveryService,
) : ToolExecutor {

    constructor(discovery: ToolDiscoveryService) : this({ discovery })

    override fun definitions(): List<ToolDefinition> =
        listOf(ToolDiscoveryService.discoveryToolDefinition())

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        val discovery = discoveryProvider()
        val requested = toolCall.arguments["toolIds"]?.let { value ->
            when (value) {
                is List<*> -> value.filterIsInstance<String>()
                is String -> listOf(value)
                else -> emptyList()
            }
        }.orEmpty()

        if (requested.isEmpty()) {
            return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Expected at least one toolId")
        }

        val expanded = discovery.expand(requested.filter { it != ToolDiscoveryService.DISCOVER_TOOL })
        val availableNames = expanded.map { it.name }.toSet()

        val availableList = requested.filter { it in availableNames }
        val unavailableList = requested.filter { it !in availableNames }

        val availableJson = availableList.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val unavailableJson = unavailableList.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }

        val jsonResult = "{\"available\": $availableJson, \"unavailable\": $unavailableJson}"

        return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, jsonResult)
    }
}
