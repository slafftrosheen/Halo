package com.halo.runtime.cognition.conversation

import com.halo.sdk.provider.AIMessage
import kotlinx.coroutines.flow.StateFlow

data class ConversationTurn(
    val id: String,
    val userText: String,
    val assistantText: String,
    val timestamp: Long
)

interface ConversationStore {

    val turns: StateFlow<List<ConversationTurn>>

    suspend fun append(
        userText: String,
        assistantText: String
    ): ConversationTurn

    suspend fun clear()

    fun recentMessages(
        maxTurns: Int
    ): List<AIMessage>
}
