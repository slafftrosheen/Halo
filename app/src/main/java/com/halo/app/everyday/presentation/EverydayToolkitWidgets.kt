package com.halo.app.everyday.presentation

import com.halo.app.everyday.collectors.CollectorAttemptState
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.collectors.europeanAqiLabel
import com.halo.app.everyday.organizer.OrganizerKind
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

enum class EverydayWidgetStyle { GLASS, SOLID, CLEAR }
enum class EverydayWidgetShows { DEFAULT, THIS_WEEK, TODAY, NEXT_ONLY }

enum class EverydayWidgetTool(val route: String, val allowedInKids: Boolean = false) {
    SCAN("scan"), RECORD("record"), NOTE("quick_note"), TRANSLATE("translate"), PLANNER("planner"),
    FOCUS("focus"), ROUTINES("routines"), FAMILY("family"), STUDY("study"), KIDS("home", true)
}

/** Aggregate data the widget host can retain safely. These contain no learning or habit titles. */
data class WidgetLearning(val steps: Int, val updatedAt: Instant)
data class WidgetFocus(val durationMs: Long, val remainingMs: Long, val deadlineElapsed: Long)
data class WidgetWeekDay(val day: LocalDate, val label: String, val today: Boolean, val eventCount: Int)

object EverydayToolkitWidgets {
    val capture = listOf(EverydayCardKind.SCAN, EverydayCardKind.RECORD, EverydayCardKind.QUICK_NOTE,
        EverydayCardKind.TRANSLATE, EverydayCardKind.AIR_POLLEN)
    val planning = listOf(EverydayCardKind.PLANNER_WEEK, EverydayCardKind.FOCUS, EverydayCardKind.LITTLE_HABITS, EverydayCardKind.NEXT_RIDE)
    val family = listOf(EverydayCardKind.OUR_HOME, EverydayCardKind.LEARN, EverydayCardKind.KIDS_SKY, EverydayCardKind.YOUR_THINGS)
    val lockScreen = listOf(EverydayCardKind.LOCK_NEXT_EVENT, EverydayCardKind.NEXT_RIDE, EverydayCardKind.LOCK_RAIN, EverydayCardKind.FOCUS)
    val launcherKinds = capture + planning + family + listOf(EverydayCardKind.SMART_STACK, EverydayCardKind.LOCK_NEXT_EVENT, EverydayCardKind.LOCK_RAIN)
    private val wide = setOf(EverydayCardKind.PLANNER_WEEK, EverydayCardKind.OUR_HOME, EverydayCardKind.SMART_STACK)
    private val strips = setOf(EverydayCardKind.QUICK_NOTE, EverydayCardKind.NEXT_RIDE, EverydayCardKind.YOUR_THINGS)
    private val filtered = setOf(EverydayCardKind.PLANNER_WEEK,
        EverydayCardKind.SMART_STACK, EverydayCardKind.UPCOMING, EverydayCardKind.AGENDA, EverydayCardKind.TASKS, EverydayCardKind.REMINDERS)

    fun supportsShows(kind: EverydayCardKind) = kind in filtered
    fun defaultSize(kind: EverydayCardKind): EverydayWidgetSize = when (kind) {
        in wide, in strips -> EverydayWidgetSize.WIDE
        in launcherKinds -> EverydayWidgetSize.SMALL
        else -> EverydayWidgetSize.WIDE
    }
    fun previewHeight(config: EverydayCardConfig) = if (config.widgetSize == null && config.kind in strips) 92
        else (config.widgetSize ?: defaultSize(config.kind)).heightDp

    fun title(kind: EverydayCardKind) = when (kind) {
        EverydayCardKind.SCAN -> "Scan"
        EverydayCardKind.RECORD -> "Record"
        EverydayCardKind.QUICK_NOTE -> "Quick note"
        EverydayCardKind.TRANSLATE -> "Translate"
        EverydayCardKind.AIR_POLLEN -> "Air & pollen"
        EverydayCardKind.PLANNER_WEEK -> "Planner week"
        EverydayCardKind.FOCUS -> "Focus"
        EverydayCardKind.LITTLE_HABITS -> "Little habits"
        EverydayCardKind.NEXT_RIDE -> "Next ride"
        EverydayCardKind.OUR_HOME -> "Our home"
        EverydayCardKind.LEARN -> "Learn"
        EverydayCardKind.KIDS_SKY -> "Mila’s sky"
        EverydayCardKind.YOUR_THINGS -> "Your things"
        EverydayCardKind.LOCK_NEXT_EVENT -> "Next event"
        EverydayCardKind.LOCK_RAIN -> "Rain"
        EverydayCardKind.SMART_STACK -> "Smart stack"
        else -> error("Unsupported toolkit widget $kind")
    }

    fun shows(config: EverydayCardConfig, date: LocalDate, today: LocalDate, locale: Locale): Boolean =
        when (config.widgetShows) {
            EverydayWidgetShows.TODAY -> date == today
            EverydayWidgetShows.THIS_WEEK -> date >= firstDay(today, locale) && date < firstDay(today, locale).plusDays(7)
            else -> true
        }

