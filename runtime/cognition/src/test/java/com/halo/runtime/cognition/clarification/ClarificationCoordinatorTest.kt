package com.halo.runtime.cognition.clarification

import com.halo.cognition.language.DayPartKind
import com.halo.cognition.language.DayRelation
import com.halo.cognition.language.InteractionId
import com.halo.cognition.language.SetAlarmIntent
import com.halo.cognition.language.TemporalValue
import com.halo.cognition.language.TimeEntity
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClarificationCoordinatorTest {
    private val coordinator = DefaultClarificationCoordinator()

    @Test
    fun `pending clarification resolved with time input`() = runBlocking {
        val now = Instant.now()
        val pending = PendingClarification(
            id = "clarification_1",
            interactionId = InteractionId("txn_123"),
            intent = SetAlarmIntent,
            field = "time",
            entities = listOf(TimeEntity(TemporalValue.DayPart(DayPartKind.EVENING, DayRelation.TOMORROW), "tomorrow evening", 0.99f)),
            originalTranscript = "set an alarm tomorrow evening",
            createdAt = now,
            expiresAt = now.plusSeconds(90)
        )

        coordinator.request(pending)
        assertEquals(pending, coordinator.pending.value)

        val resolution = coordinator.consume("8 PM", now.plusSeconds(5))
        assertTrue(resolution is ClarificationResolution.Resolved)

        val resolved = resolution as ClarificationResolution.Resolved
        assertEquals(SetAlarmIntent, resolved.intent)
        val timeEntity = resolved.entities.filterIsInstance<TimeEntity>().first()
        val timeOfDay = timeEntity.value as TemporalValue.TimeOfDay
        assertEquals(LocalTime.of(20, 0), timeOfDay.time)
        assertNull(coordinator.pending.value)
    }

    @Test
    fun `expired clarification is discarded`() = runBlocking {
        val now = Instant.now()
        val pending = PendingClarification(
            id = "clarification_1",
            interactionId = InteractionId("txn_123"),
            intent = SetAlarmIntent,
            field = "time",
            entities = emptyList(),
            originalTranscript = "set an alarm tomorrow evening",
            createdAt = now,
            expiresAt = now.plusSeconds(60)
        )

        coordinator.request(pending)
        val resolution = coordinator.consume("8 PM", now.plusSeconds(61))
        assertNull(resolution)
        assertNull(coordinator.pending.value)
    }
    @Test fun `evening fills ambiguous hour retaining interaction and unrelated input stays pending`() = runBlocking {
        val now = Instant.now()
        val pending = PendingClarification("clarification", InteractionId("original"), SetAlarmIntent, "time",
            listOf(com.halo.cognition.language.UnresolvedTimeEntity("8", com.halo.cognition.language.TemporalField.AM_PM, 1f)),
            "Set an alarm for 8", now, now.plusSeconds(60))
        coordinator.request(pending)
        assertEquals(ClarificationResolution.Unresolved, coordinator.consume("weather tomorrow", now))
        assertEquals(pending, coordinator.pending.value)
        val result = coordinator.consume("Evening", now) as ClarificationResolution.Resolved
        assertEquals(InteractionId("original"), result.interactionId)
        assertEquals(LocalTime.of(20, 0), (result.entities.filterIsInstance<TimeEntity>().single().value as TemporalValue.TimeOfDay).time)
        assertNull(coordinator.pending.value)
        coordinator.request(pending)
        coordinator.consume("cancel", now)
        assertNull(coordinator.pending.value)
    }
}
