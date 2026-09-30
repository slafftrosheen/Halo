package com.halo.runtime.cognition.conversation

data class ConversationHistoryHit(val turnId: String, val conversationId: String, val title: String,
    val timestamp: Long, val snippet: String)
fun interface ConversationHistoryRetriever {
    suspend fun search(currentConversationId: String, query: String, limit: Int): List<ConversationHistoryHit>
}
