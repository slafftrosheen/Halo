package com.halo.app.everyday.briefing

import android.content.res.Configuration
import android.content.res.Resources
import android.os.BatteryManager
import com.halo.app.R
import com.halo.app.automation.RoutineSnapshotSection
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.collectors.CollectorState
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerStatus
import com.halo.app.everyday.organizer.PlannerCalendarPreferences
import com.halo.app.language.resolveUiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Shares Planner and collector owners with Today. Scheduled reads never initiate a refresh. */
internal suspend fun readRoutineSnapshot(section: RoutineSnapshotSection): String? {
    val authority = Graph.kidsPreferences.adultActionRevision()
    Graph.haloPause.requireActive()
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val resources = routineResources()
    val result = readSection(section, now, zone, resources)
    Graph.kidsPreferences.requireAdultAction(authority)
    Graph.haloPause.requireActive()
    return result
}

private suspend fun readSection(section: RoutineSnapshotSection, now: Instant, zone: ZoneId, resources: Resources): String? {
    val locale = resources.configuration.locales[0]
    return when (section) {
        RoutineSnapshotSection.TODAY, RoutineSnapshotSection.TOMORROW -> {
            val today = section == RoutineSnapshotSection.TODAY
            val date = now.atZone(zone).toLocalDate().plusDays(if (today) 0 else 1)
            val from = if (today) now else date.atStartOfDay(zone).toInstant()
            val until = date.plusDays(1).atStartOfDay(zone).toInstant()
            val calendars = PlannerCalendarPreferences.read(Graph.storage)
            val rows = Graph.organizerUseCases.agenda(from, until, setOf(OrganizerKind.EVENT, OrganizerKind.TASK))
                .filter { !it.completed && it.start < until && calendars.appearance(it.record.calendar).visible }
            check(PlannerCalendarPreferences.read(Graph.storage) == calendars)
            resolveUiText(resources, if (today) "Today in Planner: %1\$d events and %2\$d tasks remaining."
                else "Tomorrow in Planner: %1\$d events and %2\$d tasks.",
                rows.count { it.record.kind == OrganizerKind.EVENT }, rows.count { it.record.kind == OrganizerKind.TASK })
        }
        RoutineSnapshotSection.WEATHER -> {
            val row = todayWeather(Graph.collectorRepository.states()) ?: return null
            val forecast = (row.currentPayload(now) as? CollectorPayload.Weather)?.fact ?: return null
            val temperature = forecast.temperatureC?.takeIf(Double::isFinite) ?: return null
            val city = row.source.place?.name ?: return null
            if (!row.isStillCurrent()) return null
            resolveUiText(resources, "Saved weather for %1\$s: %2\$s °C · %3\$s",
                city.take(90), java.text.NumberFormat.getNumberInstance(resources.configuration.locales[0])
                    .apply { maximumFractionDigits = 1 }.format(temperature), row.source.publisher.take(50))
        }
        RoutineSnapshotSection.HEADLINE -> {
            val latest = Graph.collectorRepository.states().asSequence()
                .filter { it.source.kind == CollectorKind.FEED && it.source.selected && it.source.enabled && !it.source.muted }
                .flatMap { row -> (row.currentPayload(now) as? CollectorPayload.Articles)?.items.orEmpty()
                    .asSequence().filter { it.title.isNotBlank() }.map { row to it } }
                .maxByOrNull { (row, item) -> item.publishedAt ?: row.snapshot?.retrievedAt ?: Instant.MIN }
                ?: return null
            val (row, item) = latest
            if (!row.isStillCurrent()) return null
            resolveUiText(resources, "%1\$s · %2\$s", item.publisher.ifBlank { row.source.publisher }.take(60),
                item.title.replace(Regex("\\s+"), " ").take(150))
        }
        RoutineSnapshotSection.AGENDA -> {
            val end = now.atZone(zone).toLocalDate().plusDays(7).atStartOfDay(zone).toInstant()
            val calendars = PlannerCalendarPreferences.read(Graph.storage)
            val count = Graph.organizerUseCases.agenda(now, end, setOf(OrganizerKind.EVENT)).count {
                !it.completed && it.start >= now && it.start < end && calendars.appearance(it.record.calendar).visible
            }
            check(PlannerCalendarPreferences.read(Graph.storage) == calendars)
            resources.getString(R.string.adaptive_summary_agenda_value, count)
        }
        RoutineSnapshotSection.TASKS, RoutineSnapshotSection.REMINDERS, RoutineSnapshotSection.ALARMS_TIMERS -> {
            val (kinds, message) = when (section) {
                RoutineSnapshotSection.TASKS -> setOf(OrganizerKind.TASK) to R.string.adaptive_summary_tasks_value
                RoutineSnapshotSection.REMINDERS -> setOf(OrganizerKind.REMINDER) to R.string.adaptive_summary_reminders_value
                else -> setOf(OrganizerKind.ALARM, OrganizerKind.TIMER) to R.string.adaptive_summary_alarms_value
            }
            resources.getString(message, Graph.organizerRepository.records().count {
                it.kind in kinds && it.status == OrganizerStatus.ACTIVE && it.deletedAt == null
            })
        }
        RoutineSnapshotSection.CLOCK -> resources.getString(R.string.adaptive_summary_clock_value,
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale).withZone(zone).format(now))
        RoutineSnapshotSection.BRIEF -> {
            val date = now.atZone(zone).toLocalDate()
            val brief = Graph.briefRepository.recent(60).firstOrNull {
                it.date == date && it.zoneId == zone.id && it.audience == BriefAudience.EVERYDAY
            }?.let { Graph.briefService.visible(it.id) } ?: return null
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(brief.generatedAt)
            resources.getString(R.string.adaptive_summary_brief_value, time, brief.facts.size)
        }
        RoutineSnapshotSection.DEVICE -> {
            val battery = Graph.applicationContext.getSystemService(BatteryManager::class.java) ?: return null
            val charge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: return null
            resources.getString(if (battery.isCharging) R.string.adaptive_summary_device_charging
                else R.string.adaptive_summary_device_value, charge)
        }
    }
}

