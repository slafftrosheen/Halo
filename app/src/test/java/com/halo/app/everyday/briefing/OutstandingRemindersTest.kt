package com.halo.app.everyday.briefing

import com.halo.app.everyday.organizer.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class OutstandingRemindersTest {
    private val zone=ZoneId.of("Europe/Riga")
    private val start=LocalDate.of(2026,11,3).atStartOfDay(zone).toInstant()
    private val record=OrganizerRecord("r",OrganizerKind.REMINDER,"Remember",time=OrganizerTime(LocalDateTime.of(2026,11,2,9,0),zoneId=zone.id),
        repeat=RepeatRule(RepeatFrequency.DAILY,count=4),createdAt=start.minusSeconds(86400))
    private val original=record.time!!.instant(zone)
    private fun receipt(state: DeliveryState)=DeliveryReceipt("r@${original.toEpochMilli()}","r",record.version,original,state,start)
    @Test fun recurringOutstandingNeedsARealReceiptAndRespectsSnoozeAndOccurrenceCancellation() {
        assertTrue(OutstandingReminders.project(record,emptyList(),emptyList(),start,zone).isEmpty())
        val snooze=receipt(DeliveryState.EXACT).copy(snoozedUntil=start.plusSeconds(3600))
        val result=OutstandingReminders.project(record,emptyList(),listOf(snooze),start,zone).single()
        assertEquals(snooze.snoozedUntil,result.snoozedUntil)
        assertEquals(original,result.occurrence.originalStart)
        for(disposition in listOf(OccurrenceDisposition.CANCELLED,OccurrenceDisposition.COMPLETED)) {
            assertTrue(OutstandingReminders.project(record,listOf(OccurrenceOverride(record.id,original,disposition)),listOf(snooze),start,zone).isEmpty())
        }
        assertTrue(OutstandingReminders.project(record,emptyList(),listOf(receipt(DeliveryState.DISMISSED)),start,zone).isEmpty())
        assertTrue(OutstandingReminders.project(record.copy(version=2),emptyList(),listOf(receipt(DeliveryState.FAILED)),start,zone).isEmpty())
    }
    @Test fun overdueOneOffWithoutDeliveryIsPendingAndCompletedRecordIsAbsent() {
        val once=record.copy(repeat=null)
        assertEquals(DeliveryState.PENDING,OutstandingReminders.project(once,emptyList(),emptyList(),start,zone).single().state)
        assertTrue(OutstandingReminders.project(once.copy(status=OrganizerStatus.COMPLETED),emptyList(),emptyList(),start,zone).isEmpty())
    }
}
