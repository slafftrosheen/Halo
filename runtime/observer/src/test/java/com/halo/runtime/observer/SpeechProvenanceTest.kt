package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import org.junit.Assert.*
import org.junit.Test

class SpeechProvenanceTest {
    @Test fun rejectedTranscriptKeepsModelTimingAndFallbackQuality() {
        val event = SpeechRejected(text = "test transcript", reason = "REPETITIVE_HALLUCINATION", captureId = "capture",
            metrics = SpeechRecognitionMetrics(modelDisplayName = "Whisper Base", language = "lv", decodeMs = 420, audioDurationMs = 2000),
            quality = RecognitionQuality(qualityEstimator = "SIMPLE_ENERGY", voicedRatio = 0.5f))
        val timeline = DefaultTimelineEventMapper().map(event)!!
        assertEquals("Speech rejected", timeline.title)
        assertEquals("capture", timeline.correlationId)
        assertTrue(timeline.details["stt"]!!.contains("Whisper Base"))
        assertTrue(timeline.details["stt"]!!.contains("lv"))
        assertTrue(timeline.details["stt"]!!.contains("420 ms"))
        assertEquals("Energy fallback", timeline.details["estimator"])
        assertTrue(timeline.description.contains(event.reason))
    }
}
