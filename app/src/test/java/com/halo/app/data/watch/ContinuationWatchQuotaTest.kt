package com.halo.app.data.watch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuationWatchQuotaTest {
    @Test fun updatingAnActiveWatchAtGlobalLimitDoesNotConsumeAnotherSlot() {
        val active = (1..100).map { "watch-$it" }
        assertTrue(watchQuotaAvailable(active, "watch-1", 100))
        assertFalse(watchQuotaAvailable(active, "watch-new", 100))
    }

    @Test fun updatingAnActiveWatchAtPerRunLimitDoesNotConsumeAnotherSlot() {
        val active = listOf("run-1", "run-2", "run-3", "run-4")
        assertTrue(watchQuotaAvailable(active, "run-2", 4))
        assertFalse(watchQuotaAvailable(active, "run-5", 4))
    }

    @Test fun availableQuotaAllowsFreshWatch() {
        assertTrue(watchQuotaAvailable(listOf("a", "b"), "c", 4))
    }
}
