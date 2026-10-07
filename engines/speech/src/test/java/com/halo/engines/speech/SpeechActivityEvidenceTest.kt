package com.halo.engines.speech

import com.halo.sdk.host.AudioFrame
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SpeechActivityEvidenceTest {
    @Test fun onlyActiveCapturePublishesBoundedContentFreeEvidenceAndSilenceResetsDuration() = runBlocking {
        val events = mutableListOf<HaloEvent>()
        val bus = object : EventBus {
            override suspend fun publish(event: HaloEvent) { events += event }
            override fun events() = emptyFlow<HaloEvent>()
            override fun <T : HaloEvent> observe(type: Class<T>) = emptyFlow<T>()
        }
        var voiced = true
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pipeline = AudioPipeline(object : VoiceActivityDetector { override fun isSpeech(frame: AudioFrame) = voiced },
            object : WakeWordDetector { override fun process(frame: AudioFrame) = WakeWordResult(false); override suspend fun health() = true },
            object : BufferedSpeechRecognitionProvider {
                override suspend fun health() = true
                override suspend fun recognize(audio: com.halo.sdk.host.AudioBuffer) = RecognitionResult("test", "en")
            }, bus, scope)
        val frame = AudioFrame(ShortArray(320) { 1000 }, 16000, 1, 0)
        try {
            pipeline.processFrame(frame); assertTrue(events.filterIsInstance<SpeechActivityObserved>().isEmpty())
            pipeline.beginCapture(SpeechInputTurn("capture", boundary = CaptureBoundary.EXTERNAL))
            repeat(25) { pipeline.processFrame(frame) }
            val observations = events.filterIsInstance<SpeechActivityObserved>()
            assertEquals(listOf(100L, 200L, 300L, 400L, 500L), observations.map { it.sustainedSpeechMs })
            assertTrue(observations.all { it.captureId == "capture" && it.inputRouteValid })
            voiced = false; pipeline.processFrame(frame); voiced = true
            repeat(5) { pipeline.processFrame(frame) }
            assertEquals(100L, events.filterIsInstance<SpeechActivityObserved>().last().sustainedSpeechMs)
        } finally { pipeline.reset(); scope.cancel() }
    }
}
