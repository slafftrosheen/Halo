package com.halo.app.data

import com.halo.runtime.cognition.conversation.*

/** Searches existing durable turns; archived/deleted workspace conversations never become hits. */
class RoomConversationHistoryRetriever(private val database: AppDatabase) : ConversationHistoryRetriever {
    override suspend fun search(currentConversationId: String, query: String, limit: Int): List<ConversationHistoryHit> {
        if (currentConversationId.isBlank() || query.isBlank()) return emptyList()
        val conversation = database.conversationDao().getConversation(currentConversationId) ?: return emptyList()
        if (conversation.archived || database.notebookDao().getWorkspaceObject(currentConversationId)?.lifecycle != "ACTIVE") return emptyList()
        val safe = query.trim().take(120).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return database.conversationDao().searchCurrent(currentConversationId, "%$safe%", limit.coerceIn(1, 8)).map {
            val content = it.userText + "\n" + it.assistantText
            val index = content.indexOf(query.trim(), ignoreCase = true).coerceAtLeast(0)
            ConversationHistoryHit(it.id, it.conversationId, conversation.title, it.timestamp,
                content.substring((index - 60).coerceAtLeast(0)).take(320))
        }
    }
}
