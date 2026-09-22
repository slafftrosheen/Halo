package com.halo.host.android.capability

import com.halo.cognition.language.DayPartKind
import com.halo.cognition.language.DayRelation
import com.halo.cognition.language.TemporalValue
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCommandResolverTest {
    private val resolver = AlarmCommandResolver()

    @Test
    fun `time of day resolves to system alarm`() {
        val plan = resolver.resolve(TemporalValue.TimeOfDay(LocalTime.of(8, 0), DayRelation.TODAY))
        assertTrue(plan is AlarmExecutionPlan.SystemAlarm)
        val systemAlarm = plan as AlarmExecutionPlan.SystemAlarm
        assertEquals(8, systemAlarm.hour)
        assertEquals(0, systemAlarm.minute)
    }

    @Test
    fun `day part resolves to clarification`() {
        val plan = resolver.resolve(TemporalValue.DayPart(DayPartKind.EVENING, DayRelation.TOMORROW))
        assertTrue(plan is AlarmExecutionPlan.Clarification)
        val clarification = plan as AlarmExecutionPlan.Clarification
        assertEquals("What time in the evening?", clarification.prompt)
    }

    @Test
    fun `parsed string expression resolves correctly`() {
        val plan = resolver.resolve(null, "tomorrow at 7 am")
        assertTrue(plan is AlarmExecutionPlan.SystemAlarm)
        val systemAlarm = plan as AlarmExecutionPlan.SystemAlarm
        assertEquals(7, systemAlarm.hour)
        assertEquals(0, systemAlarm.minute)
    }

    @Test
    fun `ambiguous evening expression asks for clarification`() {
        val plan = resolver.resolve(null, "tomorrow evening")
        assertTrue(plan is AlarmExecutionPlan.Clarification)
        val clarification = plan as AlarmExecutionPlan.Clarification
        assertEquals("What time in the evening?", clarification.prompt)
    }
}
