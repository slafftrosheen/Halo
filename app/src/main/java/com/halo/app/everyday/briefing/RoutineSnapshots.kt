package com.halo.app.everyday.briefing

import com.halo.app.automation.RoutineSnapshotSection
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.collectors.CollectorState
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.PlannerCalendarPreferences
import com.halo.app.language.resolveUiText
import java.time.Instant
import java.time.ZoneId

/** Shares Planner and collector owners with Today. Scheduled reads never initiate a refresh. */
internal suspend fun readRoutineSnapshot(section: RoutineSnapshotSection): String? {
    val authority = Graph.kidsPreferences.adultActionRevision()
    Graph.haloPause.requireActive()
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val context = Graph.applicationContext
    val result = when (section) {
        RoutineSnapshotSection.TODAY, RoutineSnapshotSection.TOMORROW -> {
            val today = section == RoutineSnapshotSection.TODAY
            val date = now.atZone(zone).toLocalDate().plusDays(if (today) 0 else 1)
            val from = if (today) now else date.atStartOfDay(zone).toInstant()
            val until = date.plusDays(1).atStartOfDay(zone).toInstant()
            val calendars = PlannerCalendarPreferences.read(Graph.storage)
            val rows = Graph.organizerUseCases.agenda(from, until, setOf(OrganizerKind.EVENT, OrganizerKind.TASK))
                .filter { !it.completed && it.start < until && calendars.appearance(it.record.calendar).visible }
            check(PlannerCalendarPreferences.read(Graph.storage) == calendars)
            resolveUiText(context.resources, if (today) "Today in Planner: %1$d events and %2$d tasks remaining."
                else "Tomorrow in Planner: %1$d events and %2$d tasks.",
                rows.count { it.record.kind == OrganizerKind.EVENT }, rows.count { it.record.kind == OrganizerKind.TASK })
        }
        RoutineSnapshotSection.WEATHER -> {
            val row = todayWeather(Graph.collectorRepository.states()) ?: return null
            val forecast = (row.currentPayload(now) as? CollectorPayload.Weather)?.fact ?: return null
            val temperature = forecast.temperatureC?.takeIf(Double::isFinite) ?: return null
            val city = row.source.place?.name ?: return null
            check(row.isStillCurrent())
            resolveUiText(context.resources, "Saved weather for %1$s: %2$s °C · %3$s",
                city.take(90), java.text.NumberFormat.getNumberInstance(context.resources.configuration.locales[0])
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
            check(row.isStillCurrent())
            resolveUiText(context.resources, "%1$s · %2$s", item.publisher.ifBlank { row.source.publisher }.take(60),
                item.title.replace(Regex("\\s+"), " ").take(150))
        }
    }
    Graph.kidsPreferences.requireAdultAction(authority)
    Graph.haloPause.requireActive()
    return result
}

private fun CollectorState.currentPayload(now: Instant) =
    snapshot?.takeIf { it.sourceVersion == source.version && it.expiresAt.isAfter(now) }?.payload

private suspend fun CollectorState.isStillCurrent(): Boolean {
    val current = Graph.collectorRepository.state(source.id) ?: return false
    return current.source == source && current.snapshot?.contentRevision == snapshot?.contentRevision &&
        current.currentPayload(Instant.now()) != null
}
