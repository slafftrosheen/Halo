package com.halo.app.data.memory

import com.halo.runtime.cognition.memory.LexicalMemoryScorer
import com.halo.runtime.cognition.memory.MemoryEmbeddingProvider
import com.halo.runtime.cognition.memory.MemoryRankingWeights
import com.halo.runtime.cognition.memory.MemoryRetriever
import com.halo.runtime.cognition.memory.MemorySearchResult
import com.halo.runtime.cognition.memory.MemorySearchScope
import com.halo.runtime.cognition.memory.MemorySemanticAvailability
import com.halo.runtime.cognition.memory.MemorySemanticIndex

/**
 * MINING-P1-001: the single lexical retrieval implementation used by the Runtime *and* the UI.
 *
 * Bounded at the database: the candidate pool is a `LIMIT`ed query, not a full-table scan into the
 * prompt. Semantic scoring is optional and reported honestly — when no embedding provider is
 * installed `semanticScore` stays null and ranking falls back to lexical + recency.
 */
class RoomMemoryRetriever(
    private val dao: MemoryRecordDao,
    private val embeddingProvider: MemoryEmbeddingProvider? = null,
    private val semanticIndex: MemorySemanticIndex? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : MemoryRetriever {

    override suspend fun search(
        query: String,
        scope: MemorySearchScope,
        limit: Int,
    ): List<MemorySearchResult> {
        val effectiveLimit = limit.coerceIn(1, MemoryRetriever.MAX_LIMIT)
        val scopes = scopesFor(scope)
        val trimmed = query.trim().take(MemoryRetriever.MAX_QUERY_CHARS)

        // Candidate pool: bounded, and small enough that scoring stays cheap on a 0.6B-class device.
        val pool = if (trimmed.isBlank()) {
            dao.recentActive(scopes, effectiveLimit * 2)
        } else {
            val terms = LexicalMemoryScorer.terms(trimmed)
            val primary = if (terms.isEmpty()) {
                emptyList()
            } else {
                // One bounded LIKE per meaningful term, merged and de-duplicated.
                terms.take(MAX_LIKE_TERMS).flatMap { term ->
                    runCatching { dao.searchActive("%${escapeLike(term)}%", scopes, effectiveLimit * 2) }
                        .getOrDefault(emptyList())
                }
            }
            val merged = LinkedHashMap<String, MemoryRecordEntity>()
            primary.forEach { merged[it.id] = it }
            dao.recentActive(scopes, effectiveLimit * 2).forEach { merged.putIfAbsent(it.id, it) }
            merged.values.toList()
        }

        if (pool.isEmpty()) return emptyList()

        val semantic = semanticScores(trimmed)

        val nowMs = now()
        return pool
            .map { entity ->
                val lexical = if (trimmed.isBlank()) 0.5f else LexicalMemoryScorer.score(trimmed, entity.content)
                val recency = LexicalMemoryScorer.recencyScore(entity.updatedAt, nowMs)
                val confidence = entity.confidence?.coerceIn(0f, 1f)
                val semanticScore = semantic?.get(entity.id)
                val finalScore = if (semanticScore != null) {
                    MemoryRankingWeights.withSemantic(lexical, semanticScore, recency, confidence)
                } else {
                    MemoryRankingWeights.lexicalOnly(lexical, recency, confidence)
                }
                MemorySearchResult(
                    memoryId = entity.id,
                    snippet = LexicalMemoryScorer.snippet(entity.content),
                    kind = entity.kind,
                    scope = runCatching { MemorySearchScope.valueOf(entity.scope) }
                        .getOrDefault(MemorySearchScope.GLOBAL),
                    confidence = confidence,
                    lexicalScore = lexical,
                    semanticScore = semanticScore,
                    recencyScore = recency,
                    finalScore = finalScore,
                    provenance = entity.sourceObjectId?.let { "notebook:$it" } ?: "direct memory record",
                )
            }
            .filter { result -> trimmed.isBlank() || result.lexicalScore > 0f || (result.semanticScore ?: 0f) > 0f }
            .sortedWith(compareByDescending<MemorySearchResult> { it.finalScore }.thenByDescending { it.recencyScore })
            .take(effectiveLimit)
    }

    /** Null when no usable semantic layer exists. Never fabricated. */
    private suspend fun semanticScores(query: String): Map<String, Float>? {
        val provider = embeddingProvider ?: return null
        val index = semanticIndex ?: return null
        if (!provider.available || !index.available || query.isBlank()) return null
        val vector = runCatching { provider.embed(query) }.getOrNull() ?: return null
        return runCatching { index.search(vector, MemoryRetriever.MAX_LIMIT) }
            .getOrNull()
            ?.associate { it.first to it.second }
    }

    suspend fun availability(): MemorySemanticAvailability = when {
        embeddingProvider == null || semanticIndex == null -> MemorySemanticAvailability.NOT_INSTALLED
        !embeddingProvider.available || !semanticIndex.available -> MemorySemanticAvailability.NOT_INSTALLED
        else -> MemorySemanticAvailability.AVAILABLE
    }

    suspend fun totalCount(): Int = runCatching { dao.activeCount() }.getOrDefault(0)

    private fun scopesFor(scope: MemorySearchScope): List<String> = when (scope) {
        MemorySearchScope.ALL -> MemoryScope.entries.map { it.name }
        MemorySearchScope.GLOBAL -> listOf(MemoryScope.GLOBAL.name)
        MemorySearchScope.PROJECT -> listOf(MemoryScope.PROJECT.name)
        MemorySearchScope.DEVICE -> listOf(MemoryScope.DEVICE.name)
        MemorySearchScope.CONVERSATION -> listOf(MemoryScope.CONVERSATION.name)
    }

    /** SQLite LIKE wildcards are user data here, so they must be escaped, not interpreted. */
    private fun escapeLike(term: String): String = term
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private companion object {
        const val MAX_LIKE_TERMS = 4
    }
}
