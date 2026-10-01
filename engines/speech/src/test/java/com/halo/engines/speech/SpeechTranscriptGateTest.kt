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
            transcript = "I am going to the store and I am going to the store and I am going to the store",
            quality = SpeechRecognitionQuality(
                audioDurationMs = 4_500L,
                voicedDurationMs = 1_600L,
                voicedRatio = 0.35f,
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

    @Test
    fun shortNaturalHumanRepetitionsAreAccepted() {
        val cases = listOf(
            "no no no",
            "yes yes yes",
            "very very good",
            "wait wait wait",
            "thank you thank you"
        )
        val quality = SpeechRecognitionQuality(
            audioDurationMs = 1_500L,
            voicedDurationMs = 500L,
            voicedRatio = 0.33f,
            peakAmplitude = 3_000,
            qualityEstimator = "SILERO"
        )

        for (text in cases) {
            val result = gate.evaluate(text, quality)
            assertTrue("Expected accepted for '$text' but got $result", result is SpeechGateResult.Accepted)
        }
    }
}
