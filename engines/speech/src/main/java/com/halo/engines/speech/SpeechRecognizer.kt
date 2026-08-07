package com.halo.engines.speech

import com.halo.sdk.runtime.AudioBuffer

data class SpeechRecognitionResult(
    val text: String,
    val language: String,
    val confidence: Float
)

interface SpeechRecognizer {
    suspend fun recognize(audio: AudioBuffer): SpeechRecognitionResult
}

class MockSpeechRecognizer : SpeechRecognizer {
    override suspend fun recognize(audio: AudioBuffer): SpeechRecognitionResult {
        return SpeechRecognitionResult("What is the weather?", "en", 0.95f)
    }
}
