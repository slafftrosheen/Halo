package com.halo.engines.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechRejectionRecoveryTest {
    @Test fun silenceAndUnvoicedAudioMayRecaptureWithoutAdmittingTheirText() {
        assertTrue(SpeechRejectionReason.NO_SPEECH.allowsAutomaticRecapture)
        assertTrue(SpeechRejectionReason.INSUFFICIENT_VOICED_AUDIO.allowsAutomaticRecapture)
    }

    @Test fun cancellationAndUncertainTranscriptsNeverAuthorizeAnAutomaticRetry() {
        for (reason in listOf(SpeechRejectionReason.CANCELLED, SpeechRejectionReason.EMPTY,
            SpeechRejectionReason.LOW_RECOGNIZER_CONFIDENCE, SpeechRejectionReason.REPETITIVE_HALLUCINATION,
            SpeechRejectionReason.LANGUAGE_MISMATCH, SpeechRejectionReason.ANNOTATION_ONLY,
            SpeechRejectionReason.PUNCTUATION_ONLY)) assertFalse(reason.name, reason.allowsAutomaticRecapture)
    }
}
