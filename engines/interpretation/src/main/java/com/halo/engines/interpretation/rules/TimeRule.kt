package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.Interpretation
import com.halo.engines.interpretation.api.InterpretationBuilder
import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.knowledge.Confidence

class TimeRule : InterpretationRule {
    override val priority: Int = 600

    override suspend fun applies(
        context: InterpretationContext
    ): Boolean = transcript(context)?.let(timePattern::containsMatchIn) == true

    override suspend fun interpret(
        context: InterpretationContext
    ): Interpretation? {
        val transcript = transcript(context) ?: return null
        val match = timePattern.find(transcript) ?: return null
        val expression = normalize(match.groupValues[1], match.groupValues[2])
        val confidence = context.observation.confidence

        return InterpretationBuilder()
            .time(expression, 0.98f)
            .slot("time", expression)
            .confidence(
                Confidence(
                    lexical = confidence,
                    contextual = 0.95f,
                    semantic = 0.98f
                )
            )
            .build()
    }

    private fun transcript(context: InterpretationContext): String? =
        (context.observation.payload as? SpeechRecognizedPayload)
            ?.transcript
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun normalize(
        day: String,
        rawTime: String
    ): String {
        if (rawTime.isBlank()) {
            return day.lowercase()
        }

        val timeMatch = clockPattern.matchEntire(rawTime.trim()) ?: return "$day $rawTime"
        var hour = timeMatch.groupValues[1].toInt()
        val minute = timeMatch.groupValues[2].ifBlank { "00" }
        val period = timeMatch.groupValues[3].lowercase()

        if (period == "pm" && hour < 12) {
            hour += 12
        } else if (period == "am" && hour == 12) {
            hour = 0
        }

        return "${day.lowercase()} ${hour.toString().padStart(2, '0')}:$minute"
    }

    private companion object {
        val timePattern = Regex(
            "\\b(today|tomorrow|tonight)(?:\\s+at\\s+(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?))?",
            RegexOption.IGNORE_CASE
        )
        val clockPattern = Regex(
            "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            RegexOption.IGNORE_CASE
        )
    }
}
