package com.halo.app.everyday.organizer

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class OrganizerKind { EVENT, TASK, REMINDER, ALARM, TIMER }
enum class OrganizerStatus { ACTIVE, COMPLETED, CANCELLED }
enum class TaskPriority { LOW, NORMAL, HIGH, URGENT }
enum class RepeatFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class OccurrenceDisposition { CANCELLED, COMPLETED, REPLACED }
enum class DeliveryState { PENDING, EXACT, APPROXIMATE, NOTIFICATIONS_BLOCKED, DELIVERING, FIRED, DISMISSED, MISSED, CANCELLED, FAILED }

/** Wall-clock recurrence retains the chosen zone across DST. Floating alarms follow the device zone. */
data class OrganizerTime(
    val start: LocalDateTime,
    val end: LocalDateTime? = null,
    val zoneId: String,
    val allDay: Boolean = false,
    val followDeviceZone: Boolean = false,
) {
    fun zone(deviceZone: ZoneId): ZoneId = if (followDeviceZone) deviceZone else ZoneId.of(zoneId)
    fun instant(deviceZone: ZoneId): Instant = start.atZone(zone(deviceZone)).toInstant()
    fun validate() {
        ZoneId.of(zoneId)
        require(start.year in 1970..2199 && (end == null || end.year in 1970..2199)) { "Date is outside the supported range" }
        require(end == null || end > start) { "End must follow start" }
        require(start.nano % 1000000 == 0 && (end == null || end.nano % 1000000 == 0)) { "Time precision cannot be smaller than a millisecond" }
        require(end == null || end.atZone(ZoneId.of(zoneId)).toInstant() > start.atZone(ZoneId.of(zoneId)).toInstant()) {
            "The time-zone change puts the end before the start; choose a later end"
        }
        require(!allDay || start.toLocalTime() == java.time.LocalTime.MIDNIGHT &&
            (end == null || end.toLocalTime() == java.time.LocalTime.MIDNIGHT)) { "All-day dates must use midnight boundaries" }
        require(end == null || Duration.between(start, end).toDays() <= 366) { "An event cannot exceed one year" }
    }
}

/** Small, explicit RFC 5545 subset. Unsupported imported rules are rejected rather than approximated. */
data class RepeatRule(
    val frequency: RepeatFrequency,
    val interval: Int = 1,
    val weekdays: Set<java.time.DayOfWeek> = emptySet(),
    val count: Int? = null,
    val until: Instant? = null,
) {
    init {
        require(interval in 1..366)
        require(count == null || count in 1..10000)
        require(count == null || until == null) { "Choose an occurrence count or an end date" }
        require(weekdays.isEmpty() || frequency == RepeatFrequency.WEEKLY) { "Weekday selection requires weekly recurrence" }
    }
}

/** Timer projection uses monotonic time on the same boot; wall deadline is the reboot fallback. */
data class TimerState(
    val durationMs: Long,
    val remainingMs: Long,
    val startedAt: Instant? = null,
    val elapsedStartMs: Long? = null,
    val bootId: String? = null,
    val runId: String = java.util.UUID.randomUUID().toString(),
) {
    init {
        require(durationMs in 1000..31L * 86400000 && remainingMs in 0..durationMs)
        require((startedAt == null) == (elapsedStartMs == null) && (startedAt == null) == (bootId == null))
        require(elapsedStartMs == null || elapsedStartMs >= 0)
        require(runId.matches(Regex("[A-Za-z0-9_.:-]{1,200}")))
    }
    fun remaining(now: Instant, elapsedMs: Long, currentBootId: String): Long {
        if (startedAt == null) return remainingMs
        val spent = if (bootId == currentBootId && elapsedMs >= requireNotNull(elapsedStartMs))
            elapsedMs - elapsedStartMs else Duration.between(startedAt, now).toMillis().coerceAtLeast(0)
        return (remainingMs - spent).coerceIn(0, remainingMs)
    }
    fun start(now: Instant, elapsedMs: Long, currentBootId: String): TimerState {
        require(startedAt == null && remainingMs > 0) { "Timer is already running or finished" }
        return copy(startedAt = now, elapsedStartMs = elapsedMs, bootId = currentBootId)
    }
    fun pause(now: Instant, elapsedMs: Long, currentBootId: String) = copy(
        remainingMs = remaining(now, elapsedMs, currentBootId), startedAt = null, elapsedStartMs = null, bootId = null)
}

data class OrganizerLink(val objectId: String, val relation: String = "RELATED_TO") {
    init { require(objectId.isNotBlank() && objectId.length <= 256); require(relation in setOf("RELATED_TO", "REFERENCES", "SOURCE_OF")) }
}

