package com.halo.app.everyday.organizer

import java.time.Instant
import java.time.ZoneId

/** A delayed first worker records the authored deadline, never a freshly invented timer deadline. */
object OrganizerInitialDelivery {
    fun overdue(record: OrganizerRecord, exceptions: List<OccurrenceOverride>, receipts: List<DeliveryReceipt>,
        now: Instant, zone: ZoneId, elapsedMs: Long, bootId: String): List<DeliveryReceipt> {
        if (record.status != OrganizerStatus.ACTIVE || record.deletedAt != null) return emptyList()
        fun unrecorded(key: String) = receipts.none { it.occurrenceKey == key && (it.recordVersion == record.version ||
            it.state in setOf(DeliveryState.FIRED, DeliveryState.DISMISSED, DeliveryState.MISSED)) }
        fun receipt(key: String, at: Instant) = DeliveryReceipt(key, record.id, record.version, at, DeliveryState.PENDING, now)
        record.timer?.let { timer ->
            if (timer.startedAt == null) return emptyList()
            val key = "${record.id}@timer:${timer.runId}"
            val value = receipt(key, timer.startedAt.plusMillis(timer.remainingMs))
            return if (unrecorded(key) && OrganizerDeliveryTiming.latenessMillis(record, value, now, elapsedMs, bootId) >= 0) listOf(value) else emptyList()
        }
        val time = record.time ?: return emptyList()
        if (record.kind in setOf(OrganizerKind.EVENT, OrganizerKind.TASK) && record.notifyBeforeMinutes == null) return emptyList()
        val lead = (record.notifyBeforeMinutes ?: 0) * 60L
        val overridden = exceptions.associateBy { it.originalStart }
        var lastOlder: DeliveryReceipt? = null
        val recent = mutableListOf<DeliveryReceipt>()
        fun consider(identity: Instant, start: Instant) {
            val at = start.minusSeconds(lead)
            if (at > now) return
            val value = receipt("${record.id}@${identity.toEpochMilli()}", at)
            if (at < now.minusSeconds(600)) {
                if (lastOlder == null || at > lastOlder!!.triggerAt) lastOlder = value
            } else if (unrecorded(value.occurrenceKey)) recent += value
        }
        for (slot in OrganizerRecurrence.slots(time, record.repeat, zone)) {
            if (slot.start.minusSeconds(lead) > now) break
            if (slot.identity !in overridden) consider(slot.identity, slot.start)
        }
        exceptions.filter { it.disposition == OccurrenceDisposition.REPLACED }.forEach {
            consider(it.originalStart, requireNotNull(it.replacementTime).instant(zone))
        }
        lastOlder?.takeIf { unrecorded(it.occurrenceKey) }?.let(recent::add)
        return recent.distinctBy { it.occurrenceKey }.sortedBy { it.triggerAt }
    }
}
