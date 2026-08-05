package com.halo.engines.ai

import com.halo.sdk.provider.*
import java.util.UUID

class PromptBuilder {
    fun build(memory: List<String>, user: String): AIConversation {
        val conversation = AIConversation(
            id = UUID.randomUUID().toString(),
            messages = mutableListOf()
        )

        conversation.messages += AIMessage(Role.SYSTEM, "You are Halo.")

        memory.forEach {
            conversation.messages += AIMessage(Role.SYSTEM, it)
        }

        conversation.messages += AIMessage(Role.USER, user)

        return conversation
    }
}
