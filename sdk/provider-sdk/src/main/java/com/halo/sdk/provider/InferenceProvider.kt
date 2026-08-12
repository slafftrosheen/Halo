package com.halo.sdk.provider

import kotlinx.coroutines.flow.Flow

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

interface InferenceProvider {
    val metadata: ProviderMetadata
        get() = ProviderMetadata(id = this::class.java.name)

    suspend fun complete(request: AIRequestLegacy): AIResponseLegacy
    fun stream(prompt: String): Flow<String>
    suspend fun health(): Boolean

    suspend fun healthDetails(): ProviderHealth = ProviderHealth(healthy = health())

    suspend fun close() = Unit
}
