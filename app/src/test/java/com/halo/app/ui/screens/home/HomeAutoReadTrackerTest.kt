package com.halo.app.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class HomeAutoReadTrackerTest {
    @Test fun restoredAndDelayedHistoryNeverSpeaks() {
        val tracker = HomeAutoReadTracker()
        assertFalse(tracker.observe("one", null, false, false))
        assertFalse(tracker.observe("one", "saved", false, true))
        assertFalse(tracker.observe("two", "other", false, true))
    }

    @Test fun newlyCompletedAnswerSpeaksOnlyOnceAfterObservedWork() {
        val tracker = HomeAutoReadTracker()
        tracker.observe("one", "old", false, true)
        assertFalse(tracker.observe("one", "old", true, true))
        assertFalse(tracker.observe("one", "old", false, true))
        assertTrue(tracker.observe("one", "new", false, true))
        assertFalse(tracker.observe("one", "new", false, true))
    }

    @Test fun switchingOrFailureDisarmsThePreviousGeneration() {
        val tracker = HomeAutoReadTracker()
        tracker.observe("one", "old", true, true)
        assertFalse(tracker.observe("two", "saved", false, true))
        tracker.observe("two", "saved", true, true)
        assertFalse(tracker.observe("two", "failed", false, true, failed = true))
        assertFalse(tracker.observe("two", "late", false, true))
    }
}
