package com.halo.providers.llamacpp

/**
 * Pure admission policy for native GGUF loading.
 *
 * File size is only a proxy for native memory usage. Admission therefore
 * requires both a device-wide model-size limit and current available-memory
 * headroom. Swap/zRAM is deliberately not counted as model capacity.
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

    private const val MB = 1024L * 1024L
    private const val GB = 1024L * MB
    private const val SMALL_MODEL_RESERVE_BYTES = 512L * MB
    private const val MEDIUM_MODEL_RESERVE_BYTES = 768L * MB
    private const val LARGE_MODEL_RESERVE_BYTES = 1024L * MB

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
        if (memory.lowMemory) return Decision.Reject(Reason.SYSTEM_LOW_MEMORY)

        val reserveBytes = reserveBytesFor(modelBytes)
        val totalFractionLimit = (memory.totalBytes / 20L) * 11L +
            (memory.totalBytes % 20L) * 11L / 20L
        val totalLimit = minOf(
            totalFractionLimit,
            (memory.totalBytes - reserveBytes).coerceAtLeast(0L)
        )
        if (modelBytes > totalLimit) return Decision.Reject(Reason.TOTAL_MEMORY_LIMIT)

        val availableAfterReserve = memory.availableBytes - reserveBytes
        if (availableAfterReserve < 0L || modelBytes > availableAfterReserve) {
            return Decision.Reject(Reason.AVAILABLE_MEMORY_LIMIT)
        }

        return Decision.Allow
    }
}
