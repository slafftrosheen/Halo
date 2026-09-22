package com.halo.cognition.language

import java.time.LocalTime

class TemporalExpressionParser {

    private val wordHours = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12
    )

    private val timePattern = Regex(
        "\\b(today|tomorrow|tonight)?(?:\\s*(?:at)?\\s*)?(\\d{1,2}(?::\\d{2})?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|morning|afternoon|evening|night)?\\s*(am|pm)?\\b",
        RegexOption.IGNORE_CASE
    )

    fun parse(expression: String): TemporalValue? {
        val normalized = expression.trim().lowercase()
        val match = timePattern.find(normalized) ?: return null

        val dayPart = match.groupValues[1]
        val timePart = match.groupValues[2]
        val amPm = match.groupValues[3]

        val relation = when (dayPart) {
            "today", "tonight" -> DayRelation.TODAY
            "tomorrow" -> DayRelation.TOMORROW
            else -> null
        }

        if (timePart.isBlank()) {
            if (dayPart == "tonight") {
                return TemporalValue.DayPart(DayPartKind.NIGHT, relation)
            }
            return null
        }

        val partKind = when (timePart) {
            "morning" -> DayPartKind.MORNING
            "afternoon" -> DayPartKind.AFTERNOON
            "evening" -> DayPartKind.EVENING
            "night" -> DayPartKind.NIGHT
            else -> null
        }

        if (partKind != null) {
            return TemporalValue.DayPart(partKind, relation)
        }

        return try {
            val (hourRaw, minStr) = if (timePart.contains(":")) {
                val parts = timePart.split(":")
                Pair(parts[0].toInt(), parts[1])
            } else {
                Pair(wordHours[timePart] ?: timePart.toInt(), "00")
            }

            var hour = hourRaw
            if (amPm == "pm" && hour < 12) hour += 12
            else if (amPm == "am" && hour == 12) hour = 0

            val time = LocalTime.of(hour, minStr.toInt())
            TemporalValue.TimeOfDay(time, relation)
        } catch (e: Exception) {
            null
        }
    }
}
