package com.halo.sdk.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

data class ProviderMetadata(
    val id: String,
    val displayName: String = id,
    val local: Boolean = true,
    val supportsStreaming: Boolean = true
)

data class ProviderHealth(
    val healthy: Boolean,
    val message: String? = null
)

interface InferenceProvider : AiProvider {
    val metadata: ProviderMetadata
        get() = ProviderMetadata(id = this::class.java.name)

    override val id: String get() = metadata.id
    override val displayName: String get() = metadata.displayName
    override val isLocal: Boolean get() = metadata.local

    override suspend fun getStatus(): ProviderStatus {
        return if (health()) ProviderStatus.READY else ProviderStatus.UNAVAILABLE
    }

    override suspend fun getCapabilities(): Set<AiCapability> {
        val caps = mutableSetOf(AiCapability.TEXT)
        if (metadata.supportsStreaming) caps.add(AiCapability.STREAMING)
        return caps
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        // Bridge modern request to legacy completion if needed
        val conversation = AIConversation(
            id = "legacy",
            messages = context.conversation.toMutableList()
        )
        val legacyRequest = AIRequestLegacy(conversation)
        val legacyResponse = complete(legacyRequest)
        return AiResponse(text = legacyResponse.text, providerId = id)
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()

    suspend fun complete(request: AIRequestLegacy): AIResponseLegacy
    fun stream(prompt: String): Flow<String>
    suspend fun health(): Boolean

    suspend fun healthDetails(): ProviderHealth = ProviderHealth(healthy = health())

    suspend fun close() = Unit
}
