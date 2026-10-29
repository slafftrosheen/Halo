package com.halo.app.ui.theme

import androidx.compose.runtime.mutableFloatStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HaloAmbientMotionTest {
    @Test fun bothMotifsKeepTheOriginalAmplitudeWithOppositeParallax() {
        for (phase in listOf(0f, .7f, 1.5f, 3f, 5f)) {
            val first = haloAmbientDrift(phase, 360f, 640f, 0)
            val second = haloAmbientDrift(phase, 360f, 640f, 1)
            assertEquals(-first.x, second.x, .0001f)
            assertEquals(-first.y, second.y, .0001f)
            assertTrue(abs(first.x) <= 360f * .018f)
            assertTrue(abs(first.y) <= 640f * .012f)
        }
        assertEquals(640f * .012f, haloAmbientDrift(0f, 360f, 640f, 0).y, .0001f)
    }

    @Test fun everyExistingMotionGateCanIndependentlyStopDrift() {
        for (style in listOf(HaloBackgroundStyle.HALO, HaloBackgroundStyle.MIST)) {
            assertTrue(haloAmbientMotionAllowed(true, false, false, true, style))
            assertFalse(haloAmbientMotionAllowed(false, false, false, true, style))
            assertFalse(haloAmbientMotionAllowed(true, true, false, true, style))
            assertFalse(haloAmbientMotionAllowed(true, false, true, true, style))
            assertFalse(haloAmbientMotionAllowed(true, false, false, false, style))
        }
        assertFalse(haloAmbientMotionAllowed(true, false, false, true, HaloBackgroundStyle.STILL))
    }

    @Test fun phaseTicksOnlyWhileResumedAndRestartsWithoutResettingItsPosition() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val phase = mutableFloatStateOf(0f)
        val job = launch { advanceHaloAmbientPhase(owner.lifecycle, true, phase) }
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent(); advanceTimeBy(500); runCurrent()
            assertEquals(0f, phase.floatValue, .00001f)
            owner.registry.currentState = Lifecycle.State.RESUMED
            runCurrent(); advanceTimeBy(999); runCurrent()
            assertEquals("No decorative update before one second", 0f, phase.floatValue, .00001f)
            advanceTimeBy(1); runCurrent()
            assertEquals("Preserve the nominal .03 radians per second", .03f, phase.floatValue, .00001f)
            advanceTimeBy(1_000); runCurrent()
            assertEquals(.06f, phase.floatValue, .00001f)
            // A partial interval before pausing must not cause an early catch-up tick.
            advanceTimeBy(250); runCurrent()
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent(); advanceTimeBy(1_000); runCurrent()
            assertEquals(.06f, phase.floatValue, .00001f)
            owner.registry.currentState = Lifecycle.State.RESUMED
            runCurrent(); advanceTimeBy(999); runCurrent()
            assertEquals("Resume preserves position without catching up hidden time", .06f, phase.floatValue, .00001f)
            advanceTimeBy(1); runCurrent()
            assertEquals(.09f, phase.floatValue, .00001f)
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent(); advanceTimeBy(1_000); runCurrent()
            assertEquals(.09f, phase.floatValue, .00001f)
            assertTrue(job.isCompleted)
        } finally {
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test fun oneSecondOfMotionPreservesBothMotifsDisplacementFromAnExistingPosition() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        val phase = mutableFloatStateOf(.7f)
        val job = launch { advanceHaloAmbientPhase(owner.lifecycle, true, phase) }
        try {
            owner.registry.currentState = Lifecycle.State.RESUMED
            runCurrent(); advanceTimeBy(1_000); runCurrent()
            // Expected position comes from the unchanged .03rad/sec motion contract.
            for (motif in 0..1) {
                val expected = haloAmbientDrift(.73f, 1080f, 2400f, motif)
                val actual = haloAmbientDrift(phase.floatValue, 1080f, 2400f, motif)
                assertEquals(expected.x, actual.x, .0001f)
                assertEquals(expected.y, actual.y, .0001f)
            }
        } finally {
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test fun gatedMotionStartsNoTickerEvenForAResumedOwner() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        try {
            owner.registry.currentState = Lifecycle.State.RESUMED
            val phase = mutableFloatStateOf(.7f)
            val job = launch { advanceHaloAmbientPhase(owner.lifecycle, false, phase) }
            runCurrent(); advanceTimeBy(1_000); runCurrent()
            assertTrue(job.isCompleted)
            assertEquals(.7f, phase.floatValue, .00001f)
        } finally { Dispatchers.resetMain() }
    }
}
