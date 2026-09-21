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

    suspend fun append(
        userText: String,
        assistantText: String,
        metadata: Map<String, String> = emptyMap()
    ): ConversationTurn

    suspend fun clear()

    fun recentMessages(
        maxTurns: Int
    ): List<AIMessage>
}
