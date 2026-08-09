package com.halo.sdk.provider

import com.halo.sdk.host.AudioBuffer
import kotlinx.coroutines.flow.Flow

data class RecognitionResult(
    val text: String,
    val language: String,
    val confidence: Float
)

sealed interface RecognitionEvent {
    object Ready : RecognitionEvent
    object Listening : RecognitionEvent
    data class Partial(val text: String) : RecognitionEvent
    data class Final(val result: RecognitionResult) : RecognitionEvent
    data class Error(val reason: String) : RecognitionEvent
    object Stopped : RecognitionEvent
}

enum class SpeechRecognitionMode {
    BUFFERED,
    LIVE
}

sealed interface SpeechRecognitionProvider {
    val metadata: ProviderMetadata
        get() = ProviderMetadata(id = this::class.java.name, supportsStreaming = mode == SpeechRecognitionMode.LIVE)
    val mode: SpeechRecognitionMode
    suspend fun health(): Boolean
    suspend fun healthDetails(): ProviderHealth = ProviderHealth(healthy = health())
    suspend fun close() = Unit
}

interface BufferedSpeechRecognitionProvider : SpeechRecognitionProvider {
    override val mode: SpeechRecognitionMode get() = SpeechRecognitionMode.BUFFERED
    suspend fun recognize(audio: AudioBuffer): RecognitionResult
}

interface LiveSpeechRecognitionProvider : SpeechRecognitionProvider {
    override val mode: SpeechRecognitionMode get() = SpeechRecognitionMode.LIVE
    fun startListening(): Flow<RecognitionEvent>
    suspend fun stopListening()
}
