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

/**
 * Multimodal AI capabilities.
 */
enum class AiCapability {
    TEXT,
    IMAGE,
    AUDIO,
    VIDEO,
    MULTIMODAL,
    STREAMING
}

/**
 * Modern multimodal AI request.
 */
data class AiRequest(
    val text: String? = null,
    val audio: ByteArray? = null,
    val image: ByteArray? = null,
    val video: ByteArray? = null,
    val mimeTypes: List<String> = emptyList(),
    val systemContext: String? = null
)

/**
 * Modern multimodal AI response.
 */
data class AiResponse(
    val text: String,
    val audio: ByteArray? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Generic AI provider interface for Halo's cognitive runtime.
 */
interface AiProvider {
    val id: String
    val name: String
    val capabilities: Set<AiCapability>

    fun isAvailable(): Boolean

    suspend fun execute(request: AiRequest): AiResponse
}

/**
 * Legacy chat-based AI request.
 */
data class AIRequestLegacy(
    val conversation: AIConversation,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 512
)

/**
 * Legacy chat-based AI response.
 */
data class AIResponseLegacy(
    val text: String,
    val finishReason: String,
    val latencyMs: Long
)
