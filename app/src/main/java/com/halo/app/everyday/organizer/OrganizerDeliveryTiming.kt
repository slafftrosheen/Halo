package com.halo.app.everyday.organizer

import java.time.Duration
import java.time.Instant

/** Timers and snoozes keep elapsed-time deadlines when the user changes the wall clock. */
object OrganizerDeliveryTiming {
    fun latenessMillis(record: OrganizerRecord, receipt: DeliveryReceipt, now: Instant, elapsedMs: Long, bootId: String): Long {
        val deadline = when {
            receipt.snoozeBootId == bootId && receipt.snoozeElapsedDeadlineMs != null -> receipt.snoozeElapsedDeadlineMs
            receipt.snoozedUntil != null -> null // A reboot uses the persisted wall deadline.
            record.timer?.bootId == bootId && record.timer.elapsedStartMs != null ->
                record.timer.elapsedStartMs + record.timer.remainingMs
            else -> null
        }
        // After a reboot the prior boot's elapsed deadline is unusable. The persisted
        // snooze wall time, not a potentially stale original trigger, is authoritative.
        val wallDeadline = receipt.snoozedUntil ?: receipt.triggerAt
        return if (deadline != null) elapsedMs - deadline else Duration.between(wallDeadline, now).toMillis()
    }

    fun snoozedPlan(record: OrganizerRecord, receipt: DeliveryReceipt, now: Instant, elapsedMs: Long, bootId: String): PlannedDelivery? {
        if (receipt.recordVersion != record.version || receipt.snoozedUntil == null || receipt.state in consumed) return null
        if (receipt.state in setOf(DeliveryState.FAILED, DeliveryState.NOTIFICATIONS_BLOCKED) &&
            latenessMillis(record, receipt, now, elapsedMs, bootId) >= 0) return null
        val monotonic = receipt.snoozeElapsedDeadlineMs?.takeIf { receipt.snoozeBootId == bootId }
        val at = monotonic?.let { now.plusMillis((it - elapsedMs).coerceAtLeast(0)) } ?: receipt.snoozedUntil
        return PlannedDelivery(receipt.occurrenceKey, record.id, record.version, at,
            record.kind in setOf(OrganizerKind.ALARM, OrganizerKind.TIMER, OrganizerKind.REMINDER),
            record.kind == OrganizerKind.ALARM && monotonic == null, monotonic)
    }
    private val consumed = setOf(DeliveryState.FIRED, DeliveryState.DISMISSED, DeliveryState.MISSED, DeliveryState.CANCELLED)
}
