package com.halo.engines.speech

data class SpeechRecognitionResult(
    val text: String,
    val language: String,
    val confidence: Float
)

interface SpeechRecognizer {
    suspend fun transcribe(segment: SpeechSegment): SpeechRecognitionResult
}

class MockSpeechRecognizer : SpeechRecognizer {
    override suspend fun transcribe(segment: SpeechSegment): SpeechRecognitionResult {
        return SpeechRecognitionResult("What is the weather?", "en", 0.95f)
    }
}
