package com.halo.providers.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMemoryAdmissionTest {
    private val gb = 1024L * 1024L * 1024L

    @Test
    fun rejectsModelThatExceedsTotalMemoryCeiling() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 4.9 * gb.toDouble().toLong(),
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 8L * gb,
                availableBytes = 7L * gb,
                lowMemory = false
            )
        )

        assertEquals(
            ModelMemoryAdmission.Reason.TOTAL_MEMORY_LIMIT,
            (decision as ModelMemoryAdmission.Decision.Reject).reason
        )
    }

    @Test
    fun rejectsWhenAvailableMemoryCannotPreserveHeadroom() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 4L * gb,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 16L * gb,
                availableBytes = 5L * gb,
                lowMemory = false
            )
        )

        assertEquals(
            ModelMemoryAdmission.Reason.AVAILABLE_MEMORY_LIMIT,
            (decision as ModelMemoryAdmission.Decision.Reject).reason
        )
    }

    @Test
    fun rejectsWhenAndroidReportsLowMemory() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 2L * gb,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 16L * gb,
                availableBytes = 12L * gb,
                lowMemory = true
            )
        )

        assertEquals(
            ModelMemoryAdmission.Reason.SYSTEM_LOW_MEMORY,
            (decision as ModelMemoryAdmission.Decision.Reject).reason
        )
    }

    @Test
    fun allowsModelWhenBothTotalAndAvailableMemoryAreHealthy() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 4L * gb,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 16L * gb,
                availableBytes = 8L * gb,
                lowMemory = false
            )
        )

        assertTrue(decision is ModelMemoryAdmission.Decision.Allow)
    }

    @Test
    fun rejectsInvalidMemorySnapshot() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 1L * gb,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 0L,
                availableBytes = 0L,
                lowMemory = false
            )
        )

        assertEquals(
            ModelMemoryAdmission.Reason.INVALID_MEMORY_SNAPSHOT,
            (decision as ModelMemoryAdmission.Decision.Reject).reason
        )
    }
}
