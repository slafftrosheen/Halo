package com.halo.app.everyday.presentation

import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class EverydayCardKind { CLOCK, CLOCK_WEATHER, UPCOMING, AGENDA, TASKS, REMINDERS, ALARMS_TIMERS, WEATHER, HEADLINES, BRIEF, DEVICE, QUICK_ACTIONS, DASHBOARD }

/** Presentation configuration only. No source bodies, executable routes, permissions or model prompts. */
data class EverydayCardConfig(val kind: EverydayCardKind, val zoneId: String? = null, val cityLabel: String = "",
    val sourceId: String? = null, val recordId: String? = null, val priority: TaskPriority? = null,
    val limit: Int = 3, val hidePrivate: Boolean = true, val theme: String = "INHERIT",
    val sections: List<EverydayCardKind> = listOf(EverydayCardKind.TASKS,EverydayCardKind.UPCOMING,EverydayCardKind.WEATHER),val headlineSourceId: String? = null) {
    init {
        zoneId?.let(ZoneId::of)
        require(cityLabel.length <= 120 && limit in 1..5 && theme in setOf("INHERIT", "LIGHT", "DARK"))
        require(sourceId == null || sourceId.matches(Regex("[A-Za-z0-9_.:-]{1,160}")))
        require(headlineSourceId == null || headlineSourceId.matches(Regex("[A-Za-z0-9_.:-]{1,160}")))
        require(recordId == null || recordId.matches(Regex("[A-Za-z0-9_.:-]{1,200}")))
        require(sections.size in 1..5 && sections.distinct().size==sections.size && sections.all { it in setOf(EverydayCardKind.TASKS,EverydayCardKind.UPCOMING,
            EverydayCardKind.WEATHER,EverydayCardKind.BRIEF,EverydayCardKind.HEADLINES,EverydayCardKind.DEVICE,EverydayCardKind.ALARMS_TIMERS) })
    }
    fun fields(): Map<String, String> = buildMap {
        put("kind", kind.name); zoneId?.let { put("zoneId", it) }; put("cityLabel", cityLabel)
        sourceId?.let { put("sourceId", it) }; recordId?.let { put("recordId", it) }; priority?.let { put("priority", it.name) }
        put("limit", limit.toString()); put("hidePrivate", hidePrivate.toString()); put("theme", theme)
        put("sections",sections.joinToString(",") { it.name })
        headlineSourceId?.let { put("headlineSourceId",it) }
    }
    companion object {
        fun from(fields: Map<String, String>, fallback: EverydayCardKind): EverydayCardConfig {
            require(fields.keys.all { it in setOf("kind", "zoneId", "cityLabel", "sourceId", "recordId", "priority", "limit", "hidePrivate", "theme", "sections", "headlineSourceId") })
            return EverydayCardConfig(fields["kind"]?.let(EverydayCardKind::valueOf) ?: fallback,
                fields["zoneId"], fields["cityLabel"].orEmpty(), fields["sourceId"], fields["recordId"],
                fields["priority"]?.let(TaskPriority::valueOf), fields["limit"]?.toInt() ?: 3,
                fields["hidePrivate"]?.toBooleanStrict() ?: true, fields["theme"] ?: "INHERIT",
                fields["sections"]?.split(",")?.map(EverydayCardKind::valueOf) ?: listOf(EverydayCardKind.TASKS,EverydayCardKind.UPCOMING,EverydayCardKind.WEATHER),fields["headlineSourceId"])
        }
    }
}

sealed interface EverydayTarget {
    data object Organizer : EverydayTarget
    data object Sources : EverydayTarget
    data object Clocks : EverydayTarget
    data object Devices : EverydayTarget
    data object BriefLatest : EverydayTarget
    data class Record(val id: String, val version: Long, val originalStart: Instant? = null) : EverydayTarget
    data class Clock(val zoneId: String, val label: String) : EverydayTarget
    data class Source(val id: String, val version: Long, val revision: String?, val articleId: String? = null) : EverydayTarget
    data class Brief(val id: String) : EverydayTarget
    data class Create(val kind: OrganizerKind) : EverydayTarget
}

data class EverydayCardRow(val title: String, val detail: String = "", val target: EverydayTarget? = null, val personal: Boolean = false,
    val publisher: String? = null,val savedAt: Instant? = null,val stale: Boolean = false)
