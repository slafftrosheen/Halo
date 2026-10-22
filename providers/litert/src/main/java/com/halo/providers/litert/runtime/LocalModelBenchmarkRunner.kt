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
    val cpuThreads: Int = 4,
    val gpuLayers: Int = 0,
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

enum class BenchmarkFailureCode { LOAD_FAILED, GENERATION_FAILED, TIMEOUT, RESOURCE_BUSY, ARTIFACT_MISMATCH }

class LocalModelBenchmarkFailure(
    val code: BenchmarkFailureCode,
    val backend: LocalComputeBackend? = null,
    val contextTokens: Int? = null,
    val cpuThreads: Int = 0,
    val gpuLayers: Int = 0,
    cause: Throwable? = null,
) : Exception(code.name, cause)

class LocalModelBenchmarkRunner(
    private val session: LocalModelSession,
    private val inventoryRepository: LocalModelInventoryRepository,
) {

    suspend fun runBenchmark(
        modelId: String,
        prompt: String = DEFAULT_PROMPT,
        maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
        requestedBackend: LocalComputeBackend? = null,
        generationConfigFor: ((LocalModelDescriptor) -> RuntimeGenerationConfig)? = null,
        freshEngine: Boolean = false,
        loadConfigFor: suspend (LocalModelDescriptor) -> RuntimeLoadConfig,
        onStageChanged: (String) -> Unit = {},
    ): Result<LocalModelBenchmarkResult> {
        val requestId = UUID.randomUUID().toString()
        val admission = session.tryBeginBenchmark(requestId)
        if (admission !is InferenceAdmission.Granted) {
            return Result.failure(LocalModelBenchmarkFailure(BenchmarkFailureCode.RESOURCE_BUSY))
        }

        try {
            if (freshEngine) session.unloadModel().getOrThrow()
            val descriptor = inventoryRepository.resolveModel(modelId)
                ?: return Result.failure(LocalModelBenchmarkFailure(BenchmarkFailureCode.ARTIFACT_MISMATCH))

            val loadConfig = loadConfigFor(descriptor)
            val heapBefore = javaHeapUsageBytes()

            val loadStart = System.currentTimeMillis()
            onStageChanged("LOAD")
            val loadResult = session.loadModel(
                modelId = descriptor.id,
                modelPath = descriptor.path,
                config = loadConfig,
                artifactSha256 = descriptor.sha256,
            ).getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                return Result.failure(LocalModelBenchmarkFailure(BenchmarkFailureCode.LOAD_FAILED,
                    cpuThreads = loadConfig.cpuThreads, gpuLayers = loadConfig.gpuLayers, cause = it))
            }
            val loadDuration = System.currentTimeMillis() - loadStart

            val genConfig = generationConfigFor?.invoke(descriptor) ?: RuntimeGenerationConfig(
                thinkingEnabled = false.takeIf { descriptor.supportsThinking },
                prompt = prompt,
                maxTokens = maxOutputTokens,
                priority = InferencePriority.INTERACTIVE,
            )

            onStageChanged("EXECUTE")
            val genResult = session.generate(
                config = genConfig,
                benchmarkRequestId = requestId,
            ).getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                return Result.failure(LocalModelBenchmarkFailure(BenchmarkFailureCode.GENERATION_FAILED,
                    backend = session.activeBackend, contextTokens = session.effectiveContextSize,
                    cpuThreads = loadConfig.cpuThreads, gpuLayers = loadResult.activeGpuLayers, cause = it))
            }

            val heapDuring = javaHeapUsageBytes()

            return Result.success(
                LocalModelBenchmarkResult(
                    modelId = descriptor.id,
                    displayName = descriptor.displayName,
                    artifactSha256 = descriptor.sha256,
                    artifactBytes = descriptor.bytes,
                    requestedBackend = requestedBackend ?: loadConfig.computeBackend,
                    effectiveBackend = session.activeBackend ?: loadResult.activeBackend,
                    requestedContextTokens = loadResult.requestedContextSize
                        ?: loadConfig.contextSize.takeUnless { loadConfig.useModelDefaultContext },
                    effectiveContextTokens = session.effectiveContextSize,
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
                    cpuThreads = session.activeConfig?.cpuThreads ?: loadConfig.cpuThreads,
                    gpuLayers = session.activeGpuLayers,
                )
            )
        } finally {
            try {
                if (freshEngine) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    session.unloadModel().getOrThrow()
                }
            } finally {
                session.endBenchmark(requestId)
            }
        }
    }

    companion object {
        const val DEFAULT_PROMPT =
            "Explain the difference between a planet and a star in two concise sentences."
        const val DEFAULT_MAX_OUTPUT_TOKENS = 256
    }
}
