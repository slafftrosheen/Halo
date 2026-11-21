package com.halo.app.ui.zone6

import com.halo.app.ai.IntentQualificationFixture.MemoryStorage
import org.junit.Assert.*
import org.junit.Test

class HaloPageGuidanceTest {
    @Test fun automaticDemosAreLimitedPerGestureAcrossReopenedPages() {
        val storage = MemoryStorage()
        repeat(3) {
            val coach = HaloPageGuidance(storage) { 100L }
            coach.gestureUsed(HaloGesture.RING_HOLD)
            coach.presentPending(requireNotNull(coach.pending))
            assertEquals(HaloGesture.RING_HOLD, coach.demonstration)
        }
        val reopened = HaloPageGuidance(storage) { 200L }
        reopened.gestureUsed(HaloGesture.RING_HOLD)
        assertNull(reopened.pending)
        reopened.gestureUsed(HaloGesture.DOCK_SWIPE)
        assertNotNull(reopened.pending)
        reopened.demonstrate(HaloGesture.RING_HOLD)
        assertEquals(HaloGesture.RING_HOLD, reopened.demonstration)
    }

    @Test fun expiredRequestAndDisposedPageAlternativeDoNothing() {
        var now = 100L
        var calls = 0
        val coach = HaloPageGuidance(MemoryStorage()) { now }
        coach.gestureUsed(HaloGesture.CANVAS_LINK)
        val pending = requireNotNull(coach.pending)
        now += 30_001L
        coach.presentPending(pending)
        assertNull(coach.demonstration)
        val original = Any()
        val replacement = Any()
        coach.bind(HaloGesture.CANVAS_LINK, original) { calls += 10 }
        coach.bind(HaloGesture.CANVAS_LINK, replacement) { calls++ }
        coach.unbind(HaloGesture.CANVAS_LINK, original)
        coach.useAlternative(HaloGesture.CANVAS_LINK)
        assertEquals(1, calls)
        coach.unbind(HaloGesture.CANVAS_LINK, replacement)
        coach.useAlternative(HaloGesture.CANVAS_LINK)
        assertEquals(1, calls)
    }
}