data class EverydayCardFace(val title: String, val value: String, val rows: List<EverydayCardRow>, val status: String,
    val target: EverydayTarget, val personal: Boolean = false, val observedAt: Instant? = null, val attribution: String? = null,
    val stale: Boolean = false,val lastRefreshFailed: Boolean = false,val oldestSelected: Boolean = false)

data class EverydaySnapshot(val now: Instant, val zone: ZoneId, val records: List<OrganizerRecord>,
    val occurrences: List<OrganizerOccurrence>, val sources: List<CollectorState>, val briefs: List<EverydayBrief>,
    val clocks: List<WorldClockRecord> = emptyList(), val receipts: List<DeliveryReceipt> = emptyList(),
    val kids: Boolean = false, val sharedRecordIds: Set<String> = emptySet(), val power: Boolean = false,
    val batteryPercent: Int? = null, val charging: Boolean = false, val authorityStamp: String = "", val familyRevision: Long = 0, val modeRevision: Long = 0)

/** Shared deterministic read-only faces for Screen and launcher hosts. This never fetches or runs a model. */
object EverydayCards {
    val screenKinds = linkedMapOf("clock" to EverydayCardKind.CLOCK, "clock_weather" to EverydayCardKind.CLOCK_WEATHER,
        "upcoming" to EverydayCardKind.UPCOMING, "agenda" to EverydayCardKind.AGENDA, "organizer_tasks" to EverydayCardKind.TASKS,
        "reminders" to EverydayCardKind.REMINDERS, "alarms_timers" to EverydayCardKind.ALARMS_TIMERS,
        "weather" to EverydayCardKind.WEATHER, "headlines" to EverydayCardKind.HEADLINES, "briefing" to EverydayCardKind.BRIEF,
        "everyday_dashboard" to EverydayCardKind.DASHBOARD, "quick_actions" to EverydayCardKind.QUICK_ACTIONS)
    val launcherKinds = listOf(EverydayCardKind.CLOCK, EverydayCardKind.CLOCK_WEATHER, EverydayCardKind.UPCOMING,
        EverydayCardKind.TASKS, EverydayCardKind.BRIEF, EverydayCardKind.HEADLINES, EverydayCardKind.ALARMS_TIMERS, EverydayCardKind.DASHBOARD)
    fun title(kind: EverydayCardKind): String = when (kind) {
        EverydayCardKind.CLOCK -> "Clock & date"; EverydayCardKind.CLOCK_WEATHER -> "Clock and weather"
        EverydayCardKind.UPCOMING -> "Upcoming event"; EverydayCardKind.AGENDA -> "Agenda"; EverydayCardKind.TASKS -> "Tasks and reminders"
        EverydayCardKind.REMINDERS -> "Reminders"; EverydayCardKind.ALARMS_TIMERS -> "Alarms and timers"; EverydayCardKind.WEATHER -> "Weather"
        EverydayCardKind.HEADLINES -> "Headlines"; EverydayCardKind.BRIEF -> "Your day in Halo"; EverydayCardKind.DEVICE -> "This phone"
        EverydayCardKind.QUICK_ACTIONS -> "Quick actions"; EverydayCardKind.DASHBOARD -> "Everyday dashboard"
    }

