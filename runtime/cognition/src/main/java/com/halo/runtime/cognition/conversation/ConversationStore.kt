package com.halo.runtime.cognition.conversation

import com.halo.sdk.provider.AIMessage
import kotlinx.coroutines.flow.StateFlow

data class ConversationTurn(
    val id: String,
    val userText: String,
    val assistantText: String,
    val timestamp: Long,
    val conversationId: String = "",
    val metadata: Map<String, String> = emptyMap()
)

interface ConversationStore {

    val turns: StateFlow<List<ConversationTurn>>

    suspend fun awaitReady() {}
    fun activeConversationId(): String? = turns.value.lastOrNull()?.conversationId?.ifBlank { null }

    suspend fun append(
        userText: String,
        assistantText: String,
        metadata: Map<String, String> = emptyMap()
    ): ConversationTurn

    /**
     * RUNTIME-018 idempotent conversation commit.
     *
     * A resumed task must not create duplicate conversation turns: stores
     * deduplicate on [commitId] (runId/responseId) and return the existing turn
     * when the commit was already recorded. Default delegates to [append] for
     * stores without durable deduplication (documented limitation).
     */
    suspend fun appendIfAbsent(
        commitId: String,
        userText: String,
        assistantText: String,
        metadata: Map<String, String> = emptyMap()
    ): ConversationTurn = append(userText, assistantText, metadata)

    /** Targeted commits never activate or recreate a conversation. */
    suspend fun appendIfAbsent(conversationId: String, commitId: String, userText: String,
        assistantText: String, metadata: Map<String, String> = emptyMap()): ConversationTurn {
        require(conversationId == activeConversationId()) { "Conversation unavailable" }
        return appendIfAbsent(commitId, userText, assistantText, metadata)
    }
    suspend fun isConversationAvailable(id: String): Boolean = id == activeConversationId()
    suspend fun activateExistingConversation(id: String): Boolean = id == activeConversationId()
    suspend fun turnsForConversation(id: String): List<ConversationTurn> = turns.value.filter { it.conversationId == id }

    suspend fun clear()

    fun recentMessages(
        maxTurns: Int
    ): List<AIMessage>
}
