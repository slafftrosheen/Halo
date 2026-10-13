package com.halo.sdk.runtime

/**
 * Limits *concurrent active heavy compute*, not background residency or platform routing.
 *
 * Android's reported totalMem is lower than the marketed RAM (8 GB phones typically report
 * less than 8 GiB). On such devices independent ASR + LiteRT GPU/TTS executions can each
 * allocate large native heaps; free RAM at admission alone does not guarantee peak safety.
 * Serializing the heavyweight execution leases avoids simultaneous peak allocations while
 * preserving the resident engine's fast follow-up turns.
 *
 * This is a conservative, deterministic default. Benchmarks can later justify a device-specific
 * override; it must not be inferred from a GPU metadata capability flag.
 */
object MobileNativeComputePolicy {
    private const val GIB = 1024L * 1024L * 1024L
    const val SMALL_PHONE_RAM_LIMIT_BYTES = 9L * GIB
    const val PARALLEL_EXECUTION_HEADROOM_BYTES = 3L * GIB

    fun isConstrainedPhone(totalBytes: Long): Boolean =
        totalBytes > 0 && totalBytes <= SMALL_PHONE_RAM_LIMIT_BYTES

    fun allowConcurrentHeavyweights(
        totalBytes: Long,
        availableBytes: Long,
        pressure: NativeMemoryPressure,
    ): Boolean = pressure == NativeMemoryPressure.NORMAL &&
        totalBytes > SMALL_PHONE_RAM_LIMIT_BYTES &&
        availableBytes > PARALLEL_EXECUTION_HEADROOM_BYTES
}
