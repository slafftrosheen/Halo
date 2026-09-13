package com.halo.providers.llamacpp

/**
 * Conservative, deterministic planner for native local-model admission.
 *
 * This is intentionally an estimator, not a claim about llama.cpp's exact allocator
 * behaviour. The native backend remains the final authority when creating a context.
 */
internal object LocalModelMemoryPlanner {
    private const val BYTES_PER_MB = 1024L * 1024L
    private const val BYTES_PER_GB = 1024L * BYTES_PER_MB

    /** Extra native/process headroom kept outside the model estimate. */
    const val PROCESS_RESERVE_BYTES = 1536L * BYTES_PER_MB

    /** Approximate KV/compute multiplier per context token. */
    const val APPROX_BYTES_PER_CONTEXT_TOKEN = 16L * 1024L

    data class MemorySnapshot(
        val totalBytes: Long,
        val availableBytes: Long,
        val lowMemory: Boolean,
    )

    data class Candidate(
        val contextSize: Int,
        val gpuLayers: Int,
    )

    data class Plan(
        val candidate: Candidate,
        val estimatedBytes: Long,
        val remainingBytes: Long,
    )

    /**
     * Selects the first safe candidate in caller-provided preference order.
     * Candidates should be ordered from most desirable to least desirable.
     */
    fun choose(
        modelBytes: Long,
        memory: MemorySnapshot,
        candidates: List<Candidate>,
    ): Plan? {
        if (modelBytes <= 0L || memory.totalBytes <= 0L || memory.availableBytes < 0L || memory.lowMemory) {
            return null
        }
        if (candidates.isEmpty()) return null

        val availableAfterReserve = memory.availableBytes - PROCESS_RESERVE_BYTES
        if (availableAfterReserve <= 0L) return null

        val totalFractionLimit = (memory.totalBytes / 20L) * 11L +
            (memory.totalBytes % 20L) * 11L / 20L
        val totalLimit = minOf(
            totalFractionLimit,
            (memory.totalBytes - PROCESS_RESERVE_BYTES).coerceAtLeast(0L),
        )

        for (candidate in candidates) {
            if (candidate.contextSize <= 0 || candidate.gpuLayers < -1) continue

            val contextBytes = candidate.contextSize.toLong() * APPROX_BYTES_PER_CONTEXT_TOKEN
            val estimatedBytes = saturatingAdd(modelBytes, contextBytes)
            if (estimatedBytes > totalLimit || estimatedBytes > availableAfterReserve) continue

            return Plan(
                candidate = candidate,
                estimatedBytes = estimatedBytes,
                remainingBytes = availableAfterReserve - estimatedBytes,
            )
        }
        return null
    }

    fun defaultCandidates(
        requestedContextSize: Int,
        requestedGpuLayers: Int,
        accelerator: Boolean,
    ): List<Candidate> {
        val contexts = listOf(requestedContextSize, 3072, 2048, 1536, 1024, 768, 512)
            .map { it.coerceAtLeast(1) }
            .distinct()
            .sortedDescending()
            .let { ordered ->
                val requested = requestedContextSize.coerceAtLeast(1)
                listOf(requested) + ordered.filter { it < requested }
            }

        val gpu = if (!accelerator) {
            listOf(0)
        } else if (requestedGpuLayers < 0) {
            listOf(-1, 64, 48, 32, 24, 16, 12, 8, 4, 2, 1, 0)
        } else {
            buildList {
                add(requestedGpuLayers.coerceAtLeast(1))
                var next = requestedGpuLayers.coerceAtLeast(1)
                while (next > 1) {
                    next /= 2
                    add(next)
                }
                add(0)
            }.distinct()
        }

        return contexts.flatMap { context -> gpu.map { layers -> Candidate(context, layers) } }
    }

    private fun saturatingAdd(a: Long, b: Long): Long {
        if (b <= 0L) return a
        if (a > Long.MAX_VALUE - b) return Long.MAX_VALUE
        return a + b
    }
}
