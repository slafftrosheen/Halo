package com.halo.app.lab.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabAttemptDeadlinePolicyTest {
    @Test
    fun acceptsPersistedDefaultStageBudgets() {
        assertEquals(450_000L, LabAttemptDeadlinePolicy.totalMs(300_000L, 120_000L, 30_000L))
    }

    @Test
    fun rejectsMissingInvalidAndOverflowingStageBudgets() {
        assertNull(LabAttemptDeadlinePolicy.totalMs(-1L, 120_000L, 30_000L))
        assertNull(LabAttemptDeadlinePolicy.totalMs(1_800_001L, 120_000L, 30_000L))
    }

    @Test
    fun rebootedElapsedClockExpiresThePersistedDeadline() {
        assertEquals(50L, LabAttemptDeadlinePolicy.deadlineElapsedMs(10_000L, 450_000L, 50L))
    }
}
