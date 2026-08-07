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
    suspend fun synthesize(request: SpeechRequest): SpeechResult
    fun stream(text: String): Flow<AudioFrame>
    suspend fun health(): Boolean
}
