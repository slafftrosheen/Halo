package com.halo.engines.ai

import com.halo.sdk.provider.*

class ConversationManager {
    private val conversations = mutableMapOf<String, AIConversation>()

    fun get(id: String): AIConversation? = conversations[id]
    fun save(conversation: AIConversation) {
        conversations[conversation.id] = conversation
    }
}
