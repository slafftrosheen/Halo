package com.halo.app.everyday.briefing

import com.halo.app.everyday.organizer.*
import java.time.Instant
import java.time.ZoneId

data class OutstandingReminder(val occurrence: OrganizerOccurrence, val state: DeliveryState, val snoozedUntil: Instant?)

/** The current series and its receipts decide outstanding work, never an invented past recurrence. */
object OutstandingReminders {
    fun project(record: OrganizerRecord, overrides: List<OccurrenceOverride>, receipts: List<DeliveryReceipt>,
        dayStart: Instant, zone: ZoneId): List<OutstandingReminder> {
        if(record.kind!=OrganizerKind.REMINDER || record.status!=OrganizerStatus.ACTIVE || record.deletedAt!=null) return emptyList()
        val time=record.time ?: return emptyList()
        val current=receipts.filter {it.recordVersion==record.version}.associateBy {it.occurrenceKey}
        val identities=if(record.repeat==null) listOf(time.start.atZone(ZoneId.of(time.zoneId)).toInstant())
            else current.values.mapNotNull {it.occurrenceKey.substringAfterLast('@').toLongOrNull()?.let(Instant::ofEpochMilli)}
        return identities.distinct().mapNotNull {original ->
            val occurrence=OrganizerRecurrence.exact(record,overrides,original,zone) ?: return@mapNotNull null
            if(occurrence.completed || occurrence.start>=dayStart) return@mapNotNull null
            val receipt=current[occurrence.key]
            // A terminal receipt remains authoritative across an unrelated series edit.
            if(receipts.any {it.occurrenceKey==occurrence.key && it.state==DeliveryState.DISMISSED} ||
                receipt?.state==DeliveryState.CANCELLED) return@mapNotNull null
            OutstandingReminder(occurrence,receipt?.state ?: DeliveryState.PENDING,receipt?.snoozedUntil)
        }.sortedBy {it.snoozedUntil ?: it.occurrence.start}
    }
}
