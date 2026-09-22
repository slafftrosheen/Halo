package com.halo.providers.llamacpp

/**
 * Conservative, deterministic planner for native local-model admission.
 *
 * This is intentionally an estimator, not a claim about llama.cpp's exact allocator
 * behaviour. The native backend remains the final authority when creating a context.
 */
object LocalModelMemoryPlanner {
    private const val BYTES_PER_MB = 1024L * 1024L

    /** Maximum extra native/process headroom kept outside the model estimate. */
    const val PROCESS_RESERVE_BYTES = 1536L * BYTES_PER_MB

    /** Fallback estimate used when GGUF attention metadata is incomplete. */
    const val APPROX_BYTES_PER_CONTEXT_TOKEN = 16L * 1024L

    /** Conservative multiplier over the raw KV-cache footprint. */
    private const val KV_CACHE_HEADROOM_NUMERATOR = 3L
    private const val KV_CACHE_HEADROOM_DENOMINATOR = 2L

    data class MemorySnapshot(
        val totalBytes: Long,
        val availableBytes: Long,
        val lowMemory: Boolean,
    )

    data class ModelProfile(
        val blockCount: Int?,
        val embeddingLength: Int?,
        val attentionHeadCount: Int?,
        val attentionHeadCountKv: Int?,
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

    sealed interface RemoteModelFit {
        val context: Int?
        data class Recommended(override val context: Int, val estimatedBytes: Long) : RemoteModelFit
        data class Fits(override val context: Int, val estimatedBytes: Long) : RemoteModelFit
        data class Tight(override val context: Int, val estimatedBytes: Long) : RemoteModelFit
        data class TooLarge(val requiredBytes: Long, val availableBytes: Long) : RemoteModelFit {
            override val context: Int? = null
        }
        data object Unknown : RemoteModelFit { override val context: Int? = null }
    }

    fun preflightRemote(
        modelBytes: Long,
        memory: MemorySnapshot,
        modelProfile: ModelProfile? = null,
    ): RemoteModelFit {
        if (modelBytes <= 0L) return RemoteModelFit.Unknown
        val contexts = listOf(16_384, 8_192, 4_096, 2_048)
        val plan = choose(modelBytes, memory, contexts.map { Candidate(it, 0) }, modelProfile)
            ?: return RemoteModelFit.TooLarge(
                modelBytes + estimateContextBytes(2_048, modelProfile),
                (memory.availableBytes - ModelMemoryAdmission.reserveBytesFor(modelBytes)).coerceAtLeast(0L)
            )
        return when {
            plan.candidate.contextSize >= 8_192 && plan.remainingBytes >= modelBytes / 2 ->
                RemoteModelFit.Recommended(plan.candidate.contextSize, plan.estimatedBytes)
            plan.remainingBytes < modelBytes / 4 ->
                RemoteModelFit.Tight(plan.candidate.contextSize, plan.estimatedBytes)
            else -> RemoteModelFit.Fits(plan.candidate.contextSize, plan.estimatedBytes)
        }
    }

    fun choose(
        modelBytes: Long,
        memory: MemorySnapshot,
        candidates: List<Candidate>,
        modelProfile: ModelProfile? = null,
    ): Plan? {
        if (modelBytes <= 0L || memory.totalBytes <= 0L || memory.availableBytes < 0L || memory.lowMemory) {
            return null
        }
        if (candidates.isEmpty()) return null

        val reserveBytes = ModelMemoryAdmission.reserveBytesFor(modelBytes)
        val availableAfterReserve = memory.availableBytes - reserveBytes
        if (availableAfterReserve <= 0L) return null

        val totalFractionLimit = (memory.totalBytes / 20L) * 11L +
            (memory.totalBytes % 20L) * 11L / 20L
        val totalLimit = minOf(
            totalFractionLimit,
            (memory.totalBytes - reserveBytes).coerceAtLeast(0L),
        )

        for (candidate in candidates) {
            if (candidate.contextSize <= 0 || candidate.gpuLayers < -1) continue

            val contextBytes = estimateContextBytes(candidate.contextSize, modelProfile)
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

    fun estimateContextBytes(contextSize: Int, modelProfile: ModelProfile?): Long {
        if (contextSize <= 0) return 0L

        val profile = modelProfile ?: return saturatingMultiply(contextSize.toLong(), APPROX_BYTES_PER_CONTEXT_TOKEN)
        val layers = profile.blockCount?.takeIf { it > 0 } ?: return fallbackContextBytes(contextSize)
        val embedding = profile.embeddingLength?.takeIf { it > 0 } ?: return fallbackContextBytes(contextSize)
        val heads = profile.attentionHeadCount?.takeIf { it > 0 } ?: return fallbackContextBytes(contextSize)
        val kvHeads = profile.attentionHeadCountKv?.takeIf { it > 0 && it <= heads } ?: return fallbackContextBytes(contextSize)
        if (embedding % heads != 0) return fallbackContextBytes(contextSize)

        val headDimension = embedding / heads
        val elementsPerToken = saturatingMultiply(
            saturatingMultiply(layers.toLong(), kvHeads.toLong()),
            headDimension.toLong(),
        )
        val rawKvBytes = saturatingMultiply(
            saturatingMultiply(elementsPerToken, contextSize.toLong()),
            4L,
        )
        return saturatingMultiply(rawKvBytes, KV_CACHE_HEADROOM_NUMERATOR) / KV_CACHE_HEADROOM_DENOMINATOR
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

    private fun fallbackContextBytes(contextSize: Int): Long = saturatingMultiply(
        contextSize.toLong(),
        APPROX_BYTES_PER_CONTEXT_TOKEN,
    )

    private fun saturatingMultiply(a: Long, b: Long): Long {
        if (a <= 0L || b <= 0L) return 0L
        if (a > Long.MAX_VALUE / b) return Long.MAX_VALUE
        return a * b
    }

    private fun saturatingAdd(a: Long, b: Long): Long {
        if (b <= 0L) return a
        if (a > Long.MAX_VALUE - b) return Long.MAX_VALUE
        return a + b
    }
}
