package com.halo.libraries.testing

import com.halo.sdk.host.AudioBuffer
import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/** Deterministic local providers for simulator and runtime integration tests. */
class FakeBufferedSpeechProvider(
    private val result: RecognitionResult = RecognitionResult("", "en-US", 1f)
) : BufferedSpeechRecognitionProvider {
    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "provider.test.buffered-stt",
        displayName = "Test buffered STT"
    )

    var calls: Int = 0
        private set

    override suspend fun recognize(audio: AudioBuffer): RecognitionResult {
        calls++
        return result
    }

    override suspend fun health(): Boolean = true
}

class FakeSpeechSynthesisProvider : SpeechSynthesisProvider {
    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "provider.test.tts",
        displayName = "Test TTS"
    )

    val requests = mutableListOf<SpeechRequest>()

    override suspend fun synthesize(request: SpeechRequest): SpeechResult {
        requests += request
        return SpeechResult(durationMillis = request.text.length.toLong(), successful = true)
    }

    override fun stream(text: String): Flow<AudioFrame> = emptyFlow()

    override suspend fun health(): Boolean = true
}

class FakeWakeWordDetector(
    private val detected: Boolean = true
) : WakeWordDetector {
    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "provider.test.wake-word",
        displayName = "Test wake word",
        supportsStreaming = false
    )

    override fun process(frame: AudioFrame): WakeWordResult = WakeWordResult(detected = detected)

    override suspend fun health(): Boolean = true
}

class FakeInferenceProvider(
    private val response: String = "Test response"
) : InferenceProvider {
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY).asStateFlow()

    override val metadata = ProviderMetadata(
        id = "provider.test.inference",
        displayName = "Test inference"
    )

    override suspend fun complete(request: AIRequestLegacy): AIResponseLegacy =
        AIResponseLegacy(response, "stop", 0L)

    override fun stream(prompt: String): Flow<String> = flow { emit(response) }

    override suspend fun health(): Boolean = true

    override suspend fun listModels(): List<AiModel> = emptyList()
}
