package com.halo.app.di.tools

import com.halo.app.data.memory.MemoryCandidate
import com.halo.app.data.memory.MemoryContextSource
import com.halo.app.data.memory.MemoryKind
import com.halo.app.data.memory.MemoryQuery
import com.halo.app.data.memory.MemoryRepository
import com.halo.app.data.memory.MemoryScope
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class MemoryToolExecutor(
    private val memoryRepository: MemoryRepository,
    private val contextSource: MemoryContextSource
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_search_memory",
            name = "halo_search_memory",
            description = "Search saved user memories.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf("type" to "string", "maxLength" to 500)
                ),
                "required" to listOf("query"),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_propose_memory",
            name = "halo_propose_memory",
            description = "Propose a memory candidate for user approval before saving.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "content" to mapOf("type" to "string", "maxLength" to 1000)
                ),
                "required" to listOf("content"),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        return when (toolCall.functionName) {
            "halo_search_memory" -> {
                val query = toolCall.arguments["query"]?.toString()?.trim().orEmpty()
                val candidates = contextSource.retrieve(MemoryQuery(query = query), limit = 5)
                val summary = candidates.joinToString("\n") { "- ${it.content} (${it.kind.name})" }.ifEmpty { "No matching memories found." }
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, summary)
            }
            "halo_propose_memory" -> {
                val content = toolCall.arguments["content"]?.toString()?.trim().orEmpty()
                if (content.isBlank()) return reject("Content is required")
                val candidate = MemoryCandidate(content = content, kind = MemoryKind.FACT, scope = MemoryScope.GLOBAL)
                ToolExecutionResult.Success(
                    toolCall.id,
                    toolCall.functionName,
                    "Proposed memory candidate for user confirmation: '${candidate.content}'"
                )
            }
            else -> reject("Unsupported memory tool: ${toolCall.functionName}")
        }
    }
}
