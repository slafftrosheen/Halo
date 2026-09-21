package com.halo.sdk.provider

import com.halo.sdk.host.AudioFrame
import kotlinx.coroutines.flow.Flow

data class SpeechRequest(
    val text: String,
    val voiceId: String? = null,
    val language: String? = null
)

data class SpeechResult(
    val durationMillis: Long,
    val successful: Boolean
)

interface SpeechSynthesisProvider {
    val metadata: ProviderMetadata
        get() = ProviderMetadata(id = this::class.java.name, supportsStreaming = true)
    suspend fun synthesize(request: SpeechRequest): SpeechResult
    fun synthesizeWithListener(request: SpeechRequest, onStart: () -> Unit): suspend () -> SpeechResult = { synthesize(request) }
    fun stopCurrent() = Unit
    fun stream(text: String): Flow<AudioFrame>
    suspend fun health(): Boolean
    suspend fun healthDetails(): ProviderHealth = ProviderHealth(healthy = health())
    suspend fun close() = Unit
}
