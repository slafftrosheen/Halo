package com.halo.app.notification

import org.junit.Assert.*
import org.junit.Test

class NotificationLifecycleTrackerTest {
    @Test fun twentyUpdatesEmitOnceAndRepostStartsNewLifecycle() {
        val tracker = NotificationLifecycleTracker()
        assertEquals(1, (1..20).count { tracker.posted("key", false, false) })
        tracker.removed("key")
        assertTrue(tracker.posted("key", false, false))
    }
    @Test fun seededExistingAndExcludedNotificationsDoNotEmit() {
        val tracker = NotificationLifecycleTracker()
        tracker.seed(listOf("existing"))
        assertFalse(tracker.posted("existing", false, false))
        assertFalse(tracker.posted("group", false, true))
        assertFalse(tracker.posted("halo", true, false))
    }

    @Test fun clearingAndConcurrentPostsPreserveLifecycle() {
        val tracker = NotificationLifecycleTracker()
        val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val threads = List(20) { Thread { results += tracker.posted("shared", false, false) } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(1, results.count { it })
        tracker.clear()
        assertTrue(tracker.posted("shared", false, false))
    }


    @Test fun listenerRoutingDeliversEveryUpdateWithOneAutomationEdge() {
        val tracker = NotificationLifecycleTracker()
        val routing = NotificationPostRouting(tracker)
        var bridgePosts = 0
        var signals = 0
        repeat(20) { routing.posted("active", false, false) { emit -> bridgePosts++; if (emit) signals++ } }
        assertEquals(20, bridgePosts)
        assertEquals(1, signals)
        tracker.removed("active")
        routing.posted("active", false, false) { emit -> bridgePosts++; if (emit) signals++ }
        assertEquals(21, bridgePosts)
        assertEquals(2, signals)
    }
}
