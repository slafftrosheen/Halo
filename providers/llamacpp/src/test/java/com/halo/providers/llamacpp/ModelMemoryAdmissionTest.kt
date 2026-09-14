package com.halo.providers.llamacpp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMemoryAdmissionTest {
    private val gb = 1024L * 1024L * 1024L
    private val mb = 1024L * 1024L

    @Test
    fun rejectsModelThatExceedsTotalMemoryCeiling() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = (4.9 * gb).toLong(),
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
    fun allowsSmallQwenModelWithAboutOneGigabyteAvailableOnSixGigabyteDevice() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 388L * mb,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 6L * gb,
                availableBytes = 1070L * mb,
                lowMemory = false
            )
        )

        assertTrue(decision is ModelMemoryAdmission.Decision.Allow)
        assertEquals(512L * mb, ModelMemoryAdmission.reserveBytesFor(388L * mb))
    }

    @Test
    fun smallModelStillRejectsWhenRealHeadroomIsTooLow() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 388L * mb,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 6L * gb,
                availableBytes = 850L * mb,
                lowMemory = false
            )
        )

        assertEquals(
            ModelMemoryAdmission.Reason.AVAILABLE_MEMORY_LIMIT,
            (decision as ModelMemoryAdmission.Decision.Reject).reason
        )
    }

    @Test
    fun allowsModelExactlyAtAvailableMemoryBoundary() {
        val modelBytes = 2L * gb
        val reserve = ModelMemoryAdmission.reserveBytesFor(modelBytes)

        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = modelBytes,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 8L * gb,
                availableBytes = modelBytes + reserve,
                lowMemory = false
            )
        )

        assertTrue(decision is ModelMemoryAdmission.Decision.Allow)
    }

    @Test
    fun rejectsNegativeAvailableMemoryBeforeApplyingArithmetic() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 1L * gb,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = 8L * gb,
                availableBytes = -1L,
                lowMemory = false
            )
        )

        assertEquals(
            ModelMemoryAdmission.Reason.INVALID_MEMORY_SNAPSHOT,
            (decision as ModelMemoryAdmission.Decision.Reject).reason
        )
    }

    @Test
    fun rejectsExtremeAvailableMemoryWithoutOverflowingModelPlusReserve() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = Long.MAX_VALUE - 1L,
            memory = ModelMemoryAdmission.Snapshot(
                totalBytes = Long.MAX_VALUE,
                availableBytes = Long.MAX_VALUE,
                lowMemory = false
            )
        )

        assertEquals(
            ModelMemoryAdmission.Reason.TOTAL_MEMORY_LIMIT,
            (decision as ModelMemoryAdmission.Decision.Reject).reason
        )
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
