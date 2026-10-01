package com.halo.engines.speech

import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class BufferedRecognitionQualityTest {
    @Test fun phoneAndExternalBothRunWholeCaptureEstimator() = runBlocking {
        for (boundary in CaptureBoundary.entries) {
            val seen = CompletableDeferred<SpeechRecognized>()
            val bus = object : EventBus {
                override suspend fun publish(event: HaloEvent) { if (event is SpeechRecognized) seen.complete(event) }
                override fun events(): Flow<HaloEvent> = emptyFlow()
                override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
            }
            var calls = 0
            val estimator = object : SpeechActivityEstimator {
                override suspend fun estimate(audio: com.halo.sdk.host.AudioBuffer): SpeechActivityEstimate {
                    calls++
                    return SpeechActivityEstimate(500, 300, 0.6f, 1234, 1, 300, SpeechQualityEstimator.SILERO)
                }
            }
            val provider = object : BufferedSpeechRecognitionProvider {
                override suspend fun health() = true
                override suspend fun recognize(audio: com.halo.sdk.host.AudioBuffer): RecognitionResult {
                    assertEquals("capture", audio.captureId)
                    return RecognitionResult("test", "lv", metrics = com.halo.sdk.runtime.SpeechRecognitionMetrics(modelId = "whisper-base", decodeMs = 42))
                }
            }
            val wake = object : WakeWordDetector {
                override fun process(frame: AudioFrame) = WakeWordResult(false)
                override suspend fun health() = true
            }
            val vad = object : VoiceActivityDetector { override fun isSpeech(frame: AudioFrame) = true }
            val pipeline = AudioPipeline(vad, wake, provider, bus, this, estimator)
            pipeline.beginCapture(SpeechInputTurn("capture", boundary = boundary))
            pipeline.processFrame(AudioFrame(ShortArray(8000) { 1234 }, 16000, 1, 0))
            pipeline.finishCapture()
            val event = withTimeout(5000) { seen.await() }
            assertEquals(1, calls)
            assertEquals("SILERO", event.quality.qualityEstimator)
            assertEquals(300L, event.quality.voicedDurationMs)
            assertEquals(0.6f, event.quality.voicedRatio!!, 0.001f)
            assertEquals("whisper-base", event.metrics?.modelId)
            assertEquals(42L, event.metrics?.decodeMs)
            pipeline.stop()
        }
    }
    @Test fun energyFallbackLabelsItself() = runBlocking {
        val result = EnergySpeechActivityEstimator().estimate(com.halo.sdk.host.AudioBuffer(
            listOf(AudioFrame(ShortArray(8000) { 1234 }, 16000, 1, 0))))
        assertEquals(SpeechQualityEstimator.SIMPLE_ENERGY, result.estimator)
        assertEquals(500L, result.voicedDurationMs)
    }
}
