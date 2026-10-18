package com.halo.app.audio

import com.halo.sdk.runtime.*
import org.junit.Assert.*
import org.junit.Test

class VoicePipelineTraceTest {
    @Test fun bufferedEndpointToFirstAudioUsesRealEventTimes() {
        val trace = VoicePipelineTrace()
        trace.onEvent(InteractionLatencyUpdated(trace = InteractionLatencyTrace(
            interactionId = "capture", startedAtMs = 1000, speechEndedAtMs = 1900)))
        trace.onEvent(SpeechRecognized(id = "capture", timestamp = 2300,
            input = SpeechInputTurn("capture"), text = "redacted", locale = "en"))
        trace.onEvent(CognitiveRequestStarted(requestId = "model", interactionId = "model-turn"))
        trace.onEvent(CognitiveTokenReceived(requestId = "model", interactionId = "model-turn",
            token = "secret token", timestamp = 2700))
        trace.onEvent(StreamingSpeechPrepared(requestId = "model", interactionId = "model-turn",
            sourceCaptureId = "capture", target = SpeechOutputTarget.Phone,
            fallback = SpeechOutputFallback.DISALLOW_PHONE))
        val result = trace.onEvent(TtsStarted(transactionId = "model",
            interactionId = "model-turn", timestamp = 3100))!!
        assertEquals(1200L, result.endOfSpeechToFirstAudioMs)
        assertEquals(400L, result.endOfSpeechToTranscriptMs)
        assertEquals(400L, result.transcriptToFirstTokenMs)
        assertEquals(2100L, result.captureToFirstAudioMs)
        assertTrue(trace.traces.value.none { it.toString().contains("secret") || it.toString().contains("redacted") })
    }

    @Test fun liveSpeechHasNoInventedEndpointOrLatency() {
        val trace = VoicePipelineTrace()
        trace.onEvent(InteractionLatencyUpdated(trace = InteractionLatencyTrace(
            interactionId = "capture", startedAtMs = 100)))
        trace.onEvent(SpeechRecognized(id = "capture", timestamp = 500,
            input = SpeechInputTurn("capture"), text = "private", locale = "en"))
        val result = trace.onEvent(TtsStarted(transactionId = "capture", timestamp = 800))!!
        assertNull(result.endOfSpeechToFirstAudioMs)
        assertNull(result.endOfSpeechToTranscriptMs)
        assertEquals(700L, result.captureToFirstAudioMs)
    }

    @Test fun cancelledOrFailedStageDoesNotCreateAudioEvidence() {
        val trace = VoicePipelineTrace()
        trace.onEvent(WorkloadStageEvent("capture", "capture", "speech-recognition",
            "STT", WorkloadStageState.CANCELLED, reasonCode = "OWNER_CANCELLED", durationMs = 180))
        val result = trace.onEvent(TtsCancelled(transactionId = "capture", timestamp = 200))!!
        assertNull(result.playbackStartedAtMs)
        assertEquals("CANCELLED", result.state)
        assertEquals(180L, result.stageDurationsMs["STT/speech-recognition"])
    }

    @Test fun boundedHistoryDoesNotRetainOldInteractions() {
        val trace = VoicePipelineTrace(capacity = 2)
        for (id in listOf("a", "b", "c")) {
            trace.onEvent(InteractionLatencyUpdated(trace = InteractionLatencyTrace(id, 100)))
        }
        assertEquals(listOf("b", "c"), trace.traces.value.map { it.interactionId })
    }

    @Test fun staleOutputCompletionCannotOverwriteNewPlayback() {
        val trace = VoicePipelineTrace()
        trace.onEvent(TtsRequested(text = "first", transactionId = "old", interactionId = "turn"))
        trace.onEvent(TtsRequested(text = "second", transactionId = "new", interactionId = "turn"))
        trace.onEvent(TtsStarted(transactionId = "new", interactionId = "turn", timestamp = 900))
        trace.onEvent(TtsCancelled(transactionId = "old", interactionId = "turn", timestamp = 930))
        trace.onEvent(TtsStarted(transactionId = "old", interactionId = "turn", timestamp = 950))
        assertEquals("SPEAKING", trace.traces.value.single().state)
        assertEquals(900L, trace.traces.value.single().playbackStartedAtMs)
        trace.onEvent(TtsCompleted(transactionId = "new", interactionId = "turn", timestamp = 1000))
        assertEquals("COMPLETED", trace.traces.value.single().state)
    }
}
