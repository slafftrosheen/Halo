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
    const val RESERVED_MEMORY_BYTES = 1536L * 1024L * 1024L

    fun evaluate(modelBytes: Long, memory: Snapshot): Decision {
        if (modelBytes <= 0L || memory.totalBytes <= 0L || memory.availableBytes < 0L) {
            return Decision.Reject(Reason.INVALID_MEMORY_SNAPSHOT)
        }
        if (memory.lowMemory) {
            return Decision.Reject(Reason.SYSTEM_LOW_MEMORY)
        }

        // 0.55 == 11/20. Integer arithmetic avoids overflow from multiplying
        // an unusually large total-memory value by a Double before conversion.
        val totalFractionLimit = (memory.totalBytes / 20L) * 11L +
            (memory.totalBytes % 20L) * 11L / 20L
        val totalLimit = minOf(
            totalFractionLimit,
            (memory.totalBytes - RESERVED_MEMORY_BYTES).coerceAtLeast(0L)
        )
        if (modelBytes > totalLimit) {
            return Decision.Reject(Reason.TOTAL_MEMORY_LIMIT)
        }

        // Subtraction form avoids overflowing modelBytes + reserve when inputs
        // are near Long.MAX_VALUE while preserving the same admission rule.
        val availableAfterReserve = memory.availableBytes - RESERVED_MEMORY_BYTES
        if (availableAfterReserve < 0L || modelBytes > availableAfterReserve) {
            return Decision.Reject(Reason.AVAILABLE_MEMORY_LIMIT)
        }

        return Decision.Allow
    }
}
