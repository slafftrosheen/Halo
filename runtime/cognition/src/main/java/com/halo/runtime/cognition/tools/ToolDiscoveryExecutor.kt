package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

/**
 * AGENTIC-001 `halo_discover_tools`.
 *
 * A small local model should be able to ask "alarm" and receive `halo_set_alarm`
 * instead of already knowing Halo's internal ids. Discovery is searchable by plain
 * words, accepts explicit ids, and its result declares exactly which tools become
 * callable on the next turn.
 */
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
        }.orEmpty().map { it.trim() }.filter { it.isNotEmpty() }

        val query = (toolCall.arguments["query"] as? String)?.trim().orEmpty()

        val matched = if (query.isNotEmpty()) discovery.search(query) else emptyList()
        val explicit = requested.mapNotNull { id -> discovery.registryDescriptorOrNull(id) }

        val candidates = (explicit + matched).distinctBy { it.name }
        if (candidates.isEmpty() && requested.isEmpty() && query.isEmpty()) {
            val catalogue = discovery.catalogueSummary()
            return ToolExecutionResult.Success(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                output = ToolResultEnvelope.success(
                    actionTruth = ActionTruth.ACKNOWLEDGED,
                    details = "status=needs_input\nProvide query or toolIds.\n$catalogue",
                ),
                actionTruth = ActionTruth.ACKNOWLEDGED,
            )
        }

        if (candidates.isEmpty()) {
            return ToolExecutionResult.Success(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                output = ToolResultEnvelope.success(
                    actionTruth = ActionTruth.ACKNOWLEDGED,
                    reasonCode = "NO_MATCHES",
                    details = buildString {
                        append("status=no_matches\n")
                        if (query.isNotEmpty()) append("query=").append(query).append('\n')
                        if (requested.isNotEmpty()) append("requested=").append(requested.joinToString(",")).append('\n')
                        append("next=TRY_DIFFERENT_WORDS_OR_ANSWER_WITHOUT_A_TOOL")
                    },
                ),
                actionTruth = ActionTruth.ACKNOWLEDGED,
            )
        }

        // Everything found becomes callable next turn; Runtime still re-applies
        // availability, security and the local allowlist before exposing schemas.
        val expandable = candidates.map { it.name }

        val details = buildString {
            append("status=discovered\n")
            append("count=").append(candidates.size).append('\n')
            append(discovery.describe(candidates)).append('\n')
            append(ToolExposureDirective.emit(expandable)).append('\n')
            append("next=CALL_THE_ACTION_NOW")
        }

        return ToolExecutionResult.Success(
            toolCallId = toolCall.id,
            toolId = toolCall.functionName,
            output = ToolResultEnvelope.success(
                actionTruth = ActionTruth.ACKNOWLEDGED,
                details = details,
            ),
            actionTruth = ActionTruth.ACKNOWLEDGED,
        )
    }
}
