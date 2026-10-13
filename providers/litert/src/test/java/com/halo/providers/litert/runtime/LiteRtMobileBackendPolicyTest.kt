package com.halo.providers.litert.runtime

import com.halo.sdk.provider.LocalComputeBackend
import org.junit.Assert.*
import org.junit.Test

class LiteRtMobileBackendPolicyTest {
    private fun qualified(backend: LocalComputeBackend) = LiteRtBackendResolution(
        requested = LocalComputeBackend.AUTO, effective = backend,
        gpuQualification = LiteRtBackendQualification.QUALIFIED, reason = "gpu qualified"
    )

    @Test fun qualifiedGpuEvidenceSurvivesFourSixGbAutoCpuGate() {
        val resolution = LiteRtMobileBackendPolicy.apply(qualified(LocalComputeBackend.GPU), 5_700_000_000L, false)
        assertEquals(LocalComputeBackend.CPU, resolution.effective)
        assertEquals(LocalComputeBackend.AUTO, resolution.requested)
        assertEquals(LiteRtBackendQualification.QUALIFIED, resolution.gpuQualification)
    }

    @Test fun eightGbQualifiedGpuAndExplicitGpuAreUnaffected() {
        assertEquals(LocalComputeBackend.GPU,
            LiteRtMobileBackendPolicy.apply(qualified(LocalComputeBackend.GPU), 7_600_000_000L, false).effective)
        assertEquals(LocalComputeBackend.GPU,
            LiteRtMobileBackendPolicy.apply(qualified(LocalComputeBackend.GPU), 3_700_000_000L, true).effective)
    }

    @Test fun cpuOrUnknownRamUsesSafeFallback() {
        assertEquals(LocalComputeBackend.CPU,
            LiteRtMobileBackendPolicy.apply(qualified(LocalComputeBackend.GPU), 0, false).effective)
        assertEquals(LocalComputeBackend.CPU,
            LiteRtMobileBackendPolicy.apply(qualified(LocalComputeBackend.CPU), 12_000_000_000L, false).effective)
    }
}
