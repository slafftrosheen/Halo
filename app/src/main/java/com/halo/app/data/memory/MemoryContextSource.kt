package com.halo.app.data.memory

import com.halo.runtime.cognition.context.MemoryContextItem
import com.halo.runtime.cognition.context.MemoryRecallQuery
import kotlinx.coroutines.flow.first

data class MemoryQuery(
    val query: String = "",
    val kinds: Set<MemoryKind> = emptySet(),
    val scopes: Set<MemoryScope> = emptySet(),
    val scopeIds: Set<String> = emptySet()
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
    suspend fun markUsed(ids: List<String>) = Unit
}

class BoundedMemoryContextSource(
    private val memoryRepository: MemoryRepository,
    /**
     * MINING-P1-001: when present this is the single retrieval implementation shared with the
     * Memory UI and halo_search_memory, so relevance never means two different things.
     */
    private val retriever: com.halo.runtime.cognition.memory.MemoryRetriever? = null,
) : MemoryContextSource, com.halo.runtime.cognition.context.MemoryContextSource {

    override suspend fun recall(query: MemoryRecallQuery): List<MemoryContextItem> {
        val appQuery = MemoryQuery(query = query.text, kinds = query.kinds.mapNotNull { runCatching { MemoryKind.valueOf(it) }.getOrNull() }.toSet(),
            scopes = query.scopes.mapNotNull { runCatching { MemoryScope.valueOf(it) }.getOrNull() }.toSet())
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
        val shared = retriever
        if (shared != null) {
            val scope = when (query.scopes.singleOrNull()) {
                MemoryScope.GLOBAL -> com.halo.runtime.cognition.memory.MemorySearchScope.GLOBAL
                MemoryScope.PROJECT -> com.halo.runtime.cognition.memory.MemorySearchScope.PROJECT
                MemoryScope.DEVICE -> com.halo.runtime.cognition.memory.MemorySearchScope.DEVICE
                MemoryScope.CONVERSATION -> com.halo.runtime.cognition.memory.MemorySearchScope.CONVERSATION
                MemoryScope.WORKSPACE_OBJECT -> com.halo.runtime.cognition.memory.MemorySearchScope.WORKSPACE_OBJECT
                else -> com.halo.runtime.cognition.memory.MemorySearchScope.ALL
            }
            return shared.search(query.query, scope, if(query.kinds.isEmpty()) limit.coerceIn(1,20) else 20).filter { result ->
                (query.scopes.isEmpty() || result.scope.toMemoryScope() in query.scopes) &&
                    (query.kinds.isEmpty() || runCatching { MemoryKind.valueOf(result.kind) }.getOrNull() in query.kinds)
            }.take(limit.coerceIn(1,20)).map { result ->
                MemoryContextCandidate(
                    id = result.memoryId,
                    content = result.snippet,
                    kind = runCatching { MemoryKind.valueOf(result.kind) }.getOrDefault(MemoryKind.FACT),
                    scope = result.scope.toMemoryScope(),
                    provenanceSummary = result.provenance,
                    relevance = result.finalScore,
                )
            }
        }
        // Fallback is conservative: global current records only without explicit scope-target evidence.
        val memories = memoryRepository.contextCandidates(query.scopes,query.scopeIds).filter {
            (it.scope == MemoryScope.GLOBAL || it.scopeId in query.scopeIds) && it.scope != MemoryScope.CONVERSATION &&
                (it.validUntil == null || it.validUntil > System.currentTimeMillis()) &&
                com.halo.runtime.cognition.memory.MemorySensitivityClassifier.classify(it.content) != com.halo.runtime.cognition.memory.MemorySensitivity.SECRET
        }
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

    override suspend fun markUsed(ids: List<String>) = memoryRepository.markUsed(ids)
    override suspend fun used(ids: List<String>) = memoryRepository.markUsed(ids)

    private fun com.halo.runtime.cognition.memory.MemorySearchScope.toMemoryScope(): MemoryScope = when (this) {
        com.halo.runtime.cognition.memory.MemorySearchScope.GLOBAL -> MemoryScope.GLOBAL
        com.halo.runtime.cognition.memory.MemorySearchScope.PROJECT -> MemoryScope.PROJECT
        com.halo.runtime.cognition.memory.MemorySearchScope.DEVICE -> MemoryScope.DEVICE
        com.halo.runtime.cognition.memory.MemorySearchScope.CONVERSATION -> MemoryScope.CONVERSATION
        com.halo.runtime.cognition.memory.MemorySearchScope.WORKSPACE_OBJECT -> MemoryScope.WORKSPACE_OBJECT
        com.halo.runtime.cognition.memory.MemorySearchScope.ALL -> MemoryScope.GLOBAL
    }
}
