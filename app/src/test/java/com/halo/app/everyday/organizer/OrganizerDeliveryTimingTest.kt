package com.halo.app.everyday.organizer

import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class OrganizerDeliveryTimingTest {
    private val base = Instant.parse("2026-11-03T07:00:00Z")
    private val snoozeEnd = base.plusSeconds(10 * 60)
    private val record = OrganizerRecord(
        id = "wake",
        kind = OrganizerKind.ALARM,
        title = "Wake up",
        time = OrganizerTime(LocalDateTime.parse("2026-11-03T07:00"), zoneId = "UTC"),
        createdAt = base,
    )

    private fun receipt(state: DeliveryState = DeliveryState.PENDING) = DeliveryReceipt(
        occurrenceKey = "wake@${base.toEpochMilli()}",
        recordId = record.id,
        recordVersion = record.version,
        triggerAt = base, // Old trigger may survive a partial restore.
        state = state,
        updatedAt = base,
        snoozedUntil = snoozeEnd,
        snoozeElapsedDeadlineMs = 900_000L,
        snoozeBootId = "before-reboot",
    )

    @Test fun rebootFallsBackToPersistedSnoozeDeadlineNotOriginalTrigger() {
        val pending = receipt()
        assertEquals(-300_000L, OrganizerDeliveryTiming.latenessMillis(
            record, pending, base.plusSeconds(5 * 60), 1_000L, "after-reboot"))
        assertEquals(0L, OrganizerDeliveryTiming.latenessMillis(
            record, pending, snoozeEnd, 100_000L, "after-reboot"))
        assertEquals(120_000L, OrganizerDeliveryTiming.latenessMillis(
            record, pending, snoozeEnd.plusSeconds(120), 200_000L, "after-reboot"))
    }

    @Test fun sameBootKeepsMonotonicDeadlineAcrossWallClockChange() {
        val pending = receipt()
        assertEquals(-60_000L, OrganizerDeliveryTiming.latenessMillis(
            record, pending, base.plusSeconds(7200), 840_000L, "before-reboot"))
        assertEquals(10_000L, OrganizerDeliveryTiming.latenessMillis(
            record, pending, base.minusSeconds(3600), 910_000L, "before-reboot"))
    }

    @Test fun failedSnoozeIsStillScheduledBeforeItsActualDeadlineAfterReboot() {
        val failed = receipt(DeliveryState.FAILED)
        val now = base.plusSeconds(300)
        val plan = OrganizerDeliveryTiming.snoozedPlan(record, failed, now, 3_000L, "after-reboot")
        assertNotNull(plan)
        assertEquals(snoozeEnd, plan!!.triggerAt)
        assertEquals(failed.occurrenceKey, plan.occurrenceKey)
        assertTrue(plan.alarmClock)
        assertNull(OrganizerDeliveryTiming.snoozedPlan(
            record, failed, snoozeEnd, 5_000L, "after-reboot"))
    }

    @Test fun ordinaryUnsnoozedReceiptStillUsesItsTrigger() {
        val plain = receipt().copy(snoozedUntil = null, snoozeElapsedDeadlineMs = null, snoozeBootId = null)
        assertEquals(60_000L, OrganizerDeliveryTiming.latenessMillis(
            record, plain, base.plusSeconds(60), 1_000L, "after-reboot"))
    }
}
