package com.halo.runtime.cognition.memory

/**
 * MINING-P1-001 memory pull architecture.
 *
 * Halo already had a bounded `MemoryContextSource`; this adds the portable *retrieval contract* the
 * rest of the Runtime reasons about, so the agent can PULL memory instead of receiving a dump of
 * every stored fact in the system prompt.
 *
 * Scores are reported separately and never fabricated:
 *
 * - `lexicalScore`  — always available (bounded lexical match).
 * - `semanticScore` — null when no embedding provider/index is installed. Never faked with hashes.
 * - `recencyScore`  — always available from the record timestamps.
 */
enum class MemorySearchScope {
    /** All active durable records. */
    ALL,
    GLOBAL,
    PROJECT,
    DEVICE,

    /** Working/session-scoped memories: episodes tied to one conversation. */
    CONVERSATION,
    WORKSPACE_OBJECT,
}

data class MemorySearchResult(
    val memoryId: String,
    /** Bounded snippet. Never the entire record dump. */
    val snippet: String,
    val kind: String,
    val scope: MemorySearchScope,
    val confidence: Float?,
    val lexicalScore: Float,
    val semanticScore: Float?,
    val recencyScore: Float,
    val finalScore: Float,
    /** Bounded provenance label, e.g. "direct memory record". */
    val provenance: String?,
)

interface MemoryRetriever {
    suspend fun search(
        query: String,
        scope: MemorySearchScope = MemorySearchScope.ALL,
        limit: Int = DEFAULT_LIMIT,
    ): List<MemorySearchResult>

    companion object {
        const val DEFAULT_LIMIT = 5
        const val MAX_LIMIT = 20
        const val MAX_QUERY_CHARS = 500
        const val MAX_SNIPPET_CHARS = 240
    }
}

/**
 * Optional semantic layer. Absence is a supported state, not an error: lexical + recency ranking
 * still works, and the UI reports "Not installed".
 */
interface MemoryEmbeddingProvider {
    val id: String

    /** True only when the model is present and loadable. */
    val available: Boolean

    suspend fun embed(text: String): FloatArray?
}

interface MemorySemanticIndex {
    val available: Boolean

    suspend fun search(query: FloatArray, limit: Int): List<Pair<String, Float>>
}

/** Capability status surfaced to UI and diagnostics without pretending a model exists. */
enum class MemorySemanticAvailability {
    NOT_INSTALLED,
    LOADING,
    AVAILABLE,
}

/** Weighting used when both lexical and (optional) semantic scores exist. */
object MemoryRankingWeights {
    const val LEXICAL = 0.62f
    const val SEMANTIC = 0.20f
    const val RECENCY = 0.12f
    const val CONFIDENCE = 0.06f

    /**
     * Lexical-only ranking used when no embedding provider is installed.
     * Weights are renormalised so an absent semantic score never silently dilutes the result.
     */
    fun lexicalOnly(lexical: Float, recency: Float, confidence: Float?): Float {
        val weightSum = LEXICAL + RECENCY + (if (confidence != null) CONFIDENCE else 0f)
        val total = lexical * LEXICAL +
            recency * RECENCY +
            (confidence ?: 0f) * CONFIDENCE
        return if (weightSum <= 0f) lexical else (total / weightSum).coerceIn(0f, 1f)
    }

    fun withSemantic(lexical: Float, semantic: Float, recency: Float, confidence: Float?): Float {
        val weightSum = LEXICAL + SEMANTIC + RECENCY + (if (confidence != null) CONFIDENCE else 0f)
        val total = lexical * LEXICAL +
            semantic * SEMANTIC +
            recency * RECENCY +
            (confidence ?: 0f) * CONFIDENCE
        return if (weightSum <= 0f) lexical else (total / weightSum).coerceIn(0f, 1f)
    }
}

/**
 * Deterministic, bounded lexical scoring shared by the retriever and its callers so the Runtime and
 * the UI cannot drift into two different notions of relevance.
 */
object LexicalMemoryScorer {
    fun terms(query: String): List<String> = query
        .trim()
        .lowercase()
        .split(WHITESPACE)
        .filter { it.length >= MIN_TERM_CHARS }
        .distinct()
        .take(MAX_TERMS)

    fun score(query: String, content: String): Float {
        val terms = terms(query)
        if (terms.isEmpty()) return 0.5f
        val haystack = content.lowercase()
        var matched = 0
        var exact = 0
        for (term in terms) {
            if (haystack.contains(term)) {
                matched++
                // A whole-word hit is stronger evidence than a substring hit.
                if (Regex("(^|\\W)${Regex.escape(term)}(\\W|$)").containsMatchIn(haystack)) exact++
            }
        }
        if (matched == 0) return 0f
        val coverage = matched.toFloat() / terms.size
        val precision = exact.toFloat() / terms.size
        return (coverage * 0.75f + precision * 0.25f).coerceIn(0f, 1f)
    }

    /** Recency as a slow-decaying 0..1 score: 1.0 today, ~0.5 after a month. */
    fun recencyScore(updatedAtMs: Long, nowMs: Long): Float {
        val ageMs = (nowMs - updatedAtMs).coerceAtLeast(0L)
        val ageDays = ageMs.toDouble() / 86_400_000.0
        return (1.0 / (1.0 + ageDays / 30.0)).toFloat().coerceIn(0f, 1f)
    }

    fun snippet(content: String, maxChars: Int = MemoryRetriever.MAX_SNIPPET_CHARS): String {
        val flat = content.replace(WHITESPACE, " ").trim()
        return if (flat.length <= maxChars) flat else flat.take(maxChars - 1).trimEnd() + "…"
    }

    private val WHITESPACE = Regex("\\s+")
    private const val MIN_TERM_CHARS = 3
    private const val MAX_TERMS = 12
}
