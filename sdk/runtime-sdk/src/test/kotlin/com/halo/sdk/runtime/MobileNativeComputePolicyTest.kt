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

    @Test fun missingDeviceBudgetFailsClosed() {
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(0, 10L * gib, NativeMemoryPressure.NORMAL))
        assertFalse(MobileNativeComputePolicy.allowConcurrentHeavyweights(-1, 10L * gib, NativeMemoryPressure.NORMAL))
        assertFalse(MobileNativeComputePolicy.isConstrainedPhone(0))
    }
}
