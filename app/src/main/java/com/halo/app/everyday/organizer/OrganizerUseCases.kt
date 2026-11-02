package com.halo.app.everyday.organizer

import java.time.*
import java.util.Locale
import java.util.UUID

/** Shared operations for manual UI, widgets and the existing runtime tool executor. No model dependency. */
class OrganizerUseCases(
    val repository: OrganizerRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val elapsedMs: () -> Long,
    private val bootId: () -> String,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun create(kind: OrganizerKind, title: String, details: String = "", time: OrganizerTime? = null,
        repeat: RepeatRule? = null, priority: TaskPriority = TaskPriority.NORMAL, parentId: String? = null,
        notifyBeforeMinutes: Int? = null, timerDurationMs: Long? = null,
        links: List<OrganizerLink> = emptyList(), sourceUrl: String? = null, sourceLabel: String? = null, location: String = ""): OrganizerRecord {
        val now = Instant.ofEpochMilli(clock.millis())
        val timer = timerDurationMs?.let { TimerState(it, it).start(now, elapsedMs(), bootId()) }
        return repository.save(OrganizerRecord(newId(), kind, title.trim(), details, time, repeat, priority,
            parentId = parentId, notifyBeforeMinutes = notifyBeforeMinutes, timer = timer, links = links,
            sourceUrl = sourceUrl, sourceLabel = sourceLabel, createdAt = now, location = location), null)
    }
    suspend fun requireCurrent(id: String, version: Long): OrganizerRecord {
        val record = repository.get(id) ?: throw OrganizerConflict("Record no longer exists")
        if (record.version != version || record.deletedAt != null) throw OrganizerConflict("Record changed; reopen it")
        return record
    }
    suspend fun completeTask(id: String, version: Long, occurrenceStart: Instant? = null): OrganizerRecord {
        val record = requireCurrent(id, version)
        require(record.kind == OrganizerKind.TASK)
        if (record.repeat != null) {
            require(occurrenceStart != null) { "Choose which recurring task occurrence to complete" }
            val existing = repository.overrides(id).find { it.originalStart == occurrenceStart }
            return repository.saveOverride(existing?.copy(disposition = OccurrenceDisposition.COMPLETED)
                ?: OccurrenceOverride(id, occurrenceStart, OccurrenceDisposition.COMPLETED), version)
        }
        require(occurrenceStart == null)
        return repository.save(record.copy(status = OrganizerStatus.COMPLETED), version)
    }
    suspend fun reopenTask(id: String, version: Long, occurrenceStart: Instant? = null): OrganizerRecord {
        val record = requireCurrent(id, version)
        require(record.kind == OrganizerKind.TASK)
        if (record.repeat != null) {
            require(occurrenceStart != null)
            val existing = repository.overrides(id).find { it.originalStart == occurrenceStart }
                ?: throw OrganizerConflict("This occurrence is already open")
            require(existing.disposition == OccurrenceDisposition.COMPLETED)
            return if (existing.replacementTime != null) repository.saveOverride(existing.copy(disposition = OccurrenceDisposition.REPLACED), version)
                else repository.removeOverride(id, occurrenceStart, version)
        }
        require(occurrenceStart == null)
        return repository.save(record.copy(status = OrganizerStatus.ACTIVE), version)
    }
    suspend fun pauseTimer(id: String, version: Long): OrganizerRecord {
        val record = requireCurrent(id, version)
        val timer = requireNotNull(record.timer)
        return repository.save(record.copy(timer = timer.pause(clock.instant(), elapsedMs(), bootId())), version)
    }
    suspend fun resumeTimer(id: String, version: Long): OrganizerRecord {
        val record = requireCurrent(id, version)
        require(record.status == OrganizerStatus.ACTIVE)
        return repository.save(record.copy(timer = requireNotNull(record.timer).start(Instant.ofEpochMilli(clock.millis()), elapsedMs(), bootId())), version)
    }
    suspend fun stopTimer(id: String, version: Long): OrganizerRecord {
        val record = requireCurrent(id, version)
        val timer = requireNotNull(record.timer)
        return repository.save(record.copy(status = OrganizerStatus.COMPLETED,
            timer = timer.copy(remainingMs = 0, startedAt = null, elapsedStartMs = null, bootId = null)), version)
    }
    suspend fun cancel(id: String, version: Long): OrganizerRecord {
        val record = requireCurrent(id, version)
        return repository.save(record.copy(status = OrganizerStatus.CANCELLED), version)
    }
    suspend fun agenda(from: Instant, until: Instant, kinds: Set<OrganizerKind> = setOf(OrganizerKind.EVENT, OrganizerKind.TASK, OrganizerKind.REMINDER, OrganizerKind.ALARM)): List<OrganizerOccurrence> {
        val results = mutableListOf<OrganizerOccurrence>()
        repository.records().filter { it.kind in kinds }.forEach { record ->
            results += OrganizerRecurrence.between(record, repository.overrides(record.id), from, until, zone())
            require(results.size <= 10000) { "Too many agenda entries; narrow the date range" }
        }
        return results.sortedWith(compareBy<OrganizerOccurrence> { it.start }.thenByDescending { it.record.priority }.thenBy { it.key })
    }
    suspend fun search(query: String, kind: OrganizerKind? = null): List<OrganizerRecord> {
        require(query.length <= 200)
        val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return repository.records().filter { record -> (kind == null || kind == record.kind) &&
            words.all { word -> word in "${record.title}\n${record.details}".lowercase(Locale.ROOT) } }
            .sortedWith(compareBy<OrganizerRecord> { it.status != OrganizerStatus.ACTIVE }.thenByDescending { it.priority }.thenByDescending { it.updatedAt }.thenBy { it.id })
    }
}

