package com.halo.providers.litert.runtime

import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.runtime.MobileNativeComputePolicy

/**
 * The host's RAM safety gate applies AFTER artifact/runtime/device GPU qualification.
 * OpenCL scratch/weight allocation can dwarf the .litertlm artifact size. CPU-first AUTO
 * on 4/6 GB prevents a past test success from overriding low-memory platform policy.
 *
 * The qualification record is never altered. An explicit developer GPU override
 * can still exercise and benchmark the GPU with native crash recovery enabled.
 */
object LiteRtMobileBackendPolicy {
    fun apply(resolution: LiteRtBackendResolution, totalRamBytes: Long, developerExplicitGpu: Boolean): LiteRtBackendResolution {
        if (resolution.effective != LocalComputeBackend.GPU || developerExplicitGpu) return resolution
        if (MobileNativeComputePolicy.autoGpuAllowed(totalRamBytes)) return resolution
        return resolution.copy(
            effective = LocalComputeBackend.CPU,
            reason = "4/6GB or unknown RAM: AUTO uses CPU; explicit developer GPU available",
        )
    }
}
