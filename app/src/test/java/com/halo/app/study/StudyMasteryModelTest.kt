package com.halo.app.study

import com.halo.app.data.study.StudyReviewEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The memory model is what every mastery number on screen is derived from, so these tests pin the
 * invariants that keep the display, the schedule and the drawn curve telling the same story.
 */
class StudyMasteryModelTest {
    private val day = StudyMasteryModel.DAY_MS
    private val now = 100L * day

    private fun event(
        at: Long,
        known: Boolean,
        index: Int = 0,
    ) = StudyReviewEvent(
        id = "event-$at-$known",
        itemId = "item",
        sessionId = "session",
        known = known,
        previousIntervalIndex = index,
        nextIntervalIndex = index,
        nextDueAtEpochMs = at,
        reviewedAtEpochMs = at,
    )

    private fun snapshot(
        events: List<StudyReviewEvent>,
        retention: Double = StudyMasteryModel.DEFAULT_DESIRED_RETENTION,
        ladderDueAt: Long? = null,
    ) = StudyMasteryModel.snapshot(
        events = events,
        now = now,
        desiredRetention = retention,
        ladderDueAtEpochMs = ladderDueAt,
    )

    @Test
    fun `an untouched card is not measured`() {
        val snapshot = snapshot(emptyList(), ladderDueAt = now + day)

        assertFalse(snapshot.isMeasured)
        assertEquals(0.0, snapshot.stabilityDays, 0.0)
        assertEquals("New", snapshot.statusLabel(now))
        assertEquals(now + day, snapshot.dueAtEpochMs)
    }

    @Test
    fun `a failed recall lowers stability compared with a successful one`() {
        val remembered = snapshot(listOf(event(now - day, known = true)))
        val forgotten = snapshot(listOf(event(now - day, known = false)))

        assertTrue(forgotten.stabilityDays < remembered.stabilityDays)
        assertTrue(forgotten.masteredProbability < remembered.masteredProbability)
    }

    @Test
    fun `a repeated success raises stability and mastery without bound drift`() {
        val once = snapshot(listOf(event(now - 3 * day, known = true)))
        val twice = snapshot(listOf(event(now - 3 * day, true), event(now - day, true)))

        assertTrue(twice.stabilityDays > once.stabilityDays)
        assertTrue(twice.masteredProbability > once.masteredProbability)
        assertEquals(2, twice.reviewCount)
    }

    @Test
    fun `the retention target moves the due date and never the measured mastery`() {
        val events = listOf(event(now - 2 * day, true), event(now - day, true))

        val relaxed = snapshot(events, retention = StudyReviewIntensity.RELAXED.desiredRetention)
        val standard = snapshot(events, retention = StudyReviewIntensity.STANDARD.desiredRetention)
        val strict = snapshot(events, retention = StudyReviewIntensity.STRICT.desiredRetention)

        assertEquals(relaxed.masteredProbability, strict.masteredProbability, 1e-12)
        assertTrue(relaxed.dueAtEpochMs > standard.dueAtEpochMs)
        assertTrue(standard.dueAtEpochMs > strict.dueAtEpochMs)
    }

    @Test
    fun `the due date is where recall probability reaches the chosen target`() {
        StudyReviewIntensity.entries.forEach { intensity ->
            val events = listOf(event(now - 4 * day, true), event(now - day, true))
            val snapshot = snapshot(events, retention = intensity.desiredRetention)
            val atDue = StudyMasteryCurve.retrievabilityAt(
                StudyMasteryModel.reviewPoints(events),
                snapshot.dueAtEpochMs,
            )

            assertEquals(intensity.desiredRetention, atDue, 0.005)
        }
    }

    @Test
    fun `recall probability falls monotonically with time since the last review`() {
        val points = StudyMasteryModel.reviewPoints(listOf(event(now - 10 * day, true), event(now, true)))

        var previous = Double.MAX_VALUE
        (0..30).forEach { offset ->
            val value = StudyMasteryCurve.retrievabilityAt(points, now + offset * day)
            assertTrue("probability must not rise at day $offset", value <= previous + 1e-12)
            assertTrue(value in 0.0..1.0)
            previous = value
        }
    }

    @Test
    fun `nothing is claimed before the first review`() {
        val points = StudyMasteryModel.reviewPoints(listOf(event(now, true)))

        assertEquals(0.0, StudyMasteryCurve.retrievabilityAt(points, now - 5 * day), 0.0)
    }

    @Test
    fun `a card without history draws no curve`() {
        assertNull(StudyMasteryCurve.cardCurve(emptyList(), dueAtEpochMs = now, now = now))
    }

    @Test
    fun `the curve covers the due date and stays inside the chart`() {
        val history = listOf(event(now - 6 * day, true), event(now - 2 * day, true))
        val curve = StudyMasteryCurve.cardCurve(history, dueAtEpochMs = now + 3 * day, now = now)

        assertNotNull(curve)
        requireNotNull(curve)
        assertEquals(2, curve.reviewCount)
        assertTrue(curve.points.size >= 2)
        assertTrue(curve.points.first().atEpochMs >= curve.fromEpochMs)
        assertTrue(curve.points.last().atEpochMs >= curve.toEpochMs)
        assertTrue(curve.points.all { it.retrievability in 0.0..1.0 })
        assertTrue(curve.dueAtEpochMs in curve.fromEpochMs..curve.toEpochMs)
        val fraction = requireNotNull(curve.dueFraction)
        assertTrue(fraction in 0f..1f)
    }

    @Test
    fun `an overdue card keeps its fall inside the window`() {
        val history = listOf(event(now - 40 * day, true))
        val curve = StudyMasteryCurve.cardCurve(history, dueAtEpochMs = now - 20 * day, now = now)

        requireNotNull(curve)
        assertEquals(now, curve.toEpochMs)
        assertTrue(curve.points.last().retrievability < curve.points.first().retrievability)
    }
}