data class OrganizerRecord(
    val id: String,
    val kind: OrganizerKind,
    val title: String,
    val details: String = "",
    val time: OrganizerTime? = null,
    val repeat: RepeatRule? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val status: OrganizerStatus = OrganizerStatus.ACTIVE,
    val parentId: String? = null,
    val notifyBeforeMinutes: Int? = null,
    val timer: TimerState? = null,
    val links: List<OrganizerLink> = emptyList(),
    val sourceUrl: String? = null,
    val sourceLabel: String? = null,
    val version: Long = 1,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
    val deletedAt: Instant? = null,
    val location: String = "",
    val calendarUid: String? = null,
) {
    fun validate() {
        require(id.matches(Regex("[A-Za-z0-9_.:-]{1,200}"))) { "Invalid record identity" }
        require(title.isNotBlank() && title.length <= 200 && details.length <= 16000) { "Title or details exceed supported limits" }
        require(location.length <= 1000)
        require(calendarUid == null || calendarUid.isNotBlank() && calendarUid.length <= 2000 && calendarUid.none { it.isISOControl() })
        require(version > 0 && links.size <= 50 && links.distinctBy { it.objectId to it.relation }.size == links.size)
        require(parentId == null || kind == OrganizerKind.TASK && parentId != id)
        require(kind == OrganizerKind.TASK || time != null || kind == OrganizerKind.TIMER) { "Date and time are required" }
        require((kind == OrganizerKind.TIMER) == (timer != null)) { "Timer duration is required only for timers" }
        require(kind != OrganizerKind.TIMER || time == null && repeat == null && notifyBeforeMinutes == null)
        require(repeat == null || time != null)
        require(notifyBeforeMinutes == null || notifyBeforeMinutes in 0..10080 && time != null)
        require(kind != OrganizerKind.EVENT || time?.end != null) { "Events need an end time" }
        require(kind != OrganizerKind.ALARM || time?.allDay == false) { "An alarm requires a time" }
        require(sourceUrl == null || sourceUrl.length <= 2048 && java.net.URI(sourceUrl).let {
            it.scheme in setOf("https", "http") && !it.host.isNullOrBlank() && it.rawUserInfo == null })
        require(sourceLabel == null || sourceLabel.length <= 200)
        time?.validate()
        require(repeat?.until == null || repeat.until >= requireNotNull(time).start.atZone(ZoneId.of(time.zoneId)).toInstant())
    }
}

data class OrganizerOccurrence(
    val record: OrganizerRecord,
    val originalStart: Instant,
    val start: Instant,
    val end: Instant?,
    val completed: Boolean = false,
) { val key: String get() = "${record.id}@${originalStart.toEpochMilli()}" }

/** The original start is immutable identity even when this occurrence moves to another day. */
data class OccurrenceOverride(
    val recordId: String,
    val originalStart: Instant,
    val disposition: OccurrenceDisposition,
    val replacementTime: OrganizerTime? = null,
    val replacementTitle: String? = null,
    val version: Long = 1,
    val replacementDetails: String? = null,
    val replacementLocation: String? = null,
    /** null inherits the source URL; an empty string explicitly clears it. */
    val sourceUrlOverride: String? = null,
) {
    init {
        require(version > 0)
        require(originalStart.nano % 1000000 == 0) { "Occurrence identity must use millisecond precision" }
        require(disposition != OccurrenceDisposition.REPLACED || replacementTime != null)
        replacementTime?.validate()
        require(replacementTitle == null || replacementTitle.isNotBlank() && replacementTitle.length <= 200)
        require(replacementDetails == null || replacementDetails.length <= 16000)
        require(replacementLocation == null || replacementLocation.length <= 1000)
        require(sourceUrlOverride.isNullOrEmpty() || sourceUrlOverride.length <= 2048 && java.net.URI(sourceUrlOverride).let {
            it.scheme in setOf("https", "http") && !it.host.isNullOrBlank() && it.rawUserInfo == null })
    }
}

data class DeliveryReceipt(
    val occurrenceKey: String,
    val recordId: String,
    val recordVersion: Long,
    val triggerAt: Instant,
    val state: DeliveryState,
    val updatedAt: Instant,
    val detail: String? = null,
    val snoozedUntil: Instant? = null,
    val snoozeElapsedDeadlineMs: Long? = null,
    val snoozeBootId: String? = null,
)

data class OrganizerChange(val recordId: String, val version: Long, val action: String, val at: Instant)
class OrganizerConflict(message: String) : IllegalStateException(message)

interface OrganizerRepository {
    fun observe(): kotlinx.coroutines.flow.Flow<List<OrganizerRecord>>
    suspend fun records(includeDeleted: Boolean = false): List<OrganizerRecord>
    suspend fun get(id: String): OrganizerRecord?
    suspend fun save(record: OrganizerRecord, expectedVersion: Long?, resetOccurrences: Boolean = false): OrganizerRecord
    suspend fun delete(id: String, expectedVersion: Long): OrganizerRecord
    suspend fun restore(id: String, deletedVersion: Long): OrganizerRecord
    suspend fun purgeDeleted(id: String, deletedVersion: Long)
    suspend fun overrides(recordId: String): List<OccurrenceOverride>
    suspend fun saveOverride(exception: OccurrenceOverride, expectedRecordVersion: Long): OrganizerRecord
    suspend fun removeOverride(recordId: String, originalStart: Instant, expectedRecordVersion: Long): OrganizerRecord
    suspend fun receipts(recordId: String): List<DeliveryReceipt>
    /** Clock rollback requires an explicit comparison with the exact currently stored receipt. */
    suspend fun putReceipt(receipt: DeliveryReceipt, expected: DeliveryReceipt? = null): Boolean
    suspend fun claimDelivery(key: String, version: Long, triggerAt: Instant, now: Instant): Boolean
    suspend fun snoozeDelivery(key: String, version: Long, duration: Duration, elapsedMs: Long, bootId: String): OrganizerRecord
    suspend fun dismissDelivery(key: String, version: Long): Boolean
    suspend fun importLockedDeliveries(recordId: String, version: Long, receipts: List<DeliveryReceipt>): Boolean
    suspend fun pendingScheduleIds(): List<String>
    suspend fun acknowledgeSchedule(id: String, version: Long): Boolean
    suspend fun changes(since: Instant): List<OrganizerChange>
    /** Add-only, atomic import. An existing UID (including trash) is never overwritten or resurrected. */
    suspend fun importCalendar(preview: CalendarImportPreview): CalendarImportResult
}
