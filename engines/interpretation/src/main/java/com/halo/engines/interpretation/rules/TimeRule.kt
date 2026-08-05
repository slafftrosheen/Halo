package com.halo.engines.interpretation.rules

import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.engines.interpretation.api.InterpretationRule
import com.halo.engines.interpretation.api.RuleMatch
import com.halo.engines.interpretation.model.TimeEntity
import com.halo.engines.speech.SpeechRecognizedPayload

class TimeRule : InterpretationRule {
    override val priority: Int = 600

    override suspend fun match(context: InterpretationContext): RuleMatch? {
        val transcript = transcript(context) ?: return null
        val match = timePattern.find(transcript) ?: return null
        val expression = normalize(match.groupValues[1], match.groupValues[2])

        return RuleMatch(
            confidence = 0.98f,
            entities = listOf(TimeEntity(expression, 0.98f)),
            consumedTokens = match.value.tokenCount(),
            diagnostics = "Extracted time $expression"
        )
    }

    private fun transcript(context: InterpretationContext): String? =
        (context.observation.payload as? SpeechRecognizedPayload)
            ?.transcript
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun normalize(day: String, rawTime: String): String {
        if (rawTime.isBlank()) {
            return day.lowercase()
        }

        val normalizedTime = wordHours[rawTime.trim().lowercase()]
            ?: rawTime.trim()
        val timeMatch = clockPattern.matchEntire(normalizedTime)
            ?: return "$day ${rawTime.trim().lowercase()}"
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

    private fun String.tokenCount(): Int =
        trim().split(Regex("\\s+")).count { it.isNotBlank() }

    private companion object {
        val timePattern = Regex(
            "\\b(today|tomorrow|tonight)(?:\\s+(?:at\\s+)?(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|morning|afternoon|evening))?",
            RegexOption.IGNORE_CASE
        )
        val clockPattern = Regex(
            "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?",
            RegexOption.IGNORE_CASE
        )
        val wordHours = mapOf(
            "one" to "1:00",
            "two" to "2:00",
            "three" to "3:00",
            "four" to "4:00",
            "five" to "5:00",
            "six" to "6:00",
            "seven" to "7:00",
            "eight" to "8:00",
            "nine" to "9:00",
            "ten" to "10:00",
            "eleven" to "11:00",
            "twelve" to "12:00"
        )
    }
}
