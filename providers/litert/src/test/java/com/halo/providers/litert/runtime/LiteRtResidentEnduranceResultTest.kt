package com.halo.providers.litert.runtime

import com.halo.sdk.provider.LocalComputeBackend
import org.junit.Assert.*
import org.junit.Test

class LiteRtResidentEnduranceResultTest {
    @Test fun completedAllTurnsIsSuccessButPartialRunIsNot() {
        val good = LiteRtResidentEnduranceResult("test", LocalComputeBackend.CPU, 5, 5, 5, false, null)
        assertTrue(good.passed)
        assertTrue(good.summaryLine().contains("PASSED"))
        val partial = good.copy(attemptedTurns = 3, passedTurns = 2, boundedFailure = "unexpected token")
        assertFalse(partial.passed)
        assertTrue(partial.summaryLine().contains("FAILED"))
    }

    @Test fun preflightBlockedNeverCountsAsSuccessfulEndurance() {
        val blocked = LiteRtResidentEnduranceResult("test", LocalComputeBackend.GPU, 4, 0, 0, true,
            "another native model is resident")
        assertFalse(blocked.passed)
        assertTrue(blocked.summaryLine().contains("BLOCKED"))
    }
}
