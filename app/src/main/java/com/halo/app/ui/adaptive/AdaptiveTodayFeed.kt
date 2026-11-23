package com.halo.app.ui.adaptive

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import com.halo.app.data.workspace.AdaptiveSlot
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerOccurrence
import com.halo.app.everyday.organizer.android.PhoneCalendarEvent
import com.halo.app.everyday.presentation.*
import com.halo.app.language.resolveUiText
import com.halo.app.language.uiText
import com.halo.app.screen.rememberEverydayScreenState
import com.halo.app.ui.screens.home.HaloHomeBlock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal enum class AdaptiveBlockState { LOADING, READY, EMPTY, OFFLINE, ERROR }

internal sealed interface AdaptiveBlockTarget {
    data class Owner(val target: EverydayTarget) : AdaptiveBlockTarget
    data class LibraryRoute(val route: String) : AdaptiveBlockTarget
    data class PhoneEvent(val event: PhoneCalendarEvent) : AdaptiveBlockTarget
}

internal data class AdaptiveBlockRow(val title: String, val detail: String = "", val target: AdaptiveBlockTarget? = null)
internal data class AdaptiveBlock(val heading: String, val value: String, val source: String,
    val reason: String, val state: AdaptiveBlockState, val rows: List<AdaptiveBlockRow> = emptyList(),
    val target: AdaptiveBlockTarget? = null, val observedAt: Instant? = null)

private val AdaptiveSlot.label: String
    get() = when (this) {
        AdaptiveSlot.PLANNER -> "Agenda"
        AdaptiveSlot.WEATHER -> "Weather"
        AdaptiveSlot.STUDY -> "Study"
        AdaptiveSlot.READS -> "Headlines"
        AdaptiveSlot.LIBRARY -> "Library"
        AdaptiveSlot.FOCUS -> "Focus"
        AdaptiveSlot.WIND_DOWN -> "Wind down"
        AdaptiveSlot.TASKS -> "Tasks"
        AdaptiveSlot.REMINDERS -> "Reminders"
        AdaptiveSlot.ALARMS_TIMERS -> "Alarms and timers"
        AdaptiveSlot.CLOCK -> "Clock & date"
        AdaptiveSlot.BRIEF -> "Your day in Halo"
        AdaptiveSlot.DEVICE -> "This phone"
    }

internal fun AdaptiveSlot.localizedLabel(resources: Resources): String = if (this == AdaptiveSlot.WIND_DOWN)
    resources.getString(R.string.adaptive_page_wind_down) else resolveUiText(resources, label)

private val AdaptiveSlot.reason: Int
    get() = when (this) {
        AdaptiveSlot.PLANNER -> R.string.adaptive_page_why_agenda
        AdaptiveSlot.WEATHER -> R.string.adaptive_page_why_weather
        AdaptiveSlot.STUDY -> R.string.adaptive_page_why_study
        AdaptiveSlot.READS -> R.string.adaptive_page_why_reads
        AdaptiveSlot.LIBRARY -> R.string.adaptive_page_why_library
        AdaptiveSlot.FOCUS -> R.string.adaptive_page_why_focus
        AdaptiveSlot.WIND_DOWN -> R.string.adaptive_page_why_wind_down
        AdaptiveSlot.TASKS -> R.string.adaptive_page_why_tasks
        AdaptiveSlot.REMINDERS -> R.string.adaptive_page_why_reminders
        AdaptiveSlot.ALARMS_TIMERS -> R.string.adaptive_page_why_alarms
        AdaptiveSlot.CLOCK -> R.string.adaptive_page_why_clock
        AdaptiveSlot.BRIEF -> R.string.adaptive_page_why_brief
        AdaptiveSlot.DEVICE -> R.string.adaptive_page_why_device
    }

private val projectedKinds = mapOf(
    AdaptiveSlot.WEATHER to EverydayCardKind.WEATHER, AdaptiveSlot.READS to EverydayCardKind.HEADLINES,
    AdaptiveSlot.TASKS to EverydayCardKind.TASKS, AdaptiveSlot.REMINDERS to EverydayCardKind.REMINDERS,
    AdaptiveSlot.ALARMS_TIMERS to EverydayCardKind.ALARMS_TIMERS, AdaptiveSlot.CLOCK to EverydayCardKind.CLOCK,
    AdaptiveSlot.BRIEF to EverydayCardKind.BRIEF, AdaptiveSlot.DEVICE to EverydayCardKind.DEVICE,
)

