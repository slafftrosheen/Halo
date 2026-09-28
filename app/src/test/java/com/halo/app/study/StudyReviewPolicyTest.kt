package com.halo.app.study

import com.halo.app.data.study.StudyPracticeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ladder is the drill rhythm for real mistakes. It is deliberately independent of the memory
 * model's retention target, and these tests pin both that behaviour and its two jump rules.
 */
class StudyReviewPolicyTest {
    private val day = StudyMasteryModel.DAY_MS
    private val at = 1_000L * day
    private val policy = StudyReviewPolicy()

    private fun item(
        intervalIndex: Int,
        knownStreak: Int = 0,
        wrongStreak: Int = 0,
        mastered: Boolean = false,
    ) = StudyPracticeItem(
        id = "card",
        sessionId = "session",
        question = "What is the derivative of x^2?",
        answer = "2x",
        sourceObjectId = null,
        sourceRevision = null,
        accepted = true,
        intervalIndex = intervalIndex,
        dueAtEpochMs = at,
        reviewCount = 0,
        knownStreak = knownStreak,
        wrongStreak = wrongStreak,
        mastered = mastered,
        userEdited = false,
        createdAtEpochMs = at - day,
        lastReviewedAtEpochMs = null,
    )

    @Test
    fun `a remembered card moves one rung up`() {
        val decision = policy.schedule(item(intervalIndex = 0), known = true, atEpochMs = at)

        assertEquals(1, decision.ladderStep)
        assertEquals(2, decision.intervalDays)
        assertEquals(at + 2 * day, decision.dueAtEpochMs)
        assertEquals(1, decision.knownStreak)
        assertEquals(0, decision.wrongStreak)
        assertFalse(decision.mastered)
    }

    @Test
    fun `two remembered cards in a row skip a rung`() {
        val decision = policy.schedule(
            item(intervalIndex = 1, knownStreak = 1),
            known = true,
            atEpochMs = at,
        )

        assertEquals(3, decision.ladderStep)
        assertEquals(7, decision.intervalDays)
        assertFalse(decision.mastered)
    }

    @Test
    fun `a forgotten card drops one rung`() {
        val decision = policy.schedule(item(intervalIndex = 3), known = false, atEpochMs = at)

        assertEquals(2, decision.ladderStep)
        assertEquals(4, decision.intervalDays)
        assertEquals(1, decision.wrongStreak)
    }

    @Test
    fun `two forgotten reviews reset the ladder`() {
        val decision = policy.schedule(
            item(intervalIndex = 4, wrongStreak = 1),
            known = false,
            atEpochMs = at,
        )

        assertEquals(0, decision.ladderStep)
        assertEquals(1, decision.intervalDays)
        assertEquals(2, decision.wrongStreak)
    }

    @Test
    fun `the last rung is mastered and kept on maintenance`() {
        val decision = policy.schedule(
            item(intervalIndex = StudyReviewPolicy.DEFAULT_STEPS_DAYS.lastIndex),
            known = true,
            atEpochMs = at,
        )

        assertTrue(decision.mastered)
        assertTrue(decision.intervalDays >= 30)
        assertEquals(at + decision.intervalDays * day, decision.dueAtEpochMs)
    }

    @Test
    fun `the ladder ignores the memory model's retention target`() {
        val relaxed = StudyReviewPolicy(desiredRetention = StudyReviewIntensity.RELAXED.desiredRetention)
        val strict = StudyReviewPolicy(desiredRetention = StudyReviewIntensity.STRICT.desiredRetention)

        StudyReviewPolicy.DEFAULT_STEPS_DAYS.indices.forEach { index ->
            val card = item(intervalIndex = index)
            assertEquals(
                relaxed.schedule(card, known = true, atEpochMs = at),
                strict.schedule(card, known = true, atEpochMs = at),
            )
        }
    }

    @Test
    fun `the ladder never schedules in the past`() {
        val card = item(intervalIndex = 0)
        val decision = policy.schedule(card, known = false, atEpochMs = at)

        assertTrue(decision.dueAtEpochMs > at)
        assertTrue(decision.intervalDays >= StudyReviewPolicy.DEFAULT_STEPS_DAYS.first())
    }
}
