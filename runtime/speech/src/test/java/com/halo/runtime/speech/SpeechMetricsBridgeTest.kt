package com.halo.runtime.speech

import com.halo.sdk.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SpeechMetricsBridgeTest {
    @Test fun duplicateRecognitionIsAdmittedOnceAndUncertainTranscriptNeverReachesInterpretation() = runBlocking {
        for (confidence in listOf(0.9f, 0.4f)) {
            val stream = MutableSharedFlow<HaloEvent>()
            val results = mutableListOf<HaloEvent>()
            val bus = object : EventBus {
                override suspend fun publish(event: HaloEvent) {
                    if (event is ObservationCreated || event is SpeechRejected) results += event else stream.emit(event)
                }
                override fun events(): Flow<HaloEvent> = stream
                override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = stream.filter { type.isInstance(it) }.map { type.cast(it) }
            }
            val bridge = SpeechPerceptionBridge(bus, this); bridge.start(); yield()
            val event = SpeechRecognized(text = "Open settings", locale = "en", timestamp = 0,
                input = SpeechInputTurn("exact", conversationId = "original"), confidence = confidence)
            bus.publish(event); yield(); bus.publish(event); yield()
            assertEquals(1, results.size)
            if (confidence > 0.55f) {
                val observation = (results.single() as ObservationCreated).observation
                assertEquals("original", observation.metadata["conversationId"])
                assertEquals("exact", observation.interactionId)
            } else {
                assertEquals("TRANSCRIPT_REVIEW", (results.single() as SpeechRejected).reason)
            }
            bridge.stop()
        }
    }
    @Test fun acceptedAndRejectedRetainNeutralMetricsWithoutDuplicateObservations() = runBlocking {
        for (text in listOf("Atver Spotify", "")) {
            val output = CompletableDeferred<HaloEvent>()
            val published = mutableListOf<HaloEvent>()
            val stream = MutableSharedFlow<HaloEvent>()
            val bus = object : EventBus {
                override suspend fun publish(event: HaloEvent) {
                    published.add(event)
                    if (event is ObservationCreated || event is SpeechRejected) output.complete(event)
                    else stream.emit(event)
                }
                override fun events(): Flow<HaloEvent> = stream
                override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = stream.filter { type.isInstance(it) }.map { type.cast(it) }
            }
            val bridge = SpeechPerceptionBridge(bus, this); bridge.start(); yield()
            val metrics = SpeechRecognitionMetrics(providerId = "neutral-provider", modelId = "model", language = "lv", decodeMs = 10, audioDurationMs = 500)
            val quality = RecognitionQuality(audioDurationMs = 500, voicedDurationMs = 400, voicedRatio = 0.8f, peakAmplitude = 1234)
            bus.publish(SpeechRecognized(timestamp = 0, text = text, locale = "lv", input = SpeechInputTurn("capture"), quality = quality, metrics = metrics))
            val result = withTimeout(5000) { output.await() }
            if (result is ObservationCreated) {
                assertSame(metrics, result.observation.metadata["recognitionMetrics"])
                assertSame(quality, result.observation.metadata["recognitionQuality"])
                assertEquals(1, published.count { it is ObservationCreated })
            } else {
                result as SpeechRejected
                assertSame(metrics, result.metrics); assertEquals("capture", result.input?.captureId)
                assertEquals(0, published.count { it is ObservationCreated })
            }
            bridge.stop()
        }
    }
    @Test fun rtfIsUnavailableForZeroDuration() {
        assertNull(SpeechRecognitionMetrics(decodeMs = 20, audioDurationMs = 0).realTimeFactor)
        assertEquals(0.2, SpeechRecognitionMetrics(decodeMs = 100, audioDurationMs = 500).realTimeFactor!!, 0.001)
    }
}
