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
    private var active = UUID.randomUUID().toString()
    private val conversations = mutableMapOf(active to emptyList<ConversationTurn>())
    private val _turns = MutableStateFlow<List<ConversationTurn>>(emptyList())
    override val turns: StateFlow<List<ConversationTurn>> = _turns.asStateFlow()
    override fun activeConversationId(): String = active
    override suspend fun isConversationAvailable(id: String): Boolean = mutex.withLock { id in conversations }
    override suspend fun turnsForConversation(id: String): List<ConversationTurn> = mutex.withLock { conversations[id].orEmpty() }
    override suspend fun activateExistingConversation(id: String): Boolean = mutex.withLock {
        if (id !in conversations) return@withLock false
        active = id; _turns.value = conversations.getValue(id); true
    }
    suspend fun newConversation(): String = mutex.withLock {
        active = UUID.randomUUID().toString(); conversations[active] = emptyList(); _turns.value = emptyList(); active
    }
    override suspend fun append(userText: String, assistantText: String, metadata: Map<String, String>): ConversationTurn =
        appendIfAbsent(active, UUID.randomUUID().toString(), userText, assistantText, metadata)
    override suspend fun appendIfAbsent(commitId: String, userText: String, assistantText: String,
        metadata: Map<String, String>): ConversationTurn = appendIfAbsent(active, commitId, userText, assistantText, metadata)
    override suspend fun appendIfAbsent(conversationId: String, commitId: String, userText: String,
        assistantText: String, metadata: Map<String, String>): ConversationTurn = mutex.withLock {
        conversations.values.flatten().firstOrNull { it.metadata["commitId"] == commitId }?.let { return@withLock it }
        require(conversationId in conversations) { "Conversation unavailable" }
        val turn = ConversationTurn(UUID.randomUUID().toString(), userText, assistantText, System.currentTimeMillis(),
            conversationId, metadata + ("commitId" to commitId))
        conversations[conversationId] = conversations.getValue(conversationId) + turn
        if (active == conversationId) _turns.value = conversations.getValue(active)
        turn
    }
    override suspend fun clear() { mutex.withLock { conversations[active] = emptyList(); _turns.value = emptyList() } }
    override fun recentMessages(maxTurns: Int): List<AIMessage> = turns.value.takeLast(maxTurns).flatMap {
        listOf(AIMessage(Role.USER, it.userText), AIMessage(Role.ASSISTANT, it.assistantText))
    }
}
