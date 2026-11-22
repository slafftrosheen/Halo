package com.halo.runtime.cognition.conversation

data class ConversationHistoryHit(val turnId: String, val conversationId: String, val title: String,
    val timestamp: Long, val snippet: String, val conversationVersion: String? = null)
fun interface ConversationHistoryRetriever {
    suspend fun search(currentConversationId: String, query: String, limit: Int): List<ConversationHistoryHit>

    suspend fun referenceOtherChats(currentConversationId: String, query: String, targetIsLocal: Boolean): List<ConversationHistoryHit> = emptyList()

    suspend fun stillReferenceable(hit: ConversationHistoryHit, targetIsLocal: Boolean): Boolean = false
}
