package com.halo.app.ai

import org.junit.Assert.*
import org.junit.Test

class LocalModelDeviceFitBoundaryTest {
    private val gib = 1024L * 1024 * 1024
    private fun assess(bytes: Long = gib, storage: Long = 8*gib, free: Long = 4*gib,
        total: Long = 8*gib, floor: Int? = null) = LocalModelDeviceFit.assess(bytes, storage, free, total, floor)

    @Test fun knownMeasurementsCanOnlyProduceAnEstimate() {
        assertEquals(LocalModelDeviceFit.Verdict.ESTIMATED_FIT, assess())
    }
    @Test fun zeroStorageNeverMeansUnlimitedStorage() {
        assertEquals(LocalModelDeviceFit.Verdict.STORAGE_SHORTAGE, assess(storage = 0))
    }
    @Test fun unavailableStorageRemainsUnknown() {
        assertEquals(LocalModelDeviceFit.Verdict.UNKNOWN, assess(storage = -1))
    }
    @Test fun unknownMemoryCannotProduceFit() {
        assertEquals(LocalModelDeviceFit.Verdict.UNKNOWN, assess(total = 0))
        assertEquals(LocalModelDeviceFit.Verdict.UNKNOWN, assess(free = -1))
        assertEquals(LocalModelDeviceFit.Verdict.UNKNOWN, assess(free = 9*gib))
    }
    @Test fun zeroAvailableMemoryIsPressureNotFit() {
        assertEquals(LocalModelDeviceFit.Verdict.MEMORY_PRESSURE, assess(free = 0))
    }
    @Test fun badCatalogSizeDoesNotCrashTheConsumerScreen() {
        assertEquals(LocalModelDeviceFit.Verdict.UNKNOWN, assess(bytes = 0))
        assertEquals(LocalModelDeviceFit.Verdict.UNKNOWN, assess(bytes = Long.MAX_VALUE))
        assertEquals(LocalModelDeviceFit.Verdict.UNKNOWN, assess(bytes = -1))
    }
    @Test fun storageAllowanceBoundaryIsInclusive() {
        val required = gib + gib/10 + 256L*1024*1024
        assertEquals(LocalModelDeviceFit.Verdict.ESTIMATED_FIT, assess(storage = required))
        assertEquals(LocalModelDeviceFit.Verdict.STORAGE_SHORTAGE, assess(storage = required - 1))
    }
    @Test fun ramFloorAndWorkingMemoryAreIndependent() {
        assertEquals(LocalModelDeviceFit.Verdict.MEMORY_PRESSURE, assess(free = 2*gib - 1))
        assertEquals(LocalModelDeviceFit.Verdict.MEMORY_PRESSURE, assess(floor = 12))
        assertEquals(LocalModelDeviceFit.Verdict.ESTIMATED_FIT, assess(free = 2*gib, floor = 8))
    }
}
