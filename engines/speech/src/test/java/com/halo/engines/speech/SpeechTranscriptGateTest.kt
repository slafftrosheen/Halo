package com.halo.engines.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTranscriptGateTest {

    private val gate = SpeechTranscriptGate()

    @Test
    fun unknownVadWithRecognizedTextIsNotAnnotationOnlyBecauseVoicedDurationIsZero() {
        val result = gate.evaluate(
            transcript = "set alarm",
            quality = SpeechRecognitionQuality(
                confidence = null,
                audioDurationMs = 740L,
                voicedDurationMs = 0L,
                voicedRatio = 0f,
                peakAmplitude = null,
                qualityEstimator = "UNKNOWN",
            ),
        )

        assertTrue(result is SpeechGateResult.Accepted)
    }

    @Test
    fun lowGainWearableSpeechFromEnergyFallbackIsNotRejectedByRatioAlone() {
        val result = gate.evaluate(
            transcript = "What is the current time?",
            quality = SpeechRecognitionQuality(
                audioDurationMs = 3_353L,
                voicedDurationMs = 160L,
                voicedRatio = 0.0477f,
                peakAmplitude = 2_543,
                language = "en",
                qualityEstimator = "SIMPLE_ENERGY",
                source = "Wearable"
            )
        )

        assertTrue(result is SpeechGateResult.Accepted)
    }

    @Test
    fun sileroLongCaptureStillRejectsVeryLowVoicedRatio() {
        val result = gate.evaluate(
            transcript = "What is the current time?",
            quality = SpeechRecognitionQuality(
                audioDurationMs = 3_353L,
                voicedDurationMs = 160L,
                voicedRatio = 0.0477f,
                peakAmplitude = 2_543,
                language = "en",
                qualityEstimator = "SILERO",
                source = "Wearable"
            )
        )

        assertTrue(result is SpeechGateResult.Rejected)
        assertEquals(
            SpeechRejectionReason.INSUFFICIENT_VOICED_AUDIO,
            (result as SpeechGateResult.Rejected).reason
        )
    }

    @Test
    fun energyFallbackStillRejectsInsufficientAbsoluteVoicedAudio() {
        val result = gate.evaluate(
            transcript = "What is the current time?",
            quality = SpeechRecognitionQuality(
                audioDurationMs = 4_900L,
                voicedDurationMs = 80L,
                voicedRatio = 0.016f,
                peakAmplitude = 2_000,
                language = "en",
                qualityEstimator = "SIMPLE_ENERGY",
                source = "Wearable"
            )
        )

        assertTrue(result is SpeechGateResult.Rejected)
        assertEquals(
            SpeechRejectionReason.INSUFFICIENT_VOICED_AUDIO,
            (result as SpeechGateResult.Rejected).reason
        )
    }

    @Test
    fun repetitiveTranscriptRemainsRejectedOnEnergyFallback() {
        val result = gate.evaluate(
            transcript = "hello hello hello hello hello hello",
            quality = SpeechRecognitionQuality(
                audioDurationMs = 2_500L,
                voicedDurationMs = 600L,
                voicedRatio = 0.24f,
                peakAmplitude = 4_000,
                language = "en",
                qualityEstimator = "SIMPLE_ENERGY",
                source = "Wearable"
            )
        )

        assertTrue(result is SpeechGateResult.Rejected)
        assertEquals(
            SpeechRejectionReason.REPETITIVE_HALLUCINATION,
            (result as SpeechGateResult.Rejected).reason
        )
    }
}
