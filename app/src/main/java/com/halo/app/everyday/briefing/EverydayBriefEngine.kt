package com.halo.app.everyday.briefing

import com.halo.app.daily.DailyAssistantPriority
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import java.security.MessageDigest
import java.time.*

/** Deterministic facts only. No network, model, background work or synthesized private context. */
object EverydayBriefEngine {
    fun project(input: BriefInput, settings: BriefSettings = BriefSettings()): BriefProjection {
        val date = input.now.atZone(input.zone).toLocalDate()
        val from = date.atStartOfDay(input.zone).toInstant()
        val until = date.plusDays(1).atStartOfDay(input.zone).toInstant()
        val allowed = input.sources.filter { it.source.selected && it.source.enabled && !it.source.muted &&
            (input.audience != BriefAudience.KIDS || it.source.childApproved) }.sortedBy { it.source.id }
        val sources = allowed.map { state -> stamp(state) }.toMutableList()
        val facts = mutableListOf<BriefFact>()
        fun recordAllowed(record: OrganizerRecord) = input.audience != BriefAudience.KIDS || record.id in input.childRecordIds
        val organizerSource = allowed.firstOrNull { it.source.kind == CollectorKind.LOCAL_ORGANIZER }
        if (organizerSource != null) {
            val sourceId = organizerSource.source.id
            // These rows were read directly from the owner during this projection, not from a stale collector cache.
            sources.removeAll { it.id == sourceId }
            sources += stamp(organizerSource).copy(retrievedAt = input.now, verifiedAt = input.now,
                expiresAt = input.now.plusSeconds(900), attempt = CollectorAttemptState.CURRENT)
            input.occurrences.filter { it.start < until && (it.end?.let { end -> end > from } ?: (it.start >= from)) &&
                it.record.deletedAt == null && it.record.status == OrganizerStatus.ACTIVE && recordAllowed(it.record) }.forEach { occurrence ->
                val record = occurrence.record
                if (!occurrence.completed || input.period == BriefPeriod.EVENING) {
                    val kind = BriefFactKind.valueOf(record.kind.name)
                    val priority = priority(record, settings) + when {
                        occurrence.completed -> -100
                        record.kind == OrganizerKind.EVENT && occurrence.end?.let { it <= input.now } == true -> -50
                        record.kind == OrganizerKind.EVENT && occurrence.start <= input.now -> 40
                        occurrence.start <= input.now -> 30
                        occurrence.start <= input.now.plusSeconds(3600) -> 20
                        else -> 0
                    }
                    facts += BriefFact("organizer:${occurrence.key}", kind, record.title, record.details.take(500), sourceId,
                        fingerprint(record.version.toString(), occurrence.start.toString(), occurrence.end?.toString().orEmpty(), occurrence.completed.toString(), record.title),
                        "halo://organizer/record/${record.id}", priority, occurrence.start, record.sourceUrl,
                        mapOf("completed" to occurrence.completed.toString(), "recordId" to record.id,
                            "occurrence" to occurrence.originalStart.toString(), "version" to record.version.toString()))
                }
            }
            input.organizer.filter { it.deletedAt == null && it.status == OrganizerStatus.ACTIVE && recordAllowed(it) }.forEach { record ->
                if (record.kind == OrganizerKind.TASK && record.repeat == null &&
                    (record.time == null || record.time.instant(input.zone) < from)) {
                    facts += BriefFact("organizer:${record.id}", BriefFactKind.TASK, record.title, record.details.take(500), sourceId,
                        record.version.toString(), "halo://organizer/record/${record.id}", priority(record, settings) + if (record.time != null) 40 else 0,
                        record.time?.instant(input.zone), record.sourceUrl, mapOf("recordId" to record.id, "version" to record.version.toString()))
                }
                if (record.kind == OrganizerKind.TIMER) facts += BriefFact("organizer:${record.id}", BriefFactKind.TIMER,
                    record.title, "", sourceId, record.version.toString(), "halo://organizer/record/${record.id}", 10,
                    record.timer?.startedAt, values = mapOf("recordId" to record.id, "running" to (record.timer?.startedAt != null).toString()))
            }
            if (input.period == BriefPeriod.EVENING) input.organizer.filter { it.deletedAt == null && it.kind == OrganizerKind.TASK &&
                it.status == OrganizerStatus.COMPLETED && it.updatedAt in from..input.now && recordAllowed(it) }.forEach { record ->
                facts += BriefFact("organizer:${record.id}", BriefFactKind.TASK, record.title, record.details.take(500), sourceId,
                    record.version.toString(), "halo://organizer/record/${record.id}", -10, record.updatedAt,
                    values = mapOf("recordId" to record.id, "version" to record.version.toString(), "completed" to "true"))
            }
        }
        allowed.forEach { state ->
            val source = state.source
            val snapshot = state.snapshot?.takeIf { it.sourceVersion == source.version } ?: return@forEach
            when (val payload = snapshot.payload) {
                is CollectorPayload.Articles -> payload.items.forEach { item -> facts += BriefFact("${source.id}:article:${item.id}",
                    BriefFactKind.HEADLINE, item.title, item.text.take(500), source.id,
                    fingerprint(item.title, item.text, item.url.orEmpty(), item.publishedAt?.toString().orEmpty()),
                    "halo://sources/${source.id}/article/${item.id}", 0, item.publishedAt, item.url) }
                is CollectorPayload.Weather -> {
                    val weather = payload.fact
                    val today = weather.days.find { it.date == input.now.atZone(ZoneId.of(weather.place.zoneId)).toLocalDate() }
                    val values = buildMap {
                        weather.temperatureC?.let { put("temperatureC", it.toString()) }; weather.weatherCode?.let { put("weatherCode", it.toString()) }
                        today?.maximumC?.let { put("maximumC", it.toString()) }; today?.minimumC?.let { put("minimumC", it.toString()) }
                        today?.precipitationProbability?.let { put("rainProbability", it.toString()) }
                    }
                    facts += BriefFact("${source.id}:weather", BriefFactKind.WEATHER, weather.place.name, "", source.id,
                        fingerprint(values.toSortedMap().toString(), weather.observedAt?.toString().orEmpty()),
                        "halo://sources/${source.id}/weather", if ((today?.precipitationProbability ?: 0) >= 60) 15 else 0,
                        weather.observedAt, weather.attributionUrl, values)
                }
                is CollectorPayload.Warnings -> payload.items.filter { it.expires == null || it.expires > input.now }.forEach { item ->
                    facts += BriefFact("${source.id}:warning:${item.id}", BriefFactKind.WARNING, item.title, item.text.take(500), source.id,
                        fingerprint(item.title, item.text, item.severity.orEmpty(), item.expires?.toString().orEmpty()),
                        "halo://sources/${source.id}/warning/${item.id}", 80, item.onset, item.url,
                        buildMap { item.severity?.let { put("severity", it) }; item.area?.let { put("area", it) }
                            item.expires?.let { put("expires", it.toString()) } })
                }
                is CollectorPayload.Calendar -> payload.preview.records.forEach { record ->
                    OrganizerRecurrence.between(record, payload.preview.exceptions.filter { it.recordId == record.id }, from, until, input.zone).forEach { occurrence ->
                        facts += BriefFact("${source.id}:event:${occurrence.key}", BriefFactKind.EVENT, occurrence.record.title, occurrence.record.details.take(500),
                            source.id, fingerprint(occurrence.record.title, occurrence.record.details.take(500), occurrence.start.toString(),
                                occurrence.end?.toString().orEmpty(), occurrence.record.sourceUrl.orEmpty()),
                            "halo://sources/${source.id}/calendar", 5, occurrence.start, occurrence.record.sourceUrl,
                            mapOf("calendarSelection" to SavedCalendarSelection(record.id, occurrence.originalStart, snapshot.contentRevision, input.zone.id).encode()))
                    }
                }
                is CollectorPayload.Local -> if (source.kind != CollectorKind.LOCAL_ORGANIZER) payload.items.filter {
                    input.audience != BriefAudience.KIDS || source.kind == CollectorKind.LOCAL_DEVICE || it.id in input.childLocalFactIds
                }.forEach { item ->
                    val kind = when (source.kind) {
                        CollectorKind.LOCAL_WORKSPACE -> BriefFactKind.NOTE
                        CollectorKind.LOCAL_NOTIFICATIONS -> BriefFactKind.NOTIFICATION
                        CollectorKind.LOCAL_DEVICE -> BriefFactKind.DEVICE
                        else -> BriefFactKind.ACTIVITY
                    }
                    facts += BriefFact("${source.id}:${item.id}", kind, item.title, item.summary, source.id,
                        fingerprint(item.revision.orEmpty(), item.title, item.summary), item.route, 0, item.occurredAt, values = mapOf("localId" to item.id))
                }
            }
        }
        // The existing Daily Assistant owns approvals, recovery and Study prompts; preserve that owner and its action route.
        val attention = input.attention.items.filter { input.audience != BriefAudience.KIDS || it.id in input.childLocalFactIds }
        if (attention.isNotEmpty()) {
            sources += BriefSourceStamp("halo:attention", 0, "Halo", "Halo", null, false, input.now, input.now,
                input.now.plusSeconds(900), CollectorAttemptState.CURRENT, true)
            attention.forEach { item -> facts += BriefFact("attention:${item.id}", BriefFactKind.ATTENTION, item.title,
                item.summary.orEmpty(), "halo:attention", fingerprint(item.title, item.summary.orEmpty(), item.priority.name),
                "halo://day/attention/${item.id}", when (item.priority) {
                    DailyAssistantPriority.NEEDS_YOU_NOW -> 100
                    DailyAssistantPriority.DUE -> 60
                    DailyAssistantPriority.SOON -> 30
                    DailyAssistantPriority.AVAILABLE -> 0
                }, item.occurredAt, values = mapOf("localId" to item.id)) }
        }
        // Imported feed IDs can be long. Stable opaque keys keep the daily comparison index bounded.
        val unique = facts.distinctBy { it.id }.map { it.copy(id = fingerprint(it.id)) }
        require(unique.size <= 20_000) { "Brief input exceeds the supported fact limit" }
        val current = unique.associateBy { it.id }
        val fingerprints = unique.sortedBy { it.id }.map { BriefFingerprint(it.id, it.sourceId, it.revision, it.kind) }
        val observed = sources.filter { it.retrievedAt != null }.map { it.id }.toSet()
        val baseline = input.baseline?.takeIf { it.date == date && it.zoneId == input.zone.id && it.audience == input.audience }
        val before = baseline?.facts?.associateBy { it.id }.orEmpty()
        val changes = if (baseline == null) emptyList() else buildList {
            unique.forEach { fact ->
                val old = before[fact.id]
                if (fact.sourceId in baseline.observedSources) when {
                    old == null -> add(BriefChange(BriefChangeKind.ADDED, fact.id, fact.sourceId, fact))
                    old.revision != fact.revision -> add(BriefChange(BriefChangeKind.UPDATED, fact.id, fact.sourceId, fact))
                }
            }
            // Only a current, successfully read local source can assert removal. Missing, muted, failed or rolled-out feed items cannot.
            val reliableLocal = sources.filter { !it.external && it.verifiedAt == input.now && it.attempt == CollectorAttemptState.CURRENT }.map { it.id }.toSet()
            before.values.filter { it.id !in current && it.sourceId in reliableLocal }.forEach { old ->
                add(BriefChange(BriefChangeKind.REMOVED, old.id, old.sourceId, null))
            }
        }
        val chosen = select(unique, input.audience, settings)
        val digest = fingerprint(fingerprints.joinToString("\n") { "${it.id}:${it.revision}" })
        val identity = fingerprint(input.now.toString(), input.zone.id, input.period.name, input.audience.name, digest,
            settings.version.toString(), settings.headlineLimit.toString(), settings.taskLimit.toString(), settings.pinnedRecordIds.sorted().joinToString("\n"),
            baseline?.capturedAt?.toString().orEmpty(), sources.sortedBy { it.id }.joinToString("\n") { "${it.id}:${it.version}:${it.verifiedAt}:${it.attempt}" })
        return BriefProjection(EverydayBrief(identity, input.period, input.audience, date, input.zone.id,
            input.now, chosen, sources.sortedBy { it.id }, changes.take(200), baseline?.capturedAt, input.period == BriefPeriod.CHANGES && baseline == null,
            if (baseline == null) observed else (observed - baseline.observedSources) + (baseline.observedSources - observed),
            unique.size - chosen.size, (changes.size - 200).coerceAtLeast(0), digest), BriefBaseline(date, input.zone.id, input.audience, input.now, fingerprints, observed))
    }
    internal fun select(facts: List<BriefFact>, audience: BriefAudience, settings: BriefSettings): List<BriefFact> {
        val kinds = mutableMapOf<BriefFactKind, Int>()
        val order = compareByDescending<BriefFact> { it.priority }.thenBy { rank(it.kind, audience) }
            .thenBy { if (it.kind == BriefFactKind.HEADLINE) null else it.at ?: Instant.MAX }
            .thenByDescending { if (it.kind == BriefFactKind.HEADLINE) it.at else null }.thenBy { it.id }
        return facts.sortedWith(order).filter { fact ->
            val limit = when (fact.kind) {
                BriefFactKind.HEADLINE -> if (audience == BriefAudience.KIDS) minOf(settings.headlineLimit, 3) else settings.headlineLimit
                BriefFactKind.TASK -> settings.taskLimit
                BriefFactKind.NOTE, BriefFactKind.ACTIVITY, BriefFactKind.NOTIFICATION, BriefFactKind.DEVICE -> 2
                else -> 10
            }
            val count = kinds[fact.kind] ?: 0
            if (count < limit) { kinds[fact.kind] = count + 1; true } else false
        }.take(if (audience == BriefAudience.KIDS) 12 else 40)
    }
    private fun rank(kind: BriefFactKind, audience: BriefAudience): Int = when (kind) {
        BriefFactKind.ATTENTION -> 0; BriefFactKind.WARNING -> 1; BriefFactKind.EVENT -> 2
        BriefFactKind.TASK -> if (audience == BriefAudience.STUDY) 1 else 3
        BriefFactKind.REMINDER, BriefFactKind.ALARM, BriefFactKind.TIMER -> 4
        BriefFactKind.WEATHER -> 5; BriefFactKind.HEADLINE -> 6; else -> 7
    }
    private fun priority(record: OrganizerRecord, settings: BriefSettings) = record.priority.ordinal * 10 + if (record.id in settings.pinnedRecordIds) 50 else 0
    private fun stamp(state: CollectorState) = BriefSourceStamp(state.source.id, state.source.version, state.source.title, state.source.publisher,
        state.source.attributionUrl, state.source.external, state.snapshot?.retrievedAt, state.snapshot?.verifiedAt,
        state.snapshot?.expiresAt, state.attempt.state, state.source.childApproved)
    internal fun fingerprint(vararg values: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        values.forEach { value -> val bytes = value.toByteArray(Charsets.UTF_8); digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array()); digest.update(bytes) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
