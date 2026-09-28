@file:OptIn(ExperimentalCoroutinesApi::class)

package com.halo.app.study

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Review intensity decides how soon a card comes back, so a failed write must never be presented as
 * a saved preference: the learner would otherwise believe in a schedule that reverts on relaunch.
 */
class StudyReviewIntensityControllerTest {

    private class FakeStore(initial: StudyReviewIntensity = StudyReviewIntensity.DEFAULT) :
        StudyReviewIntensityStore {
        val value = MutableStateFlow(initial)
        var failNextWrite = false
        var writes = 0

        override val stored: Flow<StudyReviewIntensity> = value

        override suspend fun store(value: StudyReviewIntensity) {
            writes += 1
            if (failNextWrite) throw IllegalStateException("disk full")
            this.value.value = value
        }
    }

    @Test
    fun `an unknown stored name falls back to the default`() {
        assertEquals(StudyReviewIntensity.DEFAULT, StudyReviewIntensity.fromName(null))
        assertEquals(StudyReviewIntensity.DEFAULT, StudyReviewIntensity.fromName("SOMETHING_ELSE"))
        assertEquals(StudyReviewIntensity.STRICT, StudyReviewIntensity.fromName("strict"))
    }

    @Test
    fun `the controller mirrors what the store already holds`() = runTest(UnconfinedTestDispatcher()) {
        val store = FakeStore(StudyReviewIntensity.STRICT)
        val controller = StudyReviewIntensityController(store, backgroundScope)

        assertEquals(StudyReviewIntensity.STRICT, controller.intensity.first())
        assertEquals(StudyReviewIntensity.STRICT.desiredRetention, controller.currentRetention(), 1e-12)
    }

    @Test
    fun `selecting a new intensity persists it and publishes it`() = runTest(UnconfinedTestDispatcher()) {
        val store = FakeStore()
        val controller = StudyReviewIntensityController(store, backgroundScope)

        assertTrue(controller.select(StudyReviewIntensity.RELAXED))

        assertEquals(1, store.writes)
        assertEquals("RELAXED", store.value.value.name)
        assertEquals(StudyReviewIntensity.RELAXED, controller.current())
    }

    @Test
    fun `a late mirror emission from before a selection never overwrites it`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = FakeStore(StudyReviewIntensity.STANDARD)
            val controller = StudyReviewIntensityController(store, backgroundScope)

            // Simulate an emission that was read from the store before the local selection but is
            // delivered by the collector afterwards — the learner's choice must survive it.
            assertTrue(controller.select(StudyReviewIntensity.STRICT))
            store.value.value = StudyReviewIntensity.RELAXED

            assertEquals(StudyReviewIntensity.STRICT, controller.current())
        }

    @Test
    fun `a failed write is reported and leaves the previous value in place`() =
        runTest(UnconfinedTestDispatcher()) {
            val store = FakeStore(StudyReviewIntensity.STANDARD).apply { failNextWrite = true }
            val controller = StudyReviewIntensityController(store, backgroundScope)

            assertFalse(controller.select(StudyReviewIntensity.STRICT))
            assertEquals(StudyReviewIntensity.STANDARD, controller.current())
        }

    @Test
    fun `a store that goes away still yields the default rather than crashing`() =
        runTest(UnconfinedTestDispatcher()) {
            val controller = StudyReviewIntensityController(store = null, scope = backgroundScope)

            assertEquals(StudyReviewIntensity.DEFAULT, controller.intensity.first())
            assertFalse(controller.select(StudyReviewIntensity.STRICT))
        }
}
