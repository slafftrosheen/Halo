package com.halo.providers.llamacpp

/**
 * Pure admission policy for native GGUF loading.
 *
 * Model file size is only a proxy for native memory usage, so the policy also
 * requires headroom in currently available RAM and refuses to load while the
 * system reports memory pressure.
 */
internal object ModelMemoryAdmission {
    data class Snapshot(
        val totalBytes: Long,
        val availableBytes: Long,
        val lowMemory: Boolean
    )

    sealed interface Decision {
        data object Allow : Decision
        data class Reject(val reason: Reason) : Decision
    }

    enum class Reason {
        INVALID_MEMORY_SNAPSHOT,
        SYSTEM_LOW_MEMORY,
        TOTAL_MEMORY_LIMIT,
        AVAILABLE_MEMORY_LIMIT
    }

    const val MODEL_MEMORY_FRACTION = 0.55

    /** Maximum reserve used for large models. Retained for diagnostics/compatibility. */
    const val RESERVED_MEMORY_BYTES = 1536L * 1024L * 1024L

    private const val MB = 1024L * 1024L
    private const val GB = 1024L * MB
    private const val SMALL_MODEL_RESERVE_BYTES = 512L * MB
    private const val MEDIUM_MODEL_RESERVE_BYTES = 768L * MB
    private const val LARGE_MODEL_RESERVE_BYTES = 1024L * MB

    /**
     * Keep a reserve proportional to model size instead of requiring 1.5 GiB
     * for every model. The old fixed reserve prevented sub-400 MiB GGUF models
     * from loading on 6 GiB Android devices even when the OS was not in a
     * low-memory state.
     */
    fun reserveBytesFor(modelBytes: Long): Long = when {
        modelBytes <= 512L * MB -> SMALL_MODEL_RESERVE_BYTES
        modelBytes <= 1L * GB -> MEDIUM_MODEL_RESERVE_BYTES
        modelBytes <= 2L * GB -> LARGE_MODEL_RESERVE_BYTES
        else -> RESERVED_MEMORY_BYTES
    }

    fun evaluate(modelBytes: Long, memory: Snapshot): Decision {
        if (modelBytes <= 0L || memory.totalBytes <= 0L || memory.availableBytes < 0L) {
            return Decision.Reject(Reason.INVALID_MEMORY_SNAPSHOT)
        }
        if (memory.lowMemory) {
            return Decision.Reject(Reason.SYSTEM_LOW_MEMORY)
        }

        val reserveBytes = reserveBytesFor(modelBytes)

        // 0.55 == 11/20. Integer arithmetic avoids overflow from multiplying
        // an unusually large total-memory value by a Double before conversion.
        val totalFractionLimit = (memory.totalBytes / 20L) * 11L +
            (memory.totalBytes % 20L) * 11L / 20L
        val totalLimit = minOf(
            totalFractionLimit,
            (memory.totalBytes - reserveBytes).coerceAtLeast(0L)
        )
        if (modelBytes > totalLimit) {
            return Decision.Reject(Reason.TOTAL_MEMORY_LIMIT)
        }

        // Subtraction form avoids overflowing modelBytes + reserve when inputs
        // are near Long.MAX_VALUE while preserving the same admission rule.
        val availableAfterReserve = memory.availableBytes - reserveBytes
        if (availableAfterReserve < 0L || modelBytes > availableAfterReserve) {
            return Decision.Reject(Reason.AVAILABLE_MEMORY_LIMIT)
        }

        return Decision.Allow
    }
}
