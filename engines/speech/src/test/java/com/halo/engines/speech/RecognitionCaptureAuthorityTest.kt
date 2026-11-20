package com.halo.engines.speech

import com.halo.sdk.host.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class RecognitionCaptureAuthorityTest {
    private val authority = ThreadLocal<Long>()
    private var revision = 1L
    private var speech = true
    private var observed: Long? = null
    private var samples = 0
    private val provider = object : BufferedSpeechRecognitionProvider {
        override suspend fun health() = true
        override fun captureContext() = RecognitionCaptureContext("test", revision, authority.asContextElement(revision))
        override suspend fun recognize(audio: com.halo.sdk.host.AudioBuffer): RecognitionResult {
            observed = authority.get(); samples = audio.frames.sumOf { it.samples.size }
            return RecognitionResult("hello", "en")
        }
    }
    private val bus = object : EventBus {
        override suspend fun publish(event: HaloEvent) {}
        override fun events(): Flow<HaloEvent> = emptyFlow()
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }
    private fun pipeline(scope: CoroutineScope) = AudioPipeline(object : VoiceActivityDetector {
        override fun isSpeech(frame: AudioFrame) = speech
    }, object : WakeWordDetector {
        override fun process(frame: AudioFrame) = WakeWordResult(false)
        override suspend fun health() = true
    }, provider, bus, scope)
    private fun frame() = AudioFrame(ShortArray(1600) { 1000 }, 16000, 1, 0)
    @Test fun explicitCaptureKeepsOriginalAuthorityAcrossModeChange() = runBlocking {
        val pipeline = pipeline(this)
        pipeline.beginCapture(SpeechInputTurn("pinned")); pipeline.processFrame(frame())
        revision = 2; pipeline.finishCapture()
        while (pipeline.recognitionInFlight) yield()
        assertEquals(1L, observed); assertNull(authority.get()); pipeline.stop()
    }
    @Test fun passivePreRollFromPriorModeIsDiscarded() = runBlocking {
        val pipeline = pipeline(this)
        speech = false; pipeline.processFrame(frame())
        revision = 2; speech = true; pipeline.processFrame(frame()); pipeline.finishCapture()
        while (pipeline.recognitionInFlight) yield()
        assertEquals(2L, observed); assertEquals(1600, samples); pipeline.stop()
    }
}