@Composable
internal fun adaptiveTodayFeed(refresh: Int, now: Instant, upcoming: List<OrganizerOccurrence>, phone: List<PhoneCalendarEvent>,
    local: Map<AdaptiveSlot, HaloHomeBlock>, weatherSourceId: String?): Map<AdaptiveSlot, AdaptiveBlock> {
    val resources = LocalResources.current
    val locale = resources.configuration.locales[0]
    val state by rememberEverydayScreenState(refresh)
    val family = Graph.kidsPreferences
    val snapshot = state.snapshot?.takeIf { !it.kids && runCatching { family.requireAdultAction(it.familyRevision) }.isSuccess }
    fun tr(value: String) = resolveUiText(resources, value)
    val blocks = mutableMapOf<AdaptiveSlot, AdaptiveBlock>()
    projectedKinds.forEach { (slot, kind) ->
        val heading = slot.localizedLabel(resources)
        val reason = resources.getString(slot.reason)
        if (snapshot == null) {
            blocks[slot] = AdaptiveBlock(heading, tr(if (state.failed) "Saved information could not be opened. Open Halo and try again." else "Loading"),
                "", reason, if (state.failed) AdaptiveBlockState.ERROR else AdaptiveBlockState.LOADING)
        } else {
            val data = if (slot == AdaptiveSlot.TASKS) snapshot.copy(now = now,
                records = snapshot.records.filter { it.kind == OrganizerKind.TASK }) else snapshot.copy(now = now)
            val config = EverydayCardConfig(kind, sourceId = weatherSourceId.takeIf { slot == AdaptiveSlot.WEATHER }, limit = 4)
            val face = EverydayCards.project(config, data, locale, text = ::tr)
            val rows = face.rows.filterNot { it.target is EverydayTarget.Create }
            val mode = when {
                face.stale || face.lastRefreshFailed -> AdaptiveBlockState.OFFLINE
                slot in setOf(AdaptiveSlot.WEATHER, AdaptiveSlot.READS, AdaptiveSlot.BRIEF) && face.observedAt == null -> AdaptiveBlockState.EMPTY
                slot in setOf(AdaptiveSlot.TASKS, AdaptiveSlot.REMINDERS, AdaptiveSlot.ALARMS_TIMERS) && rows.isEmpty() -> AdaptiveBlockState.EMPTY
                slot == AdaptiveSlot.DEVICE && data.batteryPercent == null -> AdaptiveBlockState.EMPTY
                else -> AdaptiveBlockState.READY
            }
            val target = when (slot) {
                AdaptiveSlot.TASKS, AdaptiveSlot.REMINDERS, AdaptiveSlot.ALARMS_TIMERS -> EverydayTarget.Organizer
                AdaptiveSlot.CLOCK -> EverydayTarget.Clocks
                else -> face.target
            }
            blocks[slot] = AdaptiveBlock(heading, face.value, face.attribution?.takeIf { it.isNotBlank() }
                ?.let { it + " · " + face.status } ?: face.status, reason, mode,
                rows.map { AdaptiveBlockRow(it.title, it.detail, it.target?.let { target -> AdaptiveBlockTarget.Owner(target) }) }, AdaptiveBlockTarget.Owner(target), face.observedAt)
        }
    }

    val zone = java.time.ZoneId.systemDefault()
    val timed = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale)
    val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val matched = (upcoming.map { event ->
        val value = if (event.allDay) day.format(event.start.atZone(zone)) else timed.format(event.start.atZone(zone))
        event.start to AdaptiveBlockRow(event.record.title, value,
            AdaptiveBlockTarget.Owner(EverydayTarget.Record(event.record.id, event.record.version, event.originalStart)))
    } + phone.map { event ->
        val value = if (event.allDay) day.format(event.start.atZone(ZoneOffset.UTC)) else timed.format(event.start.atZone(zone))
        event.start to AdaptiveBlockRow(event.title, value, AdaptiveBlockTarget.PhoneEvent(event))
    }).sortedBy { it.first }
    blocks[AdaptiveSlot.PLANNER] = AdaptiveBlock(AdaptiveSlot.PLANNER.localizedLabel(resources),
        if (matched.isEmpty()) stringResource(R.string.adaptive_page_empty_events) else stringResource(R.string.adaptive_page_upcoming_events),
        uiText("On this phone"), stringResource(AdaptiveSlot.PLANNER.reason),
        if (matched.isEmpty()) AdaptiveBlockState.EMPTY else AdaptiveBlockState.READY,
        matched.take(4).map { it.second }, AdaptiveBlockTarget.Owner(EverydayTarget.Organizer))

    for (slot in listOf(AdaptiveSlot.STUDY, AdaptiveSlot.LIBRARY, AdaptiveSlot.FOCUS, AdaptiveSlot.WIND_DOWN)) {
        val item = local[slot]
        blocks[slot] = AdaptiveBlock(slot.localizedLabel(resources), item?.title ?: when (slot) {
            AdaptiveSlot.STUDY -> stringResource(R.string.adaptive_page_empty_study)
            AdaptiveSlot.LIBRARY -> stringResource(R.string.adaptive_page_empty_library)
            AdaptiveSlot.FOCUS -> stringResource(R.string.adaptive_page_empty_focus)
            else -> stringResource(R.string.adaptive_page_quiet_off)
        }, item?.source.orEmpty(), item?.let { it.explanation ?: stringResource(it.why) } ?: stringResource(slot.reason),
            if (item == null) AdaptiveBlockState.EMPTY else AdaptiveBlockState.READY,
            item?.supporting?.let { listOf(AdaptiveBlockRow(it)) }.orEmpty(),
            AdaptiveBlockTarget.LibraryRoute(item?.route ?: when (slot) {
                AdaptiveSlot.STUDY -> "study"
                AdaptiveSlot.LIBRARY -> "notebook"
                AdaptiveSlot.FOCUS -> "focus"
                else -> "winddown"
            }))
    }
    return blocks
}
