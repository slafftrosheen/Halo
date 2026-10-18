package com.halo.app.audio

import org.junit.Assert.*
import org.junit.Test

class VoiceLatencyReportTest {
    private fun measured(duration: Long, index: Int, state: String = "COMPLETED") = VoicePipelineSnapshot(
        interactionId = "turn-$index", captureId = "capture-$index",
        captureStartedAtMs = 100, speechEndedAtMs = 1000,
        transcriptReadyAtMs = 1100, firstTokenAtMs = 1150,
        playbackStartedAtMs = 1000 + duration,
        stageDurationsMs = emptyMap(), state = state,
    )

    @Test fun doesNotPresentPercentilesBelowThirtyRealEndpoints() {
        val report = VoiceLatencyReport.from((0 until 29).map { measured(it.toLong(), it) })
        assertEquals(29, report.endpointSamples)
        assertNull(report.medianEosToAudioMs)
        assertNull(report.p95EosToAudioMs)
    }

    @Test fun p95AndMedianAreComputedOnlyFromRealBufferedEndpoints() {
        val complete = (1..40).map { measured(it * 10L, it) }
        val missing = measured(10L, 41).copy(speechEndedAtMs = null)
        val typed = measured(10L, 42).copy(captureId = null)
        val report = VoiceLatencyReport.from(complete + missing + typed)
        assertEquals(41, report.captured)
        assertEquals(40, report.endpointSamples)
        assertEquals(205L, report.medianEosToAudioMs)
        assertEquals(380L, report.p95EosToAudioMs)
        assertFalse(report.toDiagnosticText().contains("turn-"))
    }

    @Test fun failureAndCancellationAreNotCountedAsSuccess() {
        val report = VoiceLatencyReport.from(listOf(
            measured(20, 1), measured(30, 2, "CANCELLED").copy(playbackStartedAtMs = null),
            measured(40, 3, "OUTPUT_FAILED").copy(playbackStartedAtMs = null),
        ))
        assertEquals(3, report.captured)
        assertEquals(1, report.completed)
        assertEquals(1, report.cancelled)
        assertEquals(1, report.failed)
        assertEquals(1, report.audioStarted)
    }
}
