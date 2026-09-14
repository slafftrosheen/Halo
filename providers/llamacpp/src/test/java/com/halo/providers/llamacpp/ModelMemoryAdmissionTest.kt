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
            memory = ModelMemoryAdmission.Snapshot(8L * gb, 7L * gb, false)
        )
        assertEquals(ModelMemoryAdmission.Reason.TOTAL_MEMORY_LIMIT, (decision as ModelMemoryAdmission.Decision.Reject).reason)
    }

    @Test
    fun rejectsWhenAvailableMemoryCannotPreserveHeadroom() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 4L * gb,
            memory = ModelMemoryAdmission.Snapshot(16L * gb, 5L * gb, false)
        )
        assertEquals(ModelMemoryAdmission.Reason.AVAILABLE_MEMORY_LIMIT, (decision as ModelMemoryAdmission.Decision.Reject).reason)
    }

    @Test
    fun rejectsWhenAndroidReportsLowMemory() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 2L * gb,
            memory = ModelMemoryAdmission.Snapshot(16L * gb, 12L * gb, true)
        )
        assertEquals(ModelMemoryAdmission.Reason.SYSTEM_LOW_MEMORY, (decision as ModelMemoryAdmission.Decision.Reject).reason)
    }

    @Test
    fun allowsModelWhenBothTotalAndAvailableMemoryAreHealthy() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 4L * gb,
            memory = ModelMemoryAdmission.Snapshot(16L * gb, 8L * gb, false)
        )
        assertTrue(decision is ModelMemoryAdmission.Decision.Allow)
    }

    @Test
    fun allowsSmallQwenModelWithAboutOneGigabyteAvailableOnSixGigabyteDevice() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 388L * mb,
            memory = ModelMemoryAdmission.Snapshot(6L * gb, 1070L * mb, false)
        )
        assertTrue(decision is ModelMemoryAdmission.Decision.Allow)
        assertEquals(512L * mb, ModelMemoryAdmission.reserveBytesFor(388L * mb))
    }

    @Test
    fun smallModelStillRejectsWhenRealHeadroomIsTooLow() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 388L * mb,
            memory = ModelMemoryAdmission.Snapshot(6L * gb, 850L * mb, false)
        )
        assertEquals(ModelMemoryAdmission.Reason.AVAILABLE_MEMORY_LIMIT, (decision as ModelMemoryAdmission.Decision.Reject).reason)
    }

    @Test
    fun allowsModelExactlyAtAvailableMemoryBoundary() {
        val modelBytes = 2L * gb
        val reserve = ModelMemoryAdmission.reserveBytesFor(modelBytes)
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = modelBytes,
            memory = ModelMemoryAdmission.Snapshot(8L * gb, modelBytes + reserve, false)
        )
        assertTrue(decision is ModelMemoryAdmission.Decision.Allow)
    }

    @Test
    fun reserveStepsUpAtOneGigabyteBoundary() {
        assertEquals(512L * mb, ModelMemoryAdmission.reserveBytesFor(512L * mb))
        assertEquals(768L * mb, ModelMemoryAdmission.reserveBytesFor(512L * mb + 1L))
        assertEquals(768L * mb, ModelMemoryAdmission.reserveBytesFor(1L * gb))
        assertEquals(1024L * mb, ModelMemoryAdmission.reserveBytesFor(1L * gb + 1L))
    }

    @Test
    fun reserveStepsUpAtTwoGigabyteBoundaryAndCapsAtConfiguredMaximum() {
        assertEquals(1024L * mb, ModelMemoryAdmission.reserveBytesFor(2L * gb))
        assertEquals(1536L * mb, ModelMemoryAdmission.reserveBytesFor(2L * gb + 1L))
        assertEquals(1536L * mb, ModelMemoryAdmission.reserveBytesFor(5L * gb))
    }

    @Test
    fun rejectsNegativeAvailableMemoryBeforeApplyingArithmetic() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 1L * gb,
            memory = ModelMemoryAdmission.Snapshot(8L * gb, -1L, false)
        )
        assertEquals(ModelMemoryAdmission.Reason.INVALID_MEMORY_SNAPSHOT, (decision as ModelMemoryAdmission.Decision.Reject).reason)
    }

    @Test
    fun rejectsExtremeAvailableMemoryWithoutOverflowingModelPlusReserve() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = Long.MAX_VALUE - 1L,
            memory = ModelMemoryAdmission.Snapshot(Long.MAX_VALUE, Long.MAX_VALUE, false)
        )
        assertEquals(ModelMemoryAdmission.Reason.TOTAL_MEMORY_LIMIT, (decision as ModelMemoryAdmission.Decision.Reject).reason)
    }

    @Test
    fun rejectsInvalidMemorySnapshot() {
        val decision = ModelMemoryAdmission.evaluate(
            modelBytes = 1L * gb,
            memory = ModelMemoryAdmission.Snapshot(0L, 0L, false)
        )
        assertEquals(ModelMemoryAdmission.Reason.INVALID_MEMORY_SNAPSHOT, (decision as ModelMemoryAdmission.Decision.Reject).reason)
    }
}
