package com.halo.runtime.cognition.context

data class MemoryRecallQuery(
    val text: String,
    val scopes: Set<String> = emptySet(),
    val kinds: Set<String> = emptySet(),
    val limit: Int = 5,
    val targetIsLocal: Boolean = true,
)

data class MemoryContextItem(
    val id: String,
    val content: String,
    val kind: String,
    val scope: String,
    val relevance: Float = 1.0f,
    val provenance: String? = null,
    val version: String? = null,
)

fun interface MemoryContextSource {
    suspend fun recall(query: MemoryRecallQuery): List<MemoryContextItem>
    /** Revalidate a ranked hit at the point it becomes context; never trust an old snippet. */
    suspend fun dereference(item: MemoryContextItem, query: MemoryRecallQuery): MemoryContextItem? = item
    suspend fun used(ids: List<String>) = Unit
}
