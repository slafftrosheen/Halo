package com.halo.app.data.memory

import com.halo.runtime.cognition.context.MemoryContextItem
import com.halo.runtime.cognition.context.MemoryRecallQuery
import kotlinx.coroutines.flow.first

data class MemoryQuery(
    val query: String = "",
    val kinds: Set<MemoryKind> = emptySet(),
    val scopes: Set<MemoryScope> = emptySet()
)

data class MemoryContextCandidate(
    val id: String,
    val content: String,
    val kind: MemoryKind,
    val scope: MemoryScope,
    val provenanceSummary: String?,
    val relevance: Float
)

interface MemoryContextSource {
    suspend fun retrieve(query: MemoryQuery, limit: Int = 5): List<MemoryContextCandidate>
}

class BoundedMemoryContextSource(
    private val memoryRepository: MemoryRepository
) : MemoryContextSource, com.halo.runtime.cognition.context.MemoryContextSource {

    override suspend fun recall(query: MemoryRecallQuery): List<MemoryContextItem> {
        val appQuery = MemoryQuery(query = query.text)
        val candidates = retrieve(appQuery, query.limit)
        return candidates.map { c ->
            MemoryContextItem(
                id = c.id,
                content = c.content,
                kind = c.kind.name,
                scope = c.scope.name,
                relevance = c.relevance,
                provenance = c.provenanceSummary
            )
        }
    }

    override suspend fun retrieve(query: MemoryQuery, limit: Int): List<MemoryContextCandidate> {
        val memories = memoryRepository.observeMemories().first()
        val terms = query.query.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

        val candidates = memories
            .filter { memory -> query.kinds.isEmpty() || memory.kind in query.kinds }
            .filter { memory -> query.scopes.isEmpty() || memory.scope in query.scopes }
            .map { memory ->
                val lowerContent = memory.content.lowercase()
                val matchCount = if (terms.isEmpty()) 1 else terms.count { term -> lowerContent.contains(term) }
                val relevance = if (terms.isEmpty()) 0.5f else (matchCount.toFloat() / terms.size).coerceAtMost(1.0f)

                val provenance = when {
                    memory.sourceObjectId != null -> "Source object: ${memory.sourceObjectId.value}"
                    else -> "Direct memory record"
                }

                MemoryContextCandidate(
                    id = memory.id,
                    content = memory.content,
                    kind = memory.kind,
                    scope = memory.scope,
                    provenanceSummary = provenance,
                    relevance = relevance
                )
            }
            .filter { query.query.isBlank() || it.relevance > 0.0f }
            .sortedByDescending { it.relevance }
            .take(limit)

        return candidates
    }
}
