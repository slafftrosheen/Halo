package com.halo.sdk.provider

enum class Role {
    SYSTEM, USER, ASSISTANT, TOOL
}

data class AIMessage(
    val role: Role,
    val content: String
)

data class AIConversation(
    val id: String,
    val messages: MutableList<AIMessage>
)

data class AIRequest(
    val conversation: AIConversation,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 512
)

data class AIResponse(
    val text: String,
    val finishReason: String,
    val latencyMs: Long
)
