package com.halo.app.ui.everyday

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

internal data class PlannerReminderCandidate(
    val title: String,
    val start: LocalDateTime,
    val weekday: DayOfWeek?,
    val lead: Int,
    val timeGuess: String?,
    val dateGuess: Boolean,
)

/** Keep any uncertain interpretation visible and editable in the draft. */
internal fun plannerReminderCandidate(
    request: String,
    selectedDay: LocalDate,
    now: LocalDateTime = LocalDateTime.now(),
): PlannerReminderCandidate {
    val words = request.trim().replace(Regex("\\s+"), " ")
    val command = reminderIntent.find(words)
    val prefix = command?.let { words.substring(0, it.range.first).trim(' ', ',', ':') }.orEmpty()
    val spokenTitle = command?.let { words.substring(it.range.last + 1).trim(' ', ',', '.', ':') } ?: words
    val suffix = reminderScheduleSuffix.find(spokenTitle)
    val title = (suffix?.let { spokenTitle.substring(0, it.range.first).trim() } ?: spokenTitle).take(200)
    val schedule = listOfNotNull(prefix.takeIf(String::isNotBlank), suffix?.value).joinToString(" ")
    val weekday = reminderWeekday.find(schedule)?.let { DayOfWeek.valueOf(it.groupValues[1].uppercase(Locale.ROOT)) }
    val repeats = weekday != null && everyWeekday.containsMatchIn(schedule)
    val explicitDate = reminderIsoDate.find(schedule)?.value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val dayWord = reminderDayWord.find(schedule)?.value?.lowercase(Locale.ROOT)
    val matchedTime = reminderClock.find(schedule)?.let { match ->
        val spokenHour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3].lowercase(Locale.ROOT)
        val hour = if (meridiem.isEmpty()) spokenHour else {
            if (spokenHour !in 1..12) return@let null
            spokenHour % 12 + if (meridiem == "pm") 12 else 0
        }
        runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
    val namedTime = reminderPartOfDay.find(schedule)?.value?.lowercase(Locale.ROOT)
    val anchor = maxOf(selectedDay, now.toLocalDate())
    var date = explicitDate ?: when {
        weekday != null -> anchor.with(TemporalAdjusters.nextOrSame(weekday))
        dayWord == "today" -> now.toLocalDate()
        dayWord == "tomorrow" -> now.toLocalDate().plusDays(1)
        else -> anchor
    }
    val defaultTime = if (date == now.toLocalDate()) now.plusHours(1).withSecond(0).withNano(0).toLocalTime()
        else LocalTime.of(9, 0)
    val time = matchedTime ?: when (namedTime) {
        "morning" -> LocalTime.of(9, 0)
        "afternoon" -> LocalTime.of(15, 0)
        "evening" -> LocalTime.of(19, 0)
        "noon" -> LocalTime.NOON
        else -> defaultTime
    }
    if (weekday != null && date.atTime(time) <= now && explicitDate == null) date = date.plusWeeks(1)
    if (weekday == null && dayWord == null && explicitDate == null && date.atTime(time) <= now) date = date.plusDays(1)
    val lead = reminderMinutes.find(schedule)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 10080) ?: 0
    return PlannerReminderCandidate(title, date.atTime(time), weekday.takeIf { repeats }, lead,
        timeGuess = if (matchedTime != null) null else namedTime ?: "",
        dateGuess = explicitDate == null && weekday == null && dayWord == null)
}

private val reminderIntent = Regex("\\bremind\\s+me(?:\\s+to)?\\b", RegexOption.IGNORE_CASE)
private const val days = "Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday"
private const val partOfDay = "morning|afternoon|evening|noon"
private const val clock = "(?:at\\s+\\d{1,2}(?::\\d{2})?(?:\\s*(?:am|pm))?|\\d{1,2}:\\d{2}(?:\\s*(?:am|pm))?|\\d{1,2}\\s*(?:am|pm))"
private val reminderClock = Regex(
    "(?:\\bat\\s+|(?=\\d{1,2}:\\d{2}\\b)|(?=\\d{1,2}\\s*(?:am|pm)\\b))(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b",
    RegexOption.IGNORE_CASE,
)
private val reminderScheduleSuffix = Regex(
    "\\s(?:(?:every|on)\\s+(?:$days)\\b|(?:$days|today|tomorrow)\\b|\\d{4}-\\d{2}-\\d{2}\\b|(?:at\\s+)?(?:$partOfDay)\\b|$clock)(?:\\b|$).*",
    RegexOption.IGNORE_CASE,
)
private val reminderWeekday = Regex("\\b($days)\\b", RegexOption.IGNORE_CASE)
private val everyWeekday = Regex("\\bevery\\s+(?:$days)\\b", RegexOption.IGNORE_CASE)
private val reminderIsoDate = Regex("(?<!\\d)\\d{4}-\\d{2}-\\d{2}(?!\\d)")
private val reminderDayWord = Regex("\\b(?:today|tomorrow)\\b", RegexOption.IGNORE_CASE)
private val reminderPartOfDay = Regex("\\b(?:$partOfDay)\\b", RegexOption.IGNORE_CASE)
private val reminderMinutes = Regex("\\b(\\d{1,5})\\s+minutes?\\s+before\\b", RegexOption.IGNORE_CASE)
