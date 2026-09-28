package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.provider.InferenceAdmission
import com.halo.sdk.provider.InferencePriority
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeLoadConfig
import java.util.UUID

/**
 * Telemetry for one benchmarked artifact.
 *
 * IMPORTANT: [estimatedTokenCount] and [estimatedTokensPerSecond] are derived from response text
 * length, not from a tokenizer. They are labelled "estimated" everywhere they surface and must
 * never be presented as tokenizer-accurate throughput.
 */
data class LocalModelBenchmarkResult(
    val modelId: String,
    val displayName: String,
    val artifactSha256: String?,
    val artifactBytes: Long,
    val requestedBackend: LocalComputeBackend,
    val effectiveBackend: LocalComputeBackend,
    val requestedContextTokens: Int?,
    val effectiveContextTokens: Int?,
    val loadDurationMs: Long,
    val engineInitDurationMs: Long?,
    val conversationCreateDurationMs: Long?,
    val timeToFirstTokenMs: Long,
    val generationDurationMs: Long,
    val estimatedTokenCount: Int,
    val estimatedTokensPerSecond: Double,
    val responseChars: Int,
    val fallbackReason: String?,
    val contextFallbackReason: String?,
    val responseText: String,
    val promptText: String,
    val reasoningChars: Int,
    val peakHeapBytes: Long,
) {
    /** One compact line for the development UI/log. */
    fun summaryLine(): String = buildString {
        append("model=$modelId sha=${artifactSha256?.take(12) ?: "unknown"} ")
        append("requested=${requestedBackend.name} effective=${effectiveBackend.name} ")
        append("context=${effectiveContextTokens ?: "model-default"} ")
        append("loadMs=$loadDurationMs engineInitMs=${engineInitDurationMs ?: "n/a"} ")
        append("conversationMs=${conversationCreateDurationMs ?: "n/a"} ")
        append("ttftMs=$timeToFirstTokenMs genMs=$generationDurationMs ")
        append("estimatedTokens=$estimatedTokenCount estimatedTokPerSec=%.2f ".format(estimatedTokensPerSecond))
        append("chars=$responseChars peakHeapMb=${peakHeapBytes / 1_048_576} ")
        append("fallback=${fallbackReason ?: "none"}")
        contextFallbackReason?.let { append(" contextFallback=$it") }
    }
}

/** Approximate Java-heap usage; a native-side snapshot is not available through the public API. */
internal fun javaHeapUsageBytes(): Long =
    Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

class LocalModelBenchmarkRunner(
    private val session: LocalModelSession,
    private val inventoryRepository: LocalModelInventoryRepository,
) {

    suspend fun runBenchmark(
        modelId: String,
        prompt: String = DEFAULT_PROMPT,
        maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
        loadConfigFor: (LocalModelDescriptor) -> RuntimeLoadConfig,
    ): Result<LocalModelBenchmarkResult> {
        val requestId = UUID.randomUUID().toString()
        val admission = session.tryBeginBenchmark(requestId)
        if (admission !is InferenceAdmission.Granted) {
            return Result.failure(IllegalStateException("Session is busy with another task or benchmark"))
        }

        try {
            val descriptor = inventoryRepository.resolveModel(modelId)
                ?: return Result.failure(IllegalStateException("Model $modelId is not installed in inventory"))

            val loadConfig = loadConfigFor(descriptor)
            val heapBefore = javaHeapUsageBytes()

            val loadStart = System.currentTimeMillis()
            val loadResult = session.loadModel(
                modelId = descriptor.id,
                modelPath = descriptor.path,
                config = loadConfig,
            ).getOrElse { return Result.failure(it) }
            val loadDuration = System.currentTimeMillis() - loadStart

            val genConfig = RuntimeGenerationConfig(
                prompt = prompt,
                maxTokens = maxOutputTokens,
                priority = InferencePriority.INTERACTIVE,
            )

            val genResult = session.generate(
                config = genConfig,
                benchmarkRequestId = requestId,
            ).getOrElse { return Result.failure(it) }

            val heapDuring = javaHeapUsageBytes()

            return Result.success(
                LocalModelBenchmarkResult(
                    modelId = descriptor.id,
                    displayName = descriptor.displayName,
                    artifactSha256 = descriptor.sha256,
                    artifactBytes = descriptor.bytes,
                    requestedBackend = loadConfig.computeBackend,
                    effectiveBackend = loadResult.activeBackend,
                    requestedContextTokens = loadResult.requestedContextSize
                        ?: loadConfig.contextSize.takeUnless { loadConfig.useModelDefaultContext },
                    effectiveContextTokens = loadResult.effectiveContextSize,
                    loadDurationMs = loadDuration,
                    engineInitDurationMs = session.engineInitDurationMs,
                    conversationCreateDurationMs = session.conversationCreateDurationMs,
                    timeToFirstTokenMs = genResult.timeToFirstTokenMs,
                    generationDurationMs = genResult.durationMs,
                    estimatedTokenCount = genResult.tokenCount,
                    estimatedTokensPerSecond = genResult.tokensPerSecond,
                    responseChars = genResult.text.length,
                    fallbackReason = loadResult.fallbackReason ?: session.fallbackReason,
                    contextFallbackReason = loadResult.contextFallbackReason,
                    responseText = genResult.text,
                    promptText = prompt,
                    reasoningChars = genResult.reasoningText?.length ?: 0,
                    peakHeapBytes = maxOf(heapBefore, heapDuring),
                )
            )
        } finally {
            session.endBenchmark(requestId)
        }
    }

    companion object {
        const val DEFAULT_PROMPT =
            "Explain the difference between a planet and a star in two concise sentences."
        const val DEFAULT_MAX_OUTPUT_TOKENS = 256
    }
}
