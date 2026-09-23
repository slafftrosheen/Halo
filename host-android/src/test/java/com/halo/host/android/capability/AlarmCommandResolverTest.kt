package com.halo.host.android.capability

import com.halo.cognition.language.DayPartKind
import com.halo.cognition.language.DayRelation
import com.halo.cognition.language.TemporalValue
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCommandResolverTest {
    private val zoneId = ZoneId.of("UTC")
    private val fixedClock = Clock.fixed(Instant.parse("2026-09-22T18:00:00Z"), zoneId)
    private val resolver = AlarmCommandResolver(fixedClock, zoneId)

    @Test
    fun `time of day today 20_00 resolves to system alarm`() {
        val plan = resolver.resolve(TemporalValue.TimeOfDay(LocalTime.of(20, 0), DayRelation.TODAY))
        assertTrue(plan is AlarmExecutionPlan.SystemAlarm)
        val systemAlarm = plan as AlarmExecutionPlan.SystemAlarm
        assertEquals(20, systemAlarm.hour)
        assertEquals(0, systemAlarm.minute)
    }

    @Test
    fun `time of day tomorrow at 8 am resolves to Halo exact alarm if not standard next occurrence`() {
        // At 18:00, standard next 8am is tomorrow 8am. So tomorrow 8am is system alarm.
        val plan = resolver.resolve(TemporalValue.TimeOfDay(LocalTime.of(8, 0), DayRelation.TOMORROW))
        assertTrue(plan is AlarmExecutionPlan.SystemAlarm)
    }

    @Test
    fun `time of day today at 8 am when now is 18_00 resolves to Halo exact alarm for past time today`() {
        // Requested 08:00 TODAY, but now is 18:00 TODAY -> exact alarm in past (or exact occurrence).
        val plan = resolver.resolve(TemporalValue.TimeOfDay(LocalTime.of(8, 0), DayRelation.TODAY))
        assertTrue(plan is AlarmExecutionPlan.HaloExactAlarm)
        val exact = plan as AlarmExecutionPlan.HaloExactAlarm
        assertEquals(Instant.parse("2026-09-22T08:00:00Z"), exact.triggerAt)
    }

    @Test
    fun `day part resolves to clarification`() {
        val plan = resolver.resolve(TemporalValue.DayPart(DayPartKind.EVENING, DayRelation.TOMORROW))
        assertTrue(plan is AlarmExecutionPlan.Clarification)
        val clarification = plan as AlarmExecutionPlan.Clarification
        assertEquals("What time in the evening?", clarification.prompt)
    }

    @Test
    fun `ambiguous evening expression asks for clarification`() {
        val plan = resolver.resolve(null, "tomorrow evening")
        assertTrue(plan is AlarmExecutionPlan.Clarification)
        val clarification = plan as AlarmExecutionPlan.Clarification
        assertEquals("What time in the evening?", clarification.prompt)
    }
}
