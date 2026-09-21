package com.halo.engines.speech

enum class SpeechRejectionReason {
    EMPTY,
    NON_SPEECH,
    LOW_CONFIDENCE,
    TOO_SHORT,
    REPETITIVE_NOISE_TOKEN,
    CANCELLED
}

data class SpeechRecognitionQuality(
    val confidence: Float? = null,
    val noSpeechProbability: Float? = null,
    val averageLogProbability: Float? = null,
    val speechDurationMs: Long = 0L,
    val voicedRatio: Float? = null,
    val peakAmplitude: Int? = null,
    val source: String = "unknown"
)

sealed interface SpeechGateResult {
    data class Accepted(val transcript: String, val quality: SpeechRecognitionQuality) : SpeechGateResult
    data class Rejected(val reason: SpeechRejectionReason, val quality: SpeechRecognitionQuality) : SpeechGateResult
}

class SpeechTranscriptGate {
    fun evaluate(
        transcript: String,
        quality: SpeechRecognitionQuality
    ): SpeechGateResult {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            return SpeechGateResult.Rejected(SpeechRejectionReason.EMPTY, quality)
        }

        if (trimmed.none(Char::isLetterOrDigit)) {
            return SpeechGateResult.Rejected(SpeechRejectionReason.NON_SPEECH, quality)
        }

        // Parentheses or bracketed sound annotations e.g. (door closing), [laughter]
        val isSoundAnnotation = (trimmed.startsWith("(") && trimmed.endsWith(")")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        if (isSoundAnnotation) {
            return SpeechGateResult.Rejected(SpeechRejectionReason.NON_SPEECH, quality)
        }

        // Repetitive noise token check (e.g. "door closing door closing")
        val words = trimmed.lowercase().split("\\s+".toRegex())
        if (words.size >= 4 && words.distinct().size == 1) {
            return SpeechGateResult.Rejected(SpeechRejectionReason.REPETITIVE_NOISE_TOKEN, quality)
        }

        if (quality.noSpeechProbability != null && quality.noSpeechProbability > 0.85f) {
            return SpeechGateResult.Rejected(SpeechRejectionReason.NON_SPEECH, quality)
        }

        if (quality.confidence != null && quality.confidence < 0.20f) {
            return SpeechGateResult.Rejected(SpeechRejectionReason.LOW_CONFIDENCE, quality)
        }


        if (quality.peakAmplitude != null && quality.peakAmplitude == 0) {
            return SpeechGateResult.Rejected(SpeechRejectionReason.NON_SPEECH, quality)
        }

        if (quality.voicedRatio != null && quality.voicedRatio <= 0f) {
            return SpeechGateResult.Rejected(SpeechRejectionReason.NON_SPEECH, quality)
        }

        return SpeechGateResult.Accepted(trimmed, quality)
    }
}