data class PlannedDelivery(val occurrenceKey: String, val recordId: String, val recordVersion: Long,
    val triggerAt: Instant, val exactRequested: Boolean, val alarmClock: Boolean,
    val elapsedDeadlineMs: Long? = null)

/** Plans one next alarm per record. Android callbacks schedule the following occurrence after delivery. */
object OrganizerSchedulePlanner {
    private val terminal = setOf(DeliveryState.FIRED, DeliveryState.DISMISSED, DeliveryState.MISSED, DeliveryState.CANCELLED)
    fun next(record: OrganizerRecord, exceptions: List<OccurrenceOverride>, receipts: List<DeliveryReceipt>,
        now: Instant, zone: ZoneId, elapsedMs: Long, bootId: String): PlannedDelivery? {
        if (record.deletedAt != null || record.status != OrganizerStatus.ACTIVE) return null
        val snoozes = receipts.mapNotNull { OrganizerDeliveryTiming.snoozedPlan(record, it, now, elapsedMs, bootId) }
        if (record.kind == OrganizerKind.TIMER) {
            if (snoozes.isNotEmpty()) return snoozes.minBy { it.triggerAt }
            val timer = requireNotNull(record.timer)
            if (timer.startedAt == null) return null
            val key = "${record.id}@timer:${timer.runId}"
            if (receipts.any { it.occurrenceKey == key && (it.state in setOf(DeliveryState.FIRED, DeliveryState.DISMISSED, DeliveryState.MISSED) ||
                    it.state == DeliveryState.CANCELLED && it.recordVersion == record.version) }) return null
            val remaining = timer.remaining(now, elapsedMs, bootId)
            if (remaining == 0L && receipts.any { it.occurrenceKey == key && it.recordVersion == record.version &&
                    it.state in setOf(DeliveryState.NOTIFICATIONS_BLOCKED, DeliveryState.FAILED) }) return null
            return PlannedDelivery(key, record.id, record.version, now.plusMillis(remaining), true, false,
                if (timer.bootId == bootId) elapsedMs + remaining else null)
        }
        val time = record.time ?: return null
        if (record.kind in setOf(OrganizerKind.EVENT, OrganizerKind.TASK) && record.notifyBeforeMinutes == null) return null
        val lead = (record.notifyBeforeMinutes ?: 0).toLong() * 60
        val overrides = exceptions.associateBy { it.originalStart }
        val candidates = snoozes.toMutableList()
        fun add(original: Instant, start: Instant) {
            val key = "${record.id}@${original.toEpochMilli()}"
            val receipt = receipts.find { it.occurrenceKey == key }
            if (snoozes.any { it.occurrenceKey == key }) return
            if (receipt != null && (receipt.state in setOf(DeliveryState.FIRED, DeliveryState.DISMISSED, DeliveryState.MISSED) ||
                receipt.state == DeliveryState.CANCELLED && receipt.recordVersion == record.version)) return
            val trigger = start.minusSeconds(lead)
            if (trigger < now) return
            candidates += PlannedDelivery(key, record.id, record.version, trigger,
                record.kind in setOf(OrganizerKind.ALARM, OrganizerKind.REMINDER), record.kind == OrganizerKind.ALARM)
        }
        // Exceptions retain identity and can move across the next normal occurrence.
        exceptions.filter { it.disposition == OccurrenceDisposition.REPLACED }.forEach {
            add(it.originalStart, requireNotNull(it.replacementTime).instant(zone))
        }
        receipts.filter { it.snoozedUntil != null && it.recordVersion == record.version && it.state !in terminal }.forEach { receipt ->
            receipt.occurrenceKey.substringAfterLast('@').toLongOrNull()?.let { add(Instant.ofEpochMilli(it), receipt.triggerAt.plusSeconds(lead)) }
        }
        for (slot in OrganizerRecurrence.slots(time, record.repeat, zone)) {
            if (slot.start.minusSeconds(lead) < now || overrides.containsKey(slot.identity)) continue
            add(slot.identity, slot.start)
            if (candidates.any { it.occurrenceKey == "${record.id}@${slot.identity.toEpochMilli()}" }) break
        }
        return candidates.minWithOrNull(compareBy<PlannedDelivery> { it.triggerAt }.thenBy { it.occurrenceKey })
    }
}
