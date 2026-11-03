package com.halo.runtime.cognition.routine

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class OrganizerRoutineStartTest {
    @Test fun dailyAndWeeklySchedulesNeverRunBeforeAnExplicitFutureStart() {
        val now = Instant.parse("2026-11-03T00:00:00Z")
        val start = Instant.parse("2026-12-15T07:30:00Z")
        val daily = RoutineTrigger.AtTime(LocalTime.of(7, 30), zoneId = ZoneOffset.UTC, notBefore = start)
        assertEquals(start, daily.nextOccurrence(now))
        assertEquals(start.plusSeconds(86400), daily.nextOccurrence(start))
        val weekly = daily.copy(days = setOf(DayOfWeek.SUNDAY))
        assertEquals(Instant.parse("2026-12-20T07:30:00Z"), weekly.nextOccurrence(now))
        assertEquals(Instant.parse("2026-11-03T07:30:00Z"), daily.copy(notBefore = null).nextOccurrence(now))
    }
    @Test fun futureStartRetainsZoneGapResolution() {
        val zone = ZoneId.of("Europe/Riga")
        val gapDay = LocalDate.of(2027, 3, 28)
        val start = gapDay.atStartOfDay(zone).toInstant()
        val trigger = RoutineTrigger.AtTime(LocalTime.of(3, 30), zoneId = zone, notBefore = start)
        assertEquals(gapDay.atTime(3, 30).atZone(zone).toInstant(), trigger.nextOccurrence(Instant.parse("2026-11-03T00:00:00Z")))
    }
}
