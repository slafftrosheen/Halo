package com.halo.sdk.provider

import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.host.AudioFrame
import kotlinx.coroutines.flow.Flow

data class RecognitionResult(
    val text: String,
    val language: String,
    val confidence: Float
)

data class RecognitionEvent(
    val partialText: String
)

interface SpeechRecognitionProvider {
    suspend fun recognize(audio: AudioBuffer): RecognitionResult
    fun stream(frames: Flow<AudioFrame>): Flow<RecognitionEvent>
    suspend fun health(): Boolean
}
