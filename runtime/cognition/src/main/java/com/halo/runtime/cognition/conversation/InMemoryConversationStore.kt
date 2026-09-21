package com.halo.runtime.cognition.conversation

import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class InMemoryConversationStore : ConversationStore {

    private val mutex = Mutex()
    private val _turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    override val turns: StateFlow<List<ConversationTurn>> = _turns.asStateFlow()

    override suspend fun append(
        userText: String,
        assistantText: String,
        metadata: Map<String, String>
    ): ConversationTurn = mutex.withLock {
        val turn = ConversationTurn(
            id = UUID.randomUUID().toString(),
            userText = userText,
            assistantText = assistantText,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )
        _turns.value = _turns.value + turn
        turn
    }

    override suspend fun clear() {
        mutex.withLock {
            _turns.value = emptyList()
        }
    }

    override fun recentMessages(
        maxTurns: Int
    ): List<AIMessage> {
        return _turns.value
            .takeLast(maxTurns)
            .flatMap { turn ->
                listOf(
                    AIMessage(
                        role = Role.USER,
                        content = turn.userText
                    ),
                    AIMessage(
                        role = Role.ASSISTANT,
                        content = turn.assistantText
                    )
                )
            }
    }
}
