package com.halo.app.data

/** Ephemeral bounded projections of RoomConversationStore, never a second persistence owner. */
data class SavedChatSummary(val id: String, val title: String, val lifecycle: String, val updatedAt: Long)
data class SavedChatPage(val chats: List<SavedChatSummary>, val nextOffset: Int?)
data class SavedChatTurn(val id: String, val timestamp: Long, val userText: String, val assistantText: String, val truncated: Boolean)
data class SavedChatSnapshot(val id: String, val title: String, val lifecycle: String, val version: String,
    val updatedAt: Long, val turns: List<SavedChatTurn>, val totalTurns: Int, val nextOffset: Int?)

/** Raised only before a lifecycle mutation effect, never as an uncertain-write receipt. */
class SavedChatAdmissionException : IllegalArgumentException("Saved chat changed or active writer is protected")
