package com.halo.app.everyday.organizer

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class OrganizerRecurrenceTest {
    private val riga = ZoneId.of("Europe/Riga")
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private fun record(time: OrganizerTime, rule: RepeatRule? = null) = OrganizerRecord("meeting", OrganizerKind.EVENT,
        "Review", time = time, repeat = rule, createdAt = now)

    @Test fun recurringAlarmKeepsLocalTimeAcrossSpringAndAutumnOffsetChanges() {
        val spring = OrganizerTime(LocalDateTime.parse("2026-03-28T07:00"), zoneId = riga.id)
        val starts = OrganizerRecurrence.starts(spring, RepeatRule(RepeatFrequency.DAILY, count = 3), riga).toList()
        assertEquals(listOf(7, 7, 7), starts.map { it.atZone(riga).hour })
        assertEquals(23, Duration.between(starts[0], starts[1]).toHours())
        val autumn = spring.copy(start = LocalDateTime.parse("2026-10-24T07:00"))
        val fall = OrganizerRecurrence.starts(autumn, RepeatRule(RepeatFrequency.DAILY, count = 3), riga).toList()
        assertEquals(25, Duration.between(fall[0], fall[1]).toHours())
    }
    @Test fun gapMovesForwardAndOverlapFiresOnceWithEarlierOffset() {
        val gap = OrganizerTime(LocalDateTime.parse("2026-03-29T03:30"), zoneId = riga.id)
        assertEquals(4, OrganizerRecurrence.starts(gap, null, riga).single().atZone(riga).hour)
        val overlap = gap.copy(start = LocalDateTime.parse("2026-10-25T03:30"))
        val values = OrganizerRecurrence.starts(overlap, RepeatRule(RepeatFrequency.DAILY, count = 2), riga).toList()
        assertEquals(2, values.size)
        assertEquals(ZoneOffset.ofHours(3), values.first().atZone(riga).offset)
    }
    @Test fun monthEndAndLeapDaySkipInvalidDatesWithoutConsumingCount() {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-31T10:00"), zoneId = "UTC")
        assertEquals(listOf(1, 3, 5), OrganizerRecurrence.starts(time, RepeatRule(RepeatFrequency.MONTHLY, count = 3), riga)
            .map { it.atZone(ZoneOffset.UTC).monthValue }.toList())
        val leap = time.copy(start = LocalDateTime.parse("2024-02-29T10:00"))
        assertEquals(listOf(2024, 2028, 2032), OrganizerRecurrence.starts(leap, RepeatRule(RepeatFrequency.YEARLY, count = 3), riga)
            .map { it.atZone(ZoneOffset.UTC).year }.toList())
    }
    @Test fun weekdayIntervalAndUntilAreInclusiveWithoutDuplicatingAnchor() {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-05T09:00"), zoneId = "UTC")
        val rule = RepeatRule(RepeatFrequency.WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            until = Instant.parse("2026-01-21T09:00:00Z"))
        assertEquals(listOf(5, 7, 19, 21), OrganizerRecurrence.starts(time, rule, riga).map { it.atZone(ZoneOffset.UTC).dayOfMonth }.toList())
    }
    @Test fun allDaySpansLocalMidnightsAndTimedEventKeepsActualDuration() {
        val time = OrganizerTime(LocalDateTime.parse("2026-03-29T00:00"), LocalDateTime.parse("2026-03-30T00:00"), riga.id, allDay = true)
        val allDay = OrganizerRecurrence.between(record(time), emptyList(), Instant.parse("2026-03-28T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"), riga).single()
        assertEquals(23, Duration.between(allDay.start, allDay.end).toHours())
        val timed = time.copy(start = LocalDateTime.parse("2026-03-29T02:00"), end = LocalDateTime.parse("2026-03-29T04:00"), allDay = false)
        val value = OrganizerRecurrence.between(record(timed), emptyList(), allDay.start, allDay.end!!, riga).single()
        assertEquals(1, Duration.between(value.start, value.end).toHours())
    }
    @Test fun movedOccurrenceKeepsIdentityAndCanMoveIntoAnEarlierWindow() {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-05T10:00"), LocalDateTime.parse("2026-01-05T11:00"), "UTC")
        val record = record(time, RepeatRule(RepeatFrequency.DAILY, count = 3))
        val original = Instant.parse("2026-01-07T10:00:00Z")
        val replacement = time.copy(start = LocalDateTime.parse("2026-01-04T10:00"), end = LocalDateTime.parse("2026-01-04T11:00"))
        val exception = OccurrenceOverride(record.id, original, OccurrenceDisposition.REPLACED, replacement, "Moved review")
        val rows = OrganizerRecurrence.between(record, listOf(exception), Instant.parse("2026-01-04T00:00:00Z"), Instant.parse("2026-01-05T00:00:00Z"), riga)
        assertEquals(1, rows.size); assertEquals(original, rows.single().originalStart); assertEquals("Moved review", rows.single().record.title)
        assertTrue(OrganizerRecurrence.between(record, listOf(exception), Instant.parse("2026-01-07T00:00:00Z"), Instant.parse("2026-01-08T00:00:00Z"), riga).isEmpty())
    }
    @Test fun adjacentEventsDoNotConflictButOverlapsAreSymmetric() {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-05T10:00"), LocalDateTime.parse("2026-01-05T11:00"), "UTC")
        val first = OrganizerOccurrence(record(time), now, now, now.plusSeconds(3600))
        val adjacent = first.copy(record = first.record.copy(id = "adjacent"), start = now.plusSeconds(3600), end = now.plusSeconds(7200))
        val overlap = first.copy(record = first.record.copy(id = "overlap"), start = now.plusSeconds(1800))
        val conflicts = OrganizerRecurrence.conflicts(listOf(first, adjacent, overlap))
        assertEquals(setOf(overlap.key), conflicts[first.key]); assertEquals(setOf(first.key), conflicts[overlap.key]); assertNull(conflicts[adjacent.key])
    }
    @Test fun timerUsesMonotonicTimeWithinBootAndWallDeadlineAfterReboot() {
        val timer = TimerState(60000, 60000).start(now, 1000, "boot1")
        assertEquals(40000, timer.remaining(now.plusSeconds(3600), 21000, "boot1"))
        assertEquals(30000, timer.remaining(now.plusSeconds(30), 100, "boot2"))
        val paused = timer.pause(now, 21000, "boot1")
        assertEquals(40000, paused.remaining(now.plusSeconds(3600), 100000, "boot1"))
        assertEquals(0, timer.remaining(now.plusSeconds(1000), 200, "boot2"))
    }
    @Test fun plannerSkipsDeliveredCompletedAndCancelledOccurrencesAfterRestart() {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-01T07:00"), zoneId = "UTC")
        val alarm = OrganizerRecord("alarm", OrganizerKind.ALARM, "Wake", time = time,
            repeat = RepeatRule(RepeatFrequency.DAILY, count = 3), createdAt = now)
        val first = time.instant(riga)
        val receipt = DeliveryReceipt("alarm@${first.toEpochMilli()}", "alarm", 1, first, DeliveryState.FIRED, first)
        val exception = OccurrenceOverride("alarm", first.plusSeconds(86400), OccurrenceDisposition.CANCELLED)
        val plan = OrganizerSchedulePlanner.next(alarm.copy(version = 2), listOf(exception), listOf(receipt), now, riga, 0, "boot")!!
        assertEquals(first.plusSeconds(2 * 86400), plan.triggerAt)
        assertTrue(plan.alarmClock); assertEquals(2L, plan.recordVersion)
        assertNull(OrganizerSchedulePlanner.next(alarm.copy(deletedAt = now), emptyList(), emptyList(), now, riga, 0, "boot"))
    }
    @Test fun floatingAlarmFollowsDeviceZoneWhileEventRetainsChosenZone() {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-05T07:00"), zoneId = "Europe/Riga")
        val london = ZoneId.of("Europe/London")
        assertEquals(Instant.parse("2026-01-05T05:00:00Z"), time.instant(london))
        assertEquals(Instant.parse("2026-01-05T07:00:00Z"), time.copy(followDeviceZone = true).instant(london))
    }
    @Test fun floatingCancellationAndMovedCompletionSurviveDeviceZoneChange() {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-05T07:00"), zoneId = riga.id, followDeviceZone = true)
        val record = OrganizerRecord("alarm", OrganizerKind.ALARM, "Wake", time = time,
            repeat = RepeatRule(RepeatFrequency.DAILY, count = 2), createdAt = now)
        val identity = time.instant(riga)
        val exception = OccurrenceOverride(record.id, identity, OccurrenceDisposition.CANCELLED)
        val london = ZoneId.of("Europe/London")
        val rows = OrganizerRecurrence.between(record, listOf(exception), now, now.plusSeconds(10 * 86400), london)
        assertEquals(1, rows.size); assertEquals(Instant.parse("2026-01-06T07:00:00Z"), rows.single().start)
        assertEquals(Instant.parse("2026-01-06T05:00:00Z"), rows.single().originalStart)
        val planned = OrganizerSchedulePlanner.next(record, listOf(exception), emptyList(), now, london, 0, "boot")!!
        assertEquals(rows.single().key, planned.occurrenceKey); assertEquals(rows.single().start, planned.triggerAt)
        assertTrue(OrganizerRecurrence.contains(record, identity, london))
    }
    @Test fun cancelledReceiptDoesNotSuppressRestoredFutureAlarmButFiredReceiptDoes() {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-05T07:00"), zoneId = "UTC")
        val record = OrganizerRecord("alarm", OrganizerKind.ALARM, "Wake", time = time, version = 3, createdAt = now)
        val start = time.instant(riga)
        val receipt = DeliveryReceipt("alarm@${start.toEpochMilli()}", "alarm", 2, start, DeliveryState.CANCELLED, now)
        assertNotNull(OrganizerSchedulePlanner.next(record, emptyList(), listOf(receipt), now, riga, 0, "boot"))
        assertNull(OrganizerSchedulePlanner.next(record, emptyList(), listOf(receipt.copy(state = DeliveryState.FIRED)), now, riga, 0, "boot"))
    }
    @Test fun daylightGapCannotProduceAnEndBeforeTheResolvedStart() {
        val time = OrganizerTime(LocalDateTime.parse("2026-03-29T03:30"), LocalDateTime.parse("2026-03-29T04:00"), riga.id)
        assertThrows(IllegalArgumentException::class.java) { time.validate() }
        time.copy(end = LocalDateTime.parse("2026-03-29T05:00")).validate()
    }
    @Test fun floatingReplacementKeepsAuthoredPositiveDurationAfterTravel() {
        val time = OrganizerTime(LocalDateTime.parse("2026-03-28T03:30"), LocalDateTime.parse("2026-03-28T04:00"), "UTC", followDeviceZone = true)
        val record = record(time, RepeatRule(RepeatFrequency.DAILY, count = 2))
        val replacement = time.copy(start = time.start.plusDays(1), end = time.end!!.plusDays(1))
        val original = time.start.toInstant(ZoneOffset.UTC)
        val exception = OccurrenceOverride(record.id, original, OccurrenceDisposition.REPLACED, replacement)
        val moved = OrganizerRecurrence.between(record, listOf(exception), Instant.parse("2026-03-29T00:00:00Z"), Instant.parse("2026-03-30T00:00:00Z"), riga)
            .single { it.originalStart == original }
        assertEquals(4, moved.start.atZone(riga).hour); assertEquals(5, moved.end!!.atZone(riga).hour)
        assertEquals(30, Duration.between(moved.start, moved.end).toMinutes())
    }
    @Test fun restoredTimerReschedulesCancelledRunAndNewRunCannotCollideWithAnOldRun() {
        val timer = TimerState(60000, 60000).start(now, 0, "boot")
        val record = OrganizerRecord("timer", OrganizerKind.TIMER, "Tea", timer = timer, version = 3, createdAt = now)
        val key = "timer@timer:${timer.runId}"
        val old = DeliveryReceipt(key, "timer", 2, now.plusSeconds(60), DeliveryState.CANCELLED, now)
        assertNotNull(OrganizerSchedulePlanner.next(record, emptyList(), listOf(old), now, riga, 0, "boot"))
        assertNull(OrganizerSchedulePlanner.next(record, emptyList(), listOf(old.copy(state = DeliveryState.FIRED)), now, riga, 0, "boot"))
        val newRun = record.copy(timer = TimerState(60000, 60000).start(now, 0, "boot"))
        assertNotEquals(timer.runId, newRun.timer!!.runId)
        assertNotNull(OrganizerSchedulePlanner.next(newRun, emptyList(), listOf(old.copy(state = DeliveryState.FIRED)), now, riga, 0, "boot"))
    }
}
