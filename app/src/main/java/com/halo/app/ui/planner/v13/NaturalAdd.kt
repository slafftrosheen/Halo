package com.halo.app.ui.planner.v13

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * halo 13.0 · "Swimming with Mia every Thursday at 5" → a draft the person checks before saving.
 * Rule-based on the phone (EN + RU) so it works with no AI brain; when an AI brain is available it may refine
 * the draft, but the preview card is ALWAYS shown and nothing is saved without a tap.
 */
data class Draft(
    val title: String, val date: LocalDate?, val time: LocalTime?, val weekly: DayOfWeek?, val isTask: Boolean,
    val family: Boolean, val unsure: List<String>,   // words halo wasn't sure about → shown in apricot
)

object NaturalAdd {
    // Explicit Unicode word boundaries work with both Android ICU and the desktop JDK.
    private const val WORD_START = """(?<![\p{L}\p{N}_])"""
    private const val WORD_END = """(?![\p{L}\p{N}_])"""
    private val DAYS_EN = mapOf("monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY)
    private val DAYS_RU = mapOf("понедельник" to DayOfWeek.MONDAY, "вторник" to DayOfWeek.TUESDAY, "сред" to DayOfWeek.WEDNESDAY,
        "четверг" to DayOfWeek.THURSDAY, "пятниц" to DayOfWeek.FRIDAY, "суббот" to DayOfWeek.SATURDAY, "воскресень" to DayOfWeek.SUNDAY)
    private val TIME = Regex(
        """${WORD_START}(?:at|в)\s+([0-9]{1,2})(?:[:.]([0-9]{2}))?(?:\s*(am|pm))?(?![:.][0-9])$WORD_END""",
        RegexOption.IGNORE_CASE,
    )
    private val FAMILY = Regex(
        """${WORD_START}(?:with|с)\s+(?:mia|leo|mum|dad|kids|мией|лео|мамой|папой|детьми)$WORD_END""",
        RegexOption.IGNORE_CASE,
    )
    private val schedulingWords = Regex(
        """${WORD_START}(?:every|tomorrow|today|каждый|каждую|завтра|сегодня)$WORD_END""",
        RegexOption.IGNORE_CASE,
    )
    private val weekdayWords = (DAYS_EN + DAYS_RU).map { (word, day) ->
        val ending = if (word in DAYS_RU) """\p{L}*""" else ""
        Regex("""${WORD_START}(?:on\s+|в\s+)?${Regex.escape(word)}$ending$WORD_END""", RegexOption.IGNORE_CASE) to day
    }

    fun parse(text: String, today: LocalDate): Draft {
        val t = text.lowercase(Locale.ROOT)
        val unsure = mutableListOf<String>()
        val day = weekdayWords.mapNotNull { (words, day) -> words.find(t)?.range?.first?.let { it to day } }
            .minByOrNull { it.first }?.second
        val weekly = if (day != null && (t.contains("every") || t.contains("кажд"))) day else null
        val date = when {
            t.contains("tomorrow") || t.contains("завтра") -> today.plusDays(1)
            t.contains("today") || t.contains("сегодня") -> today
            day != null -> today.with(TemporalAdjusters.next(day))
            else -> null
        }
        val timeText = TIME.find(text)
        val time = timeText?.let { m ->
            var hour = m.groupValues[1].toInt()
            val minuteText = m.groupValues[2]
            val minute = minuteText.ifEmpty { "0" }.toInt()
            val amPm = m.groupValues[3].lowercase(Locale.ROOT)
            if (minute !in 0..59 || if (amPm.isEmpty()) hour !in 0..23 else hour !in 1..12) return@let null
            if (amPm.isNotEmpty()) hour = hour % 12 + if (amPm == "pm") 12 else 0
            if (amPm.isEmpty() && minuteText.isEmpty() && hour in 1..7 && m.groupValues[1].length == 1) {
                hour += 12
                unsure += m.value
            }
            LocalTime.of(hour, minute)
        }
        val withTimeRemoved = if (timeText != null && time != null) text.removeRange(timeText.range) else text
        val title = withTimeRemoved.replace(schedulingWords, "")
            .let { s -> weekdayWords.fold(s) { acc, (words, _) -> acc.replace(words, "") } }
            .replace(Regex("\\s+"), " ").trim().replaceFirstChar { it.uppercase() }
        return Draft(title, date, time, weekly, isTask = time == null, family = FAMILY.containsMatchIn(t), unsure = unsure)
    }
}
