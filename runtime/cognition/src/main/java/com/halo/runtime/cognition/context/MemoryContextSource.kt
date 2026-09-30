package com.halo.runtime.cognition.context

data class MemoryRecallQuery(
    val text: String,
    val scopes: Set<String> = emptySet(),
    val kinds: Set<String> = emptySet(),
    val limit: Int = 5,
)

data class MemoryContextItem(
    val id: String,
    val content: String,
    val kind: String,
    val scope: String,
    val relevance: Float = 1.0f,
    val provenance: String? = null,
)

fun interface MemoryContextSource {
    suspend fun recall(query: MemoryRecallQuery): List<MemoryContextItem>
    suspend fun used(ids: List<String>) = Unit
}
