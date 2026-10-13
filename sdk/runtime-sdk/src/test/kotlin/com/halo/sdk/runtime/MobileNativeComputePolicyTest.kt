package com.halo.sdk.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileNativeComputePolicyTest {
    private val gib = 1024L * 1024L * 1024L

    @Test fun eightGbPhonesSerializeActiveVoiceAndInferenceCompute() {
        assertTrue(MobileNativeComputePolicy.isConstrainedPhone(8L * gib))
        assertTrue(MobileNativeComputePolicy.isConstrainedPhone(7L * gib))
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(8L * gib, 5L * gib, NativeMemoryPressure.NORMAL))
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(7L * gib, 6L * gib, NativeMemoryPressure.NORMAL))
    }

    @Test fun largePhonesNeedSustainedHeadroomAndNormalPressure() {
        assertTrue(MobileNativeComputePolicy.allowConcurrentHeavyweights(12L * gib, 4L * gib, NativeMemoryPressure.NORMAL))
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(12L * gib, 3L * gib, NativeMemoryPressure.NORMAL))
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(12L * gib, 6L * gib, NativeMemoryPressure.LOW))
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(12L * gib, 6L * gib, NativeMemoryPressure.CRITICAL))
    }

    @Test fun fourSixEightGigabyteTiersLimitWarmResidencyAndAutomaticGpu() {
        assertTrue(MobileNativeComputePolicy.tier(3_700_000_000L) == MobileNativeComputePolicy.DeviceTier.FOUR_GB)
        assertTrue(MobileNativeComputePolicy.tier(5_700_000_000L) == MobileNativeComputePolicy.DeviceTier.SIX_GB)
        assertTrue(MobileNativeComputePolicy.tier(7_500_000_000L) == MobileNativeComputePolicy.DeviceTier.EIGHT_GB)
        assertFalse(MobileNativeComputePolicy.autoGpuAllowed(3_700_000_000L))
        assertFalse(MobileNativeComputePolicy.autoGpuAllowed(5_700_000_000L))
        assertTrue(MobileNativeComputePolicy.autoGpuAllowed(7_500_000_000L))
        assertTrue(MobileNativeComputePolicy.idleGraceMillis(3_700_000_000L) < MobileNativeComputePolicy.idleGraceMillis(5_700_000_000L))
        assertTrue(MobileNativeComputePolicy.idleGraceMillis(5_700_000_000L) < MobileNativeComputePolicy.idleGraceMillis(7_500_000_000L))
    }

    @Test fun missingDeviceBudgetFailsClosed() {
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(0, 10L * gib, NativeMemoryPressure.NORMAL))
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(-1, 10L * gib, NativeMemoryPressure.NORMAL))
        assertFalse(MobileNativeComputePolicy.isConstrainedPhone(0))
    }
}