private fun routineResources(): Resources {
    val context = Graph.applicationContext
    val locale = Locale.forLanguageTag(Graph.appLanguagePreferences.language.value.tag)
    return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) }).resources
}

internal fun routineSnapshotLabel(resources: Resources, section: RoutineSnapshotSection): String = when (section) {
    RoutineSnapshotSection.AGENDA -> resources.getString(R.string.adaptive_summary_agenda)
    RoutineSnapshotSection.TASKS -> resources.getString(R.string.adaptive_summary_tasks)
    RoutineSnapshotSection.REMINDERS -> resources.getString(R.string.adaptive_summary_reminders)
    RoutineSnapshotSection.ALARMS_TIMERS -> resources.getString(R.string.adaptive_summary_alarms)
    RoutineSnapshotSection.CLOCK -> resources.getString(R.string.adaptive_summary_clock)
    RoutineSnapshotSection.BRIEF -> resources.getString(R.string.adaptive_summary_brief)
    RoutineSnapshotSection.DEVICE -> resources.getString(R.string.adaptive_summary_device)
    else -> resolveUiText(resources, section.title)
}

internal fun routineSnapshotUnavailable(section: RoutineSnapshotSection): String {
    val resources = routineResources()
    return resources.getString(R.string.adaptive_summary_unavailable, routineSnapshotLabel(resources, section))
}

private fun CollectorState.currentPayload(now: Instant) =
    snapshot?.takeIf { it.sourceVersion == source.version && it.expiresAt.isAfter(now) }?.payload

private suspend fun CollectorState.isStillCurrent(): Boolean {
    val current = Graph.collectorRepository.state(source.id) ?: return false
    return current.source == source && current.snapshot?.contentRevision == snapshot?.contentRevision &&
        current.currentPayload(Instant.now()) != null
}
