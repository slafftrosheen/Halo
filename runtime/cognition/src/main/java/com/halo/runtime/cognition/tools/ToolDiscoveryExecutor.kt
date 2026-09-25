package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall

/**
 * RUNTIME-017: executor for the discovery meta-tool.
 *
 * The model calls halo_discover_tools with tool ids; it receives the full schemas
 * for those tools as its tool-result text. This expands exposure for the current
 * interaction without mutating the global [ToolRegistry] or bypassing any
 * authorization gate: the expanded tools still go through skill/policy checks when
 * they are actually invoked.
 */
class ToolDiscoveryExecutor(
    private val discovery: ToolDiscoveryService,
) : ToolExecutor {

    override fun definitions(): List<com.halo.sdk.provider.ToolDefinition> =
        listOf(ToolDiscoveryService.discoveryToolDefinition())

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
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

        val schemas = discovery.expand(requested.filter { it != ToolDiscoveryService.DISCOVER_TOOL })
        val unknown = requested.filter { candidate ->
            discovery.expand(listOf(candidate)).none { it.name == candidate }
        }

        val body = schemas.joinToString("\n\n") { definition ->
            buildString {
                append("TOOL ").append(definition.name).append(": ").append(definition.description).append('\n')
                append("parameters: ").append(definition.parameters.toString())
            }
        }
        val message = buildString {
            if (body.isEmpty()) {
                append("No matching tools.")
            } else {
                append(body)
            }
            if (unknown.isNotEmpty()) {
                append("\nUnrecognized or unavailable ids: ").append(unknown.joinToString())
            }
        }
        return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, message)
    }
}
