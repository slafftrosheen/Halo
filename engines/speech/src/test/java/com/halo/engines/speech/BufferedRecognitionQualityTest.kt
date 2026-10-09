package com.halo.engines.speech

import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class BufferedRecognitionQualityTest {
    @Test fun cancelledDecodeCannotPublishLateTranscriptAndNextCaptureKeepsConversation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<HaloEvent>()
        var first = true
        val provider = object : BufferedSpeechRecognitionProvider {
            override suspend fun health() = true
            override suspend fun recognize(audio: com.halo.sdk.host.AudioBuffer): RecognitionResult {
                if (first) { first = false; entered.complete(Unit); withContext(NonCancellable) { release.await() } }
                return RecognitionResult("Recognized words", "en")
            }
        }
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) { events += event }
            override fun events(): Flow<HaloEvent> = emptyFlow()
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        }
        val wake = object : WakeWordDetector {
            override fun process(frame: AudioFrame) = WakeWordResult(false)
            override suspend fun health() = true
        }
        val pipeline = AudioPipeline(object : VoiceActivityDetector { override fun isSpeech(frame: AudioFrame) = true }, wake, provider, bus, this)
        pipeline.beginCapture(SpeechInputTurn("cancelled", conversationId = "original"))
        pipeline.processFrame(AudioFrame(ShortArray(1600) { 1000 }, 16000, 1, 0))
        pipeline.finishCapture(); entered.await(); pipeline.reset(); release.complete(Unit)
        while (pipeline.recognitionInFlight) yield()
        assertTrue(events.none { it is SpeechRecognized })
        pipeline.beginCapture(SpeechInputTurn("next", conversationId = "next-conversation"))
        pipeline.processFrame(AudioFrame(ShortArray(1600) { 1000 }, 16000, 1, 0))
        pipeline.finishCapture()
        while (pipeline.recognitionInFlight) yield()
        val recognized = events.filterIsInstance<SpeechRecognized>().single()
        assertEquals("next", recognized.id)
        assertEquals("next-conversation", recognized.input?.conversationId)
        pipeline.stop()
    }
    @Test fun cancelledDecodeReturnsToIdleAndReportsTheOwnedCapture() = runBlocking {
        val failed = CompletableDeferred<RecognitionFailed>()
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) { if (event is RecognitionFailed) failed.complete(event) }
            override fun events(): Flow<HaloEvent> = emptyFlow()
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        }
        val provider = object : BufferedSpeechRecognitionProvider {
            override suspend fun health() = true
            override suspend fun recognize(audio: com.halo.sdk.host.AudioBuffer): RecognitionResult {
                throw CancellationException("Decode cancelled")
            }
        }
        val wake = object : WakeWordDetector {
            override fun process(frame: AudioFrame) = WakeWordResult(false)
            override suspend fun health() = true
        }
        val pipeline = AudioPipeline(object : VoiceActivityDetector {
            override fun isSpeech(frame: AudioFrame) = true
        }, wake, provider, bus, this)
        pipeline.beginCapture(SpeechInputTurn("cancelled-capture"))
        pipeline.processFrame(AudioFrame(ShortArray(1600) { 1000 }, 16000, 1, 0))
        pipeline.finishCapture()
        assertEquals("cancelled-capture", withTimeout(5000) { failed.await() }.id)
        assertEquals(com.halo.sdk.runtime.SpeechState.Idle, pipeline.state.value)
        pipeline.stop()
    }
    @Test fun explicitCapturePreservesSpeechOnsetWhileVadIsDeciding() = runBlocking {
        val captured = CompletableDeferred<com.halo.sdk.host.AudioBuffer>()
        val provider = object : BufferedSpeechRecognitionProvider {
            override suspend fun health() = true
            override suspend fun recognize(audio: com.halo.sdk.host.AudioBuffer): RecognitionResult {
                captured.complete(audio)
                return RecognitionResult("open settings", "en")
            }
        }
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) = Unit
            override fun events(): Flow<HaloEvent> = emptyFlow()
            override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        }
        var frames = 0
        val vad = object : VoiceActivityDetector {
            override fun isSpeech(frame: AudioFrame) = ++frames >= 3
        }
        val wake = object : WakeWordDetector {
            override fun process(frame: AudioFrame) = WakeWordResult(false)
            override suspend fun health() = true
        }
        val pipeline = AudioPipeline(vad, wake, provider, bus, this)
        pipeline.beginCapture(SpeechInputTurn("onset"))
        repeat(4) { index -> pipeline.processFrame(AudioFrame(ShortArray(1600) { (index + 1).toShort() }, 16000, 1, index * 100_000_000L)) }
        pipeline.finishCapture()
        val audio = withTimeout(5000) { captured.await() }
        assertEquals(6400, audio.frames.sumOf { it.samples.size })
        assertEquals(listOf<Short>(1, 2, 3, 4), audio.frames.map { it.samples.first() })
        pipeline.stop()
    }
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
