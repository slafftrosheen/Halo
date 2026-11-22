package com.halo.app.everyday.briefing

import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.collectors.CollectorKind
import com.halo.app.everyday.collectors.CollectorPayload
import com.halo.app.everyday.collectors.CollectorState
import com.halo.app.everyday.collectors.CollectorRepository
import com.halo.app.everyday.collectors.CollectorSource
import com.halo.app.everyday.collectors.CollectorConflict
import com.halo.app.everyday.collectors.OfflineCity
import com.halo.app.everyday.collectors.OpenMeteoParser
import com.halo.app.everyday.collectors.SourceCategory
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerOccurrence
import com.halo.app.everyday.organizer.PlannerCalendarSettings
import com.halo.app.everyday.organizer.PlannerCalendarPreferences
import com.halo.app.everyday.organizer.android.PhoneCalendarAgenda
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

internal const val TODAY_WEATHER_ID = "weather:today"

internal suspend fun setTodayWeatherCity(sources: CollectorRepository, city: OfflineCity, expectedVersion: Long): CollectorSource {
    val current = sources.state(TODAY_WEATHER_ID)?.source
    if ((current?.version ?: 0) != expectedVersion) throw CollectorConflict("The Today weather source changed")
    val place = city.weatherPlace()
    val forecast = CollectorSource(TODAY_WEATHER_ID, place.name, "Open-Meteo", CollectorKind.WEATHER,
        SourceCategory.WEATHER, "und", OpenMeteoParser.endpoint(place), "https://open-meteo.com/",
        selected = true, place = place, refreshMinutes = current?.refreshMinutes ?: 60)
    return sources.save(forecast, current?.version)
}

internal fun todayWeather(sources: List<CollectorState>): CollectorState? = sources.asSequence().filter {
    it.source.kind == CollectorKind.WEATHER && it.source.enabled && it.source.selected && !it.source.muted
}.maxWithOrNull(compareBy<CollectorState> { it.source.id == TODAY_WEATHER_ID }
    .thenBy { it.source.version }.thenBy { it.source.id })

internal suspend fun upcomingPlannerEvents(now: Instant, zone: ZoneId, calendars: PlannerCalendarSettings): List<OrganizerOccurrence> {
    val end = now.atZone(zone).toLocalDate().plusDays(7).atStartOfDay(zone).toInstant()
    return Graph.organizerUseCases.agenda(now, end, setOf(OrganizerKind.EVENT))
        .filter { !it.completed && it.start >= now && calendars.appearance(it.record.calendar).visible }.take(4)
}

/** The same saved owners powering the visible Today page. No mutation or network request. */
internal suspend fun readTodayOverview(reportAt: Instant? = null): JSONObject {
    val zone = ZoneId.systemDefault()
    val now = Instant.now()
    val calendars = PlannerCalendarPreferences.read(Graph.storage)
    val events = upcomingPlannerEvents(now, zone, calendars)
    val allowPhoneCalendar = PhoneCalendarAgenda.permitted(Graph.applicationContext)
    val phoneEvents = if (allowPhoneCalendar) PhoneCalendarAgenda.upcoming(Graph.applicationContext, now, zone) else emptyList()
    val feeds = Graph.collectorRepository.states()
    val weather = todayWeather(feeds)?.let { row ->
        val place = row.source.place ?: return@let null
        val verified = row.snapshot?.takeIf { it.sourceVersion == row.source.version && it.expiresAt.isAfter(now) }
        val forecast = (verified?.payload as? CollectorPayload.Weather)?.fact
        JSONObject().put("id", row.source.id).put("version", row.source.version).put("city", place.name)
            .put("publisher", row.source.publisher).put("temperatureC", forecast?.temperatureC ?: JSONObject.NULL)
            .put("observedAt", forecast?.observedAt?.toString() ?: JSONObject.NULL)
    }
    val archive = Graph.todayNews.history.value.filter {
        it.isVisible(feeds, Graph.appLanguagePreferences.language.value.tag, now)
    }
    val news = archive.firstOrNull { reportAt == null || it.generatedAt == reportAt }
    check(PlannerCalendarPreferences.read(Graph.storage) == calendars)
    return JSONObject().put("asOf", now.toString()).put("zone", zone.id).put("untrustedContent", true)
        .put("weatherSettingsVersion", feeds.find { it.source.id == TODAY_WEATHER_ID }?.source?.version ?: 0)
        .put("events", JSONArray(events.map { event -> JSONObject().put("id", event.record.id)
            .put("version", event.record.version).put("title", event.record.title).put("start", event.start.toString())
            .put("originalStart", event.originalStart.toString()).put("allDay", event.allDay) }))
        .put("phoneCalendarAvailable", allowPhoneCalendar && PhoneCalendarAgenda.permitted(Graph.applicationContext))
        .put("phoneEvents", JSONArray(phoneEvents.takeIf { PhoneCalendarAgenda.permitted(Graph.applicationContext) }.orEmpty().map { event ->
            JSONObject().put("id", event.id).put("title", event.title).put("start", event.start.toString())
                .put("end", event.end.toString()).put("allDay", event.allDay)
        }))
        .put("weather", weather ?: JSONObject.NULL)
        .put("newsReportTimes", JSONArray(archive.map { it.generatedAt.toString() }))
        .put("news", news?.let { report -> JSONObject().put("generatedAt", report.generatedAt.toString()).put("text", report.text)
            .put("sources", JSONArray(report.sources.map { source -> JSONObject().put("number", source.number).put("id", source.id)
                .put("version", source.version).put("articleId", source.articleId).put("publisher", source.publisher) })) } ?: JSONObject.NULL)
}