    fun allowedStackKinds(): List<EverydayCardKind> = listOf(EverydayCardKind.PLANNER_WEEK, EverydayCardKind.FOCUS,
        EverydayCardKind.LITTLE_HABITS, EverydayCardKind.YOUR_THINGS)
    fun stackKinds(input: EverydaySnapshot): List<EverydayCardKind> {
        val order = allowedStackKinds()
        val hour = input.now.atZone(input.zone).hour
        val primary = when {
            input.kids -> EverydayCardKind.KIDS_SKY
            input.widgetFocus != null -> EverydayCardKind.FOCUS
            input.charging && (hour < 7 || hour >= 21) -> EverydayCardKind.YOUR_THINGS
            input.widgetHabitNow -> EverydayCardKind.LITTLE_HABITS
            hour in 13..17 -> EverydayCardKind.FOCUS
            else -> EverydayCardKind.PLANNER_WEEK
        }
        return if (input.kids) listOf(primary, EverydayCardKind.LEARN)
        else listOf(primary) + order.filterNot { it == primary }
    }

    fun week(input: EverydaySnapshot, locale: Locale): List<WidgetWeekDay> {
        val today = input.now.atZone(input.zone).toLocalDate()
        val first = firstDay(today, locale)
        return (0L..6L).map { offset ->
            val date = first.plusDays(offset)
            WidgetWeekDay(date, date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), date == today,
                input.occurrences.count { it.record.kind == OrganizerKind.EVENT && !it.completed &&
                    it.start.atZone(input.zone).toLocalDate() == date })
        }
    }

    private fun firstDay(date: LocalDate, locale: Locale) =
        date.with(java.time.temporal.TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))

    internal fun project(config: EverydayCardConfig, input: EverydaySnapshot, locale: Locale,
        text: (String) -> String): EverydayCardFace {
        val kind = config.kind
        val heading = text(title(kind))
        val date = input.now.atZone(input.zone).toLocalDate()
        val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(input.zone)
        fun tool(tool: EverydayWidgetTool): EverydayTarget = EverydayTarget.WidgetTool(
            if (input.kids && !tool.allowedInKids) EverydayWidgetTool.KIDS else tool)
        fun face(value: String, target: EverydayTarget, status: String = "On this phone",
            observed: Instant? = null, rows: List<EverydayCardRow> = emptyList()) =
            EverydayCardFace(heading, text(value), rows, text(status), target, observedAt = observed)

        if (input.kids && kind in capture + listOf(EverydayCardKind.FOCUS, EverydayCardKind.LITTLE_HABITS, EverydayCardKind.OUR_HOME)) {
            return face("Open Kids space", tool(EverydayWidgetTool.KIDS))
        }
        return when (kind) {
            EverydayCardKind.SCAN -> face("Open camera", tool(EverydayWidgetTool.SCAN), "Point & read")
            EverydayCardKind.RECORD -> face(if (input.widgetRecording) "Recording" else "Open Recorder",
                tool(EverydayWidgetTool.RECORD), if (input.widgetRecording) "Open Recorder" else "Words as you talk")
            EverydayCardKind.QUICK_NOTE -> face("Add a note", tool(EverydayWidgetTool.NOTE))
            EverydayCardKind.TRANSLATE -> face("Open Translate", tool(EverydayWidgetTool.TRANSLATE))
            EverydayCardKind.AIR_POLLEN -> {
                val source = input.sources.firstOrNull { row ->
                    row.source.kind == CollectorKind.AIR_QUALITY && row.source.selected && row.source.enabled && !row.source.muted &&
                        (config.sourceId == null || row.source.id == config.sourceId)
                }
                val saved = source?.snapshot?.takeIf { it.sourceVersion == source.source.version }
                val air = (saved?.payload as? CollectorPayload.AirQuality)?.value
                if (source == null) face("Choose a source", EverydayTarget.Sources, "Air & pollen")
                else {
                    val target = EverydayTarget.Source(source.source.id, source.source.version, saved?.contentRevision)
                    if (air == null) face("No saved air report", target, "Open Sources")
                    else {
                        val value = air.europeanAqi?.let(::europeanAqiLabel) ?: "Air quality unavailable"
                        val format = java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
                        val pollen = air.pollen.maxByOrNull { it.value }?.let { reading ->
                            text(reading.key.label) + " · " + format.format(reading.value) + " " + text("grains/m³")
                        } ?: text("No pollen reading")
                        val index = air.europeanAqi?.let { text("European AQI") + " " + format.format(it) }
                        face(value, target, "Open Sources", saved?.retrievedAt,
                            rows = listOf(EverydayCardRow(listOfNotNull(index, pollen).joinToString(" · ")))).copy(
                            attribution = air.attribution, stale = saved?.stale(input.now, true, input.power) == true,
                            lastRefreshFailed = source.attempt.state in setOf(CollectorAttemptState.FAILED, CollectorAttemptState.OFFLINE))
                    }
                }
            }
            EverydayCardKind.PLANNER_WEEK, EverydayCardKind.LOCK_NEXT_EVENT -> {
                val events = input.occurrences.filter { !it.completed && it.record.kind == OrganizerKind.EVENT &&
                    (it.end ?: it.start) > input.now && shows(config, it.start.atZone(input.zone).toLocalDate(), date, locale) }
                    .sortedBy { it.start }
                val next = events.firstOrNull()
                val time = next?.let { if (it.record.time?.allDay == true) text("All day") else clock.format(it.start) }
                val value = when {
                    next == null || time == null -> "No upcoming events"
                    next.start <= input.now -> text("In progress")
                    next.start.atZone(input.zone).toLocalDate() == date -> time
                    else -> DateTimeFormatter.ofPattern("EEE", locale).withZone(input.zone).format(next.start) + " · " + time
                }
                face(value, tool(EverydayWidgetTool.PLANNER), if (next == null) "Open Planner" else "Next event",
                    next?.record?.updatedAt?.takeUnless { kind == EverydayCardKind.LOCK_NEXT_EVENT })
            }
            EverydayCardKind.FOCUS -> {
                val focus = input.widgetFocus
                val minutes = focus?.remainingMs?.let { (it + 59_999L) / 60_000L }
                face(minutes?.let { (it.toString() + " " + text("min")) } ?: "Open Focus", tool(EverydayWidgetTool.FOCUS),
                    if (focus == null) "Ready when you are" else "Focus session")
            }
            EverydayCardKind.LITTLE_HABITS -> face(
                if (input.widgetHabits.isEmpty()) "Set up little habits" else "Little habits",
                tool(EverydayWidgetTool.ROUTINES), "Days a routine was run")
            EverydayCardKind.NEXT_RIDE -> face("Departures are off", EverydayTarget.Sources, "Live departures are off")
            EverydayCardKind.OUR_HOME -> {
                val remaining = input.widgetHomeCount - input.widgetHomeDone
                val count = java.text.NumberFormat.getIntegerInstance(locale).format(remaining)
                face(if (input.widgetHomeCount == 0) "Open Our home" else count + " · " + text("To do"),
                    tool(EverydayWidgetTool.FAMILY), if (input.widgetHomeCount == 0) "Shared list is empty" else "Private details open in Halo")
            }
            EverydayCardKind.LEARN -> {
                val learning = input.widgetLearning?.takeUnless { input.kids }
                face(if (learning == null) "Open Learn" else text("Step") + " " + (learning.steps.coerceIn(0, 4) + 1) + " / 5",
                    tool(if (input.kids) EverydayWidgetTool.KIDS else EverydayWidgetTool.STUDY),
                    if (learning == null) "No active lesson" else "Continue learning", learning?.updatedAt)
            }
            EverydayCardKind.KIDS_SKY -> face(if (input.kids) "Stars for trying" else "Open Kids space",
                tool(EverydayWidgetTool.KIDS), if (input.kids) "Open Kids space" else "Available in Kids space")
            EverydayCardKind.YOUR_THINGS -> face(input.batteryPercent?.let { "$it%" } ?: "Battery unavailable",
                EverydayTarget.Devices, if (input.charging) "This phone · Charging" else "This phone")
            EverydayCardKind.LOCK_RAIN -> {
                val source = input.sources.firstOrNull { row ->
                    row.source.kind == CollectorKind.WEATHER && row.source.selected && row.source.enabled &&
                        !row.source.muted && (!input.kids || row.source.childApproved) &&
                        (config.sourceId == null || row.source.id == config.sourceId)
                }
                val cached = source?.snapshot?.takeIf { it.sourceVersion == source.source.version }
                val forecast = (cached?.payload as? CollectorPayload.Weather)?.fact
                val chance = forecast?.let { local ->
                    val todayInPlace = input.now.atZone(java.time.ZoneId.of(local.place.zoneId)).toLocalDate()
                    local.days.firstOrNull { it.date == todayInPlace }?.precipitationProbability
                }
                val target = if (source == null || input.kids) EverydayTarget.Sources else EverydayTarget.Source(source.source.id,
                    source.source.version, cached?.contentRevision)
                val value = chance?.let { java.text.NumberFormat.getPercentInstance(locale).format(it / 100.0) }
                    ?: if (source == null) "Choose a source" else "No saved rain forecast"
                face(value, target, "Rain chance today", cached?.retrievedAt).copy(
                    stale = cached?.stale(input.now, true, input.power) == true,
                    lastRefreshFailed = source?.attempt?.state in setOf(CollectorAttemptState.FAILED, CollectorAttemptState.OFFLINE))
            }
            EverydayCardKind.SMART_STACK -> project(config.copy(kind = stackKinds(input).first()), input, locale, text)
            else -> error("Unsupported toolkit widget $kind")
        }
    }
}
