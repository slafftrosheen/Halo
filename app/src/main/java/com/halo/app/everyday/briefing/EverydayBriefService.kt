package com.halo.app.everyday.briefing

import com.halo.app.daily.DailyAssistantObservation
import com.halo.app.everyday.EverydayDisclosureRevoked
import com.halo.app.everyday.collectors.CollectorRepository
import com.halo.app.everyday.organizer.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Shared manual/voice/Screen/widget owner. Building a brief does not refresh sources or start speech. */
class EverydayBriefService(private val repository: BriefRepository, private val collectors: CollectorRepository,
    private val organizer: OrganizerRepository, private val attention: suspend () -> DailyAssistantObservation,
    private val audience: () -> BriefAudience, private val childLocalFactIds: suspend () -> Set<String>,
    private val localFactStillExists: suspend (BriefFact) -> Boolean, private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val contentChanges: Flow<Unit> = flowOf(Unit),private val readState: (suspend ()->Any)? = null) {
    private suspend fun disclosureState(): Any = readState?.invoke() ?: Triple(repository.settings().version,
        collectors.states().map {it.source.id to it.source.version}.sortedBy {it.first},
        organizer.records(true).map {it.id to it.version}.sortedBy {it.first})
    /** Only active subscribers tick. No fetch, model, background loop or stored timestamp is advanced. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun changes(): Flow<Unit> = merge(repository.observe().map { Unit }, repository.observeSettings().map { Unit },
        collectors.observe().map { Unit }, organizer.observe().map { Unit }, contentChanges,
        flow { while (currentCoroutineContext().isActive) { emit(Unit); delay(60_000) } })
    // Revocation is normal during live organizer/profile changes. Clear this emission
    // without terminating the subscriber; direct reads still reject stale authority.
    private suspend fun observedVisible(id: String): EverydayBrief? = try { visible(id) }
        catch (_: EverydayDisclosureRevoked) { null }
    fun observeVisible(id: String): Flow<EverydayBrief?> = changes().map { observedVisible(id) }.distinctUntilChanged().flowOn(Dispatchers.IO)
    fun observeLatestVisible(): Flow<EverydayBrief?> = changes().map {
        val today = clock.instant().atZone(zone()).toLocalDate()
        val family = audience()
        repository.recent().firstOrNull { it.date == today && it.zoneId == zone().id &&
            (family != BriefAudience.KIDS || it.audience == BriefAudience.KIDS) }?.let { observedVisible(it.id) }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)
    suspend fun build(period: BriefPeriod, requestedAudience: BriefAudience = audience()): EverydayBrief {
        val now = Instant.ofEpochMilli(clock.millis()); val deviceZone = zone(); val familyAudience = audience()
        val actualAudience = if (familyAudience == BriefAudience.KIDS) BriefAudience.KIDS else requestedAudience
        val day = now.atZone(deviceZone).toLocalDate()
        val start = day.atStartOfDay(deviceZone).toInstant(); val end = day.plusDays(if (period == BriefPeriod.WEEKLY) 7 else 1).atStartOfDay(deviceZone).toInstant()
        val records = organizer.records()
        val occurrences = mutableListOf<OrganizerOccurrence>()
        val outstanding = mutableListOf<OutstandingReminder>()
        records.forEach { record ->
            val overrides=organizer.overrides(record.id)
            occurrences += OrganizerRecurrence.between(record,overrides,start,end,deviceZone)
            if(record.kind==OrganizerKind.REMINDER) outstanding += OutstandingReminders.project(record,overrides,
                organizer.receipts(record.id),start,deviceZone)
            require(occurrences.size+outstanding.size<=10_000) {"Brief organizer input exceeds the supported limit"}
        }
        val observedAttention=attention()
        val baseline = repository.baseline(day, deviceZone, actualAudience)
        val settings = repository.settings()
        val projection = EverydayBriefEngine.project(BriefInput(now, deviceZone, actualAudience, period,
            collectors.states(), records, occurrences, observedAttention.snapshot, baseline, settings.childRecordIds, childLocalFactIds(),
            observedAttention.observedAt, outstanding), settings)
        check(audience() == familyAudience) { "Experience changed; build the brief again" }
        val saved = repository.publish(projection)
        return visible(saved.id) ?: error("Brief is no longer available")
    }
    /** Revalidate current source selection and child authority before re-displaying a stored brief. */
    suspend fun visible(id: String): EverydayBrief? {
        val disclosure=com.halo.app.everyday.EverydayReadAuthority.capture()
        val canonical=disclosureState()
        val stored = repository.get(id) ?: return null
        val familyAudience = audience()
        val actualAudience = if (familyAudience == BriefAudience.KIDS) BriefAudience.KIDS else stored.audience
        val allowed = collectors.states().filter { state -> state.source.selected && state.source.enabled && !state.source.muted &&
            (actualAudience != BriefAudience.KIDS || state.source.childApproved) &&
            stored.sources.any { it.id == state.source.id && it.version == state.source.version } }.map { it.source.id }.toSet() + "halo:attention"
        val currentRecords = organizer.records().filter { it.deletedAt == null }.associateBy { it.id }
        val settings = repository.settings()
        val childRecords = settings.childRecordIds
        val childFacts = childLocalFactIds()
        suspend fun visible(fact: BriefFact) = fact.sourceId in allowed &&
            (fact.values["recordId"]?.let { currentRecords[it] != null && (actualAudience != BriefAudience.KIDS || it in childRecords) } ?: true) &&
            (fact.values["expires"]?.let { Instant.parse(it) > clock.instant() } ?: true) &&
            localFactStillExists(fact) && (actualAudience != BriefAudience.KIDS || when (fact.kind) {
                BriefFactKind.ATTENTION, BriefFactKind.NOTE, BriefFactKind.ACTIVITY, BriefFactKind.NOTIFICATION -> fact.values["localId"] in childFacts
                else -> true
            })
        val facts = EverydayBriefEngine.select(stored.facts.filter { visible(it) }, actualAudience, settings)
        val eligibleChanges = stored.changes.filter { it.sourceId in allowed &&
            (it.fact?.let { fact -> visible(fact) && EverydayBriefEngine.included(fact, settings) } ?: (actualAudience != BriefAudience.KIDS &&
                (it.section?.let { section -> section in settings.sections } ?: (settings.sections == BriefSection.entries.toSet())))) }
        val differentAudience = actualAudience != stored.audience
        val changes = if (differentAudience) emptyList() else if (actualAudience == BriefAudience.KIDS) {
            val visibleIds = EverydayBriefEngine.select(eligibleChanges.mapNotNull { it.fact }, actualAudience, settings).map { it.id }.toSet()
            eligibleChanges.filter { it.factId in visibleIds }.take(12)
        } else eligibleChanges
        if (audience() != familyAudience) throw EverydayDisclosureRevoked("Experience changed; reopen the brief")
        if(actualAudience==BriefAudience.KIDS && childLocalFactIds()!=childFacts)
            throw EverydayDisclosureRevoked("Shared local items changed; reopen the brief")
        if(disclosureState()!=canonical) throw EverydayDisclosureRevoked("Everyday disclosure authority changed")
        com.halo.app.everyday.EverydayReadAuthority.requireCurrent(disclosure)
        return stored.copy(audience = actualAudience, facts = facts, sources = stored.sources.filter { it.id in allowed },
            changes = changes, baselineAt = if (differentAudience) null else stored.baselineAt,
            baselineMissing = stored.baselineMissing || differentAudience && stored.period == BriefPeriod.CHANGES,
            comparisonUnavailableSources = if (differentAudience) stored.sources.map { it.id }.toSet().intersect(allowed)
                else stored.comparisonUnavailableSources.intersect(allowed),
            omittedChanges = stored.omittedChanges + stored.changes.size - changes.size,
            omittedFacts = stored.omittedFacts + stored.facts.size - facts.size)
    }
}

/** WorkManager may be late; never announce a missed morning brief in the evening or during quiet hours. */
object BriefSchedulePolicy {
    fun due(settings: BriefSettings, now: Instant, zone: ZoneId, delivered: Set<String>): BriefPeriod? {
        if (!settings.proactive) return null
        val local = now.atZone(zone)
        if (settings.quiet(local.toLocalTime())) return null
        return listOf(BriefPeriod.MORNING to settings.morning, BriefPeriod.EVENING to settings.evening).firstOrNull { (period, time) ->
            val target = local.toLocalDate().atTime(time).atZone(zone).toInstant()
            now >= target && now < target.plusSeconds(4 * 3600) && key(local.toLocalDate(), zone, period) !in delivered
        }?.first
    }
    fun key(date: LocalDate, zone: ZoneId, period: BriefPeriod) = "$date|${zone.id}|${period.name}"
}
