package com.halo.runtime.cognition.retrieval

data class WorkspaceChunk(
    val id: String,
    val objectId: String,
    val ordinal: Int,
    val text: String,
    val tokenEstimate: Int,
    val startOffset: Int? = null,
    val endOffset: Int? = null,
)

data class WorkspaceRetrievalHit(
    val objectId: String,
    val objectType: String,
    val title: String,
    val snippet: String?,
    val lexicalScore: Float?,
    val semanticScore: Float?,
    val recencyScore: Float,
    val finalScore: Float,
)

data class WorkspaceRetrievalResult(
    val query: String,
    val results: List<WorkspaceRetrievalHit>,
    val isHybrid: Boolean = false,
)

interface WorkspaceRetriever {
    suspend fun retrieve(query: String, limit: Int = 10): WorkspaceRetrievalResult
}

interface EmbeddingProvider {
    val modelSignature: String
    val dimension: Int
    suspend fun embed(text: String): FloatArray?
}

class NoOpEmbeddingProvider : EmbeddingProvider {
    override val modelSignature: String = "none"
    override val dimension: Int = 0
    override suspend fun embed(text: String): FloatArray? = null
}