    fun project(config: EverydayCardConfig, input: EverydaySnapshot, locale: Locale, externalHost: Boolean = false,
        locked: Boolean = false, text: (String) -> String = { it }): EverydayCardFace {
        val zone = config.zoneId?.let(ZoneId::of) ?: input.zone
        fun stamp(at: Instant) = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(at)
        val records = input.records.filter { it.deletedAt == null && (!input.kids || it.id in input.sharedRecordIds) }
        val ids = records.map { it.id }.toSet()
        val occurrences = input.occurrences.filter { it.record.id in ids && !it.completed && (it.end ?: it.start.plusSeconds(1)) > input.now }
        val mode = text(if (input.power) "Power" else "Local")
        // Hosts may cache a RemoteViews across a lock/profile change. Private text never leaves Halo.
        val privateHidden = locked || externalHost
        val heading = text(title(config.kind))
        fun sourceStatus(source: CollectorState, snapshot: CollectorSnapshot): String = mode+" · "+
            text(if(snapshot.stale(input.now,true,input.power)) "Cached · may be out of date" else "Saved snapshot is current")+
            when(source.attempt.state) {
                CollectorAttemptState.FAILED -> " · "+text("Last refresh failed")
                CollectorAttemptState.OFFLINE -> " · "+text("Offline")
                CollectorAttemptState.BACKOFF,CollectorAttemptState.QUOTA -> " · "+text("Waiting before the next refresh")
                CollectorAttemptState.CANCELLED -> " · "+text("Refresh cancelled")
                else -> ""
            }
        fun face(value: String, rows: List<EverydayCardRow>, target: EverydayTarget, personal: Boolean = false,
            status: String = text("On this phone"), at: Instant? = null, attribution: String? = null) = EverydayCardFace(heading, value,
                rows.take(config.limit).map { if (it.personal && privateHidden) it.copy(title = text("Private item"), detail = "") else it },
                status, target, personal, at, attribution)
        fun selectedOccurrence(record: OrganizerRecord)=input.occurrences.filter { it.record.id==record.id && !it.completed }.minByOrNull { it.start }
        fun recordRows(rows: List<OrganizerRecord>) = rows.map { r ->
            val selected=if(r.repeat != null) selectedOccurrence(r) else null
            EverydayCardRow(selected?.record?.title ?: r.title,
                (selected?.start ?: r.time?.takeIf { r.repeat==null }?.instant(input.zone))?.let(::stamp).orEmpty(),
                EverydayTarget.Record(r.id,r.version,selected?.originalStart),true)
        }
        fun weather(): EverydayCardFace {
            val source = input.sources.firstOrNull { s -> (config.sourceId == null || s.source.id == config.sourceId) &&
                s.source.kind == CollectorKind.WEATHER && s.source.selected && s.source.enabled && !s.source.muted &&
                (!input.kids || s.source.childApproved) }
            val snapshot = source?.snapshot?.takeIf { it.sourceVersion == source.source.version }
            val fact = (snapshot?.payload as? CollectorPayload.Weather)?.fact
            if (fact == null) {
                if(source==null) return face(text("Choose a saved weather source"),emptyList(),EverydayTarget.Sources,status=text("Needs setup"))
                val attempt=source.attempt
                val state=when(attempt.state) {
                    CollectorAttemptState.FETCHING -> "Refresh in progress"
                    CollectorAttemptState.FAILED -> "Last refresh failed"
                    CollectorAttemptState.OFFLINE -> "Offline"
                    CollectorAttemptState.BACKOFF,CollectorAttemptState.QUOTA -> "Waiting before the next refresh"
                    CollectorAttemptState.CANCELLED -> "Refresh cancelled"
                    else -> "No saved weather yet"
                }
                return face(text("No saved weather yet"),listOf(EverydayCardRow(source.source.title)),
                    EverydayTarget.Source(source.source.id,source.source.version,null),status=listOfNotNull(mode,text(state),
                        attempt.at?.let { text("Last attempt")+" · "+stamp(it) },
                        attempt.nextAllowedAt?.takeIf { it>input.now }?.let { text("Next refresh eligible")+" · "+stamp(it) }).joinToString(" · "))
                    .copy(lastRefreshFailed=attempt.state in setOf(CollectorAttemptState.FAILED,CollectorAttemptState.OFFLINE))
            }
            val temperature = fact.temperatureC?.let { java.text.NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(it) + " °C" }
                ?: text("Temperature unavailable")
            return face(temperature, listOf(EverydayCardRow(fact.place.name, fact.place.zoneId)),
                EverydayTarget.Source(source.source.id, source.source.version, snapshot.contentRevision), status = sourceStatus(source,snapshot),
                at = snapshot.retrievedAt, attribution = fact.attribution).copy(stale=snapshot.stale(input.now,true,input.power),
                    lastRefreshFailed=source.attempt.state in setOf(CollectorAttemptState.FAILED,CollectorAttemptState.OFFLINE))
        }
        return when (config.kind) {
            EverydayCardKind.CLOCK -> face(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(input.now.atZone(zone)),
                listOf(EverydayCardRow(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(input.now.atZone(zone)),
                    config.cityLabel.ifBlank { if(config.zoneId==null) text("Phone time zone") else zone.id })),
                if(config.zoneId==null) EverydayTarget.Clocks else EverydayTarget.Clock(zone.id,config.cityLabel))
            EverydayCardKind.WEATHER -> weather()
            EverydayCardKind.CLOCK_WEATHER -> {
                val weather = weather()
                val clock = project(config.copy(kind = EverydayCardKind.CLOCK), input, locale, externalHost, locked, text)
                clock.copy(title = heading, rows = (clock.rows + EverydayCardRow(weather.value, weather.rows.firstOrNull()?.title.orEmpty(), weather.target)).take(2),
                    target=if(weather.observedAt != null) weather.target else clock.target,status = weather.status, attribution = weather.attribution, observedAt = weather.observedAt,
                    stale=weather.stale,lastRefreshFailed=weather.lastRefreshFailed)
            }
            EverydayCardKind.UPCOMING, EverydayCardKind.AGENDA -> {
                val upcoming = occurrences.filter { it.record.kind == OrganizerKind.EVENT && (config.recordId == null || config.recordId == it.record.id) }.sortedBy { it.start }
                val rows = upcoming.map { EverydayCardRow(it.record.title, stamp(it.start), EverydayTarget.Record(it.record.id, it.record.version,it.originalStart), true) }
                face(if (upcoming.isEmpty()) text("No upcoming events in the next seven days") else "${upcoming.size} · ${text("Events")}", rows,
                    rows.firstOrNull()?.target ?: EverydayTarget.Organizer, true,status=text("Next seven days"))
            }
            EverydayCardKind.TASKS, EverydayCardKind.REMINDERS -> {
                val kinds = if (config.kind == EverydayCardKind.REMINDERS) setOf(OrganizerKind.REMINDER) else setOf(OrganizerKind.TASK, OrganizerKind.REMINDER)
                val rows = records.filter { it.kind in kinds && it.status == OrganizerStatus.ACTIVE && (config.priority == null || it.priority == config.priority) &&
                    (config.recordId == null || it.id == config.recordId) }.sortedWith(compareByDescending<OrganizerRecord> { it.priority.ordinal }.thenBy { it.time?.instant(input.zone) ?: Instant.MAX }.thenBy { it.id })
                val today=input.now.atZone(input.zone).toLocalDate()
                // Count eligible occurrences, not only the oldest unfinished date of each series.
                val deadlines=rows.flatMap { record -> if(record.repeat==null) listOfNotNull(record.time?.instant(input.zone))
                    else input.occurrences.filter { it.record.id==record.id && !it.completed }.distinctBy { it.originalStart }.map { it.start } }
                val due=deadlines.count { it.atZone(input.zone).toLocalDate()==today }
                val cutoff=today.minusDays(30).atStartOfDay(input.zone).toInstant()
                val overdue=deadlines.count { it < input.now && it>=cutoff }
                face(if (rows.isEmpty()) text("No matching active items") else "${rows.size} · ${text("Active items")}", recordRows(rows), EverydayTarget.Organizer, true,
                    status="${text("Due today")}: $due · ${text("Overdue in the last 30 days")}: $overdue")
            }
            EverydayCardKind.ALARMS_TIMERS -> {
                val rows = records.filter { it.kind in setOf(OrganizerKind.ALARM, OrganizerKind.TIMER) && it.status == OrganizerStatus.ACTIVE && (config.recordId == null || config.recordId == it.id) }
                val display = rows.map { record ->
                    val receipt = input.receipts.filter { it.recordId == record.id && it.recordVersion == record.version }.maxByOrNull { it.updatedAt }
                    EverydayCardRow(if(externalHost) text(if(record.kind==OrganizerKind.TIMER) "Timer controls" else "Alarm controls") else record.title, text(receipt?.state?.let { when (it) {
                        DeliveryState.EXACT -> "Exact alarm scheduled"; DeliveryState.APPROXIMATE -> "Approximate scheduling"
                        DeliveryState.NOTIFICATIONS_BLOCKED -> "Notifications blocked"; DeliveryState.FIRED -> "Alert delivered"
                        DeliveryState.DISMISSED -> "Dismissed"; DeliveryState.MISSED -> "Missed"; DeliveryState.FAILED -> "Scheduling failed"
                        DeliveryState.CANCELLED -> "Cancelled"; else -> "Scheduling pending"
                    } } ?: if (record.timer?.startedAt == null && record.timer != null) "Timer paused" else "Scheduling pending"), EverydayTarget.Record(record.id, record.version), !externalHost)
                }
                val controls=if(rows.isEmpty()) listOf(EverydayCardRow(text("Set alarm"),target=EverydayTarget.Create(OrganizerKind.ALARM)),
                    EverydayCardRow(text("Start timer"),target=EverydayTarget.Create(OrganizerKind.TIMER))) else display
                face(if (rows.isEmpty()) text("No active alarms or timers") else "${rows.size} · ${text("Active items")}", controls,
                    controls.first().target!!, true)
            }
            EverydayCardKind.HEADLINES -> {
                val sources = input.sources.filter { s -> (config.sourceId == null || config.sourceId == s.source.id) && s.source.kind == CollectorKind.FEED &&
                    s.source.selected && s.source.enabled && !s.source.muted && (!input.kids || s.source.childApproved) }
                val articles = sources.flatMap { s -> (s.snapshot?.takeIf { it.sourceVersion == s.source.version }?.payload as? CollectorPayload.Articles)?.items.orEmpty().map { s to it } }
                    .sortedByDescending { it.second.publishedAt ?: Instant.MIN }
                val displayedSources=articles.take(config.limit).map { it.first }.distinctBy { it.source.id }
                val oldest=displayedSources.mapNotNull { it.snapshot?.retrievedAt }.minOrNull()
                val anyStale=displayedSources.any { it.snapshot?.stale(input.now,true,input.power)!=false }
                val anyFailed=displayedSources.any { it.attempt.state in setOf(CollectorAttemptState.FAILED,CollectorAttemptState.OFFLINE) }
                face(if (articles.isEmpty()) text("No saved headlines from selected sources") else text("Selected headlines"), articles.map { (s, a) ->
                    EverydayCardRow(a.title, listOfNotNull(a.publisher,s.snapshot?.let { sourceStatus(s,it) },s.snapshot?.retrievedAt?.let { text("Retrieved")+" · "+stamp(it) }).joinToString(" · "),
                        EverydayTarget.Source(s.source.id, s.source.version, s.snapshot?.contentRevision, a.id),publisher=a.publisher,
                        savedAt=s.snapshot?.retrievedAt,stale=s.snapshot?.stale(input.now,true,input.power)!=false)
                }, articles.firstOrNull()?.let { (s,a) -> EverydayTarget.Source(s.source.id, s.source.version, s.snapshot?.contentRevision, a.id) } ?: EverydayTarget.Sources,
                    status = mode+" · "+text(if(displayedSources.isEmpty()) "No saved snapshot" else if(anyStale) "Some saved headlines may be out of date" else "Saved snapshots are current")+
                        (if(anyFailed) " · "+text("Last refresh failed") else "")+(if(oldest != null) " · "+text("Oldest selected snapshot") else ""),at=oldest)
                    .copy(stale=anyStale,lastRefreshFailed=anyFailed,oldestSelected=oldest!=null)
            }
            EverydayCardKind.BRIEF -> {
                val brief = input.briefs.firstOrNull { (!input.kids || it.audience == BriefAudience.KIDS) && it.date == input.now.atZone(input.zone).toLocalDate() && it.zoneId == input.zone.id }
                face(if (brief == null) text("Build your day from saved information") else BriefPresentation.title(brief.period,locale), brief?.facts.orEmpty().map { fact ->
                    EverydayCardRow(BriefPresentation.factText(fact, locale, input.zone), "", EverydayTarget.Brief(brief!!.id), true)
                }, brief?.let { EverydayTarget.Brief(it.id) } ?: EverydayTarget.BriefLatest, true,
                    status = mode + " · " + text(if (brief == null) "No saved brief yet" else "Saved on this phone"), at = brief?.generatedAt)
            }
            EverydayCardKind.DEVICE -> face(input.batteryPercent?.let { "$it%" } ?: text("Battery unavailable"),
                listOf(EverydayCardRow(text(if (input.charging) "Charging" else "On battery"))), EverydayTarget.Devices)
            EverydayCardKind.QUICK_ACTIONS -> face(text("Add to your plan"), listOf(EverydayCardRow(text("Add task"), target = EverydayTarget.Create(OrganizerKind.TASK)),
                EverydayCardRow(text("Set alarm"), target = EverydayTarget.Create(OrganizerKind.ALARM)), EverydayCardRow(text("Start timer"), target = EverydayTarget.Create(OrganizerKind.TIMER))), EverydayTarget.Organizer)
            EverydayCardKind.DASHBOARD -> {
                val faces=config.sections.map { section -> project(config.copy(kind=section,sourceId=when(section) {
                    EverydayCardKind.WEATHER -> config.sourceId;EverydayCardKind.HEADLINES -> config.headlineSourceId;else -> null }),input,locale,externalHost,locked,text) }
                face(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(input.now.atZone(zone)),
                    faces.map { child -> EverydayCardRow(child.title+" · "+child.value,listOfNotNull(child.status,child.attribution,
                        child.observedAt?.let { text("Saved at")+" · "+stamp(it) }).joinToString(" · "),child.target,
                        publisher=child.attribution,savedAt=child.observedAt,stale=child.stale) },
                    EverydayTarget.Organizer, status = mode)
            }
        }
    }
}
