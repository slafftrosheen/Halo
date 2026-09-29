package com.halo.engines.speech

enum class SpeechRejectionReason {
    EMPTY, PUNCTUATION_ONLY, NO_SPEECH, INSUFFICIENT_VOICED_AUDIO,
    LOW_RECOGNIZER_CONFIDENCE, REPETITIVE_HALLUCINATION, LANGUAGE_MISMATCH,
    ANNOTATION_ONLY, CANCELLED
}

data class SpeechAdmissionPolicy(
    val minimumVoicedMs: Long = 120L,
    val minimumVoicedRatioForLongCapture: Float = 0.08f,
    val longCaptureThresholdMs: Long = 2_000L,
    val minimumRecognizerConfidence: Float? = 0.20f,
    val rejectPunctuationOnly: Boolean = true,
    val languageSanityEnabled: Boolean = true
)

data class RepetitionAnalysis(
    val dominantPhraseRatio: Float,
    val repeatedRuns: Int,
    val uniqueTokenRatio: Float
)

data class SpeechRecognitionQuality(
    val confidence: Float? = null,
    val noSpeechProbability: Float? = null,
    val averageLogProbability: Float? = null,
    val audioDurationMs: Long = 0L,
    val voicedDurationMs: Long = 0L,
    val voicedRatio: Float? = null,
    val peakAmplitude: Int? = null,
    val language: String = "auto",
    val qualityEstimator: String = "UNKNOWN",
    val source: String = "unknown"
)

sealed interface SpeechGateResult {
    data class Accepted(val transcript: String, val quality: SpeechRecognitionQuality) : SpeechGateResult
    data class Rejected(val reason: SpeechRejectionReason, val quality: SpeechRecognitionQuality) : SpeechGateResult
}

class SpeechTranscriptGate(private val policy: SpeechAdmissionPolicy = SpeechAdmissionPolicy()) {
    fun evaluate(transcript: String, quality: SpeechRecognitionQuality): SpeechGateResult {
        val trimmed = transcript.trim()
        fun reject(reason: SpeechRejectionReason) = SpeechGateResult.Rejected(reason, quality)
        if (trimmed.isEmpty()) return reject(SpeechRejectionReason.EMPTY)
        if (policy.rejectPunctuationOnly && trimmed.none(Char::isLetterOrDigit)) {
            return reject(SpeechRejectionReason.PUNCTUATION_ONLY)
        }
        if ((trimmed.startsWith("(") && trimmed.endsWith(")")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) return reject(SpeechRejectionReason.ANNOTATION_ONLY)
        if (quality.noSpeechProbability?.let { it > 0.85f } == true || quality.peakAmplitude == 0) {
            return reject(SpeechRejectionReason.NO_SPEECH)
        }
        val hasKnownVad = !quality.qualityEstimator.equals("UNKNOWN", ignoreCase = true)
        if (hasKnownVad && quality.voicedRatio != null && quality.voicedDurationMs < policy.minimumVoicedMs) {
            return reject(SpeechRejectionReason.INSUFFICIENT_VOICED_AUDIO)
        }
        // A whole-capture voiced-ratio threshold is only trustworthy when the
        // estimator provides real VAD semantics. SIMPLE_ENERGY is deliberately a
        // fallback and underestimates low-gain wearable speech; absolute voiced
        // duration still protects that path from empty/noise-only captures.
        val reliableVoicedRatio =
            quality.qualityEstimator.equals("SILERO", ignoreCase = true)
        if (reliableVoicedRatio &&
            quality.audioDurationMs >= policy.longCaptureThresholdMs &&
            quality.voicedRatio?.let { it < policy.minimumVoicedRatioForLongCapture } == true
        ) return reject(SpeechRejectionReason.INSUFFICIENT_VOICED_AUDIO)
        if (policy.minimumRecognizerConfidence != null &&
            quality.confidence?.let { it < policy.minimumRecognizerConfidence } == true
        ) return reject(SpeechRejectionReason.LOW_RECOGNIZER_CONFIDENCE)

        val repetition = analyzeRepetition(trimmed)
        if (repetition.repeatedRuns >= 2 && repetition.dominantPhraseRatio >= 0.65f &&
            repetition.uniqueTokenRatio <= 0.5f
        ) return reject(SpeechRejectionReason.REPETITIVE_HALLUCINATION)

        if (policy.languageSanityEnabled && quality.language.startsWith("en", ignoreCase = true) &&
            quality.confidence == null && (quality.voicedRatio ?: 1f) < 0.15f &&
            nonLatinLetterRatio(trimmed) > 0.75f
        ) return reject(SpeechRejectionReason.LANGUAGE_MISMATCH)
        return SpeechGateResult.Accepted(trimmed, quality)
    }

    private fun analyzeRepetition(text: String): RepetitionAnalysis {
        val tokens = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .filter(String::isNotBlank)
        if (tokens.isEmpty()) return RepetitionAnalysis(0f, 0, 1f)
        val clauses = text.lowercase().split(Regex("[,，。.!?;；]+"))
            .map(String::trim).filter(String::isNotBlank)
        val counts = clauses.groupingBy { it }.eachCount().values
        val dominant = counts.maxOrNull() ?: 1
        val characterLoop = analyzeCharacterNgrams(text)
        return RepetitionAnalysis(
            dominantPhraseRatio = maxOf(
                if (clauses.isEmpty()) 0f else dominant.toFloat() / clauses.size,
                characterLoop.first
            ),
            repeatedRuns = maxOf(dominant - 1, characterLoop.second),
            uniqueTokenRatio = tokens.distinct().size.toFloat() / tokens.size
        )
    }

    private fun analyzeCharacterNgrams(text: String): Pair<Float, Int> {
        val normalized = text.lowercase().filter(Char::isLetterOrDigit)
        if (normalized.length < MIN_NGRAM_SIZE * 3) return 0f to 0
        val ngramSize = (normalized.length / 3).coerceIn(MIN_NGRAM_SIZE, MAX_NGRAM_SIZE)
        val counts = normalized.windowed(ngramSize).groupingBy { it }.eachCount()
        val dominant = counts.values.maxOrNull() ?: 1
        val coveredRatio = (dominant * ngramSize).toFloat().coerceAtMost(normalized.length.toFloat()) /
            normalized.length
        return coveredRatio to (dominant - 1)
    }

    private fun nonLatinLetterRatio(text: String): Float {
        val letters = text.filter(Char::isLetter)
        if (letters.isEmpty()) return 0f
        val nonLatin = letters.count {
            Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN
        }
        return nonLatin.toFloat() / letters.length
    }

    private companion object {
        const val MIN_NGRAM_SIZE = 4
        const val MAX_NGRAM_SIZE = 24
    }
}
