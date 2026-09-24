package com.halo.providers.llamacpp.runtime

import android.os.Build
import android.util.Log
import com.halo.providers.llamacpp.ModelPerformanceProfile
import com.halo.sdk.provider.InferencePriority
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.LocalModelRuntime
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.InferenceAdmission
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiFailureDetails
import com.halo.sdk.provider.Recoverability
import kotlinx.coroutines.CancellationException
import java.util.UUID

data class BenchmarkResult(
    val artifactId: String,
    val backend: LocalComputeBackend,
    val gpuLayers: Int,
    val contextSize: Int,
    val loadMs: Long,
    val ttftMs: Long,
    val promptTokensPerSecond: Float,
    val generationTokensPerSecond: Float,
    val totalTokens: Int,
    val durationMs: Long,
    val isStable: Boolean,
    val failureReason: String? = null,
) {
    fun toModelPerformanceProfile(
        artifactFingerprint: String,
        deviceFingerprint: String = "${Build.MANUFACTURER}_${Build.MODEL}_API${Build.VERSION.SDK_INT}",
        threads: Int = 4,
        appVersion: String,
        runtimeVersion: String,
        compatibilityEpoch: Int = 1,
    ): ModelPerformanceProfile = ModelPerformanceProfile(
        artifactId = artifactId,
        artifactFingerprint = artifactFingerprint,
        deviceFingerprint = deviceFingerprint,
        runtime = LocalModelRuntime.LLAMA_CPP,
        backend = backend,
        contextSize = contextSize,
        threads = threads,
        gpuLayers = gpuLayers,
        loadMs = loadMs,
        ttftMs = ttftMs,
        promptTokensPerSecond = promptTokensPerSecond,
        generationTokensPerSecond = generationTokensPerSecond,
        testedAtEpochMs = System.currentTimeMillis(),
        appVersion = appVersion,
        runtimeVersion = runtimeVersion,
        compatibilityEpoch = compatibilityEpoch,
        stable = isStable,
        failureReason = failureReason,
    )
}

/**
 * Executes standardized, non-interactive on-device benchmarks for local machine learning models
 * to assess latency (TTFT), decode speed (TPS), and load stability across compute backends.
 */
class LocalModelBenchmarkRunner(
    private val session: LocalModelSession,
) {

    suspend fun benchmark(
        artifactId: String,
        modelPath: String,
        format: LocalModelFormat = LocalModelFormat.GGUF,
        backend: LocalComputeBackend = LocalComputeBackend.AUTO,
        gpuLayers: Int = 0,
        contextSize: Int = 2048,
        prompt: String = BENCHMARK_PROMPT,
        maxTokens: Int = 64,
    ): Result<BenchmarkResult> {
        val benchmarkRequestId = UUID.randomUUID().toString()
        when (val admission = session.tryBeginBenchmark(benchmarkRequestId)) {
            InferenceAdmission.Granted -> Unit
            is InferenceAdmission.Busy -> return Result.failure(
                AiProviderException(
                    type = AiErrorType.SESSION_CONFLICT,
                    message = "Model is currently in use by ${admission.mode ?: "another session"}",
                    details = AiFailureDetails(
                        activeSessionId = admission.activeRequestId,
                        activeMode = admission.mode,
                        retryAfterMs = admission.retryAfterMs,
                        recoverability = Recoverability.SESSION_CONFLICT,
                    ),
                )
            )
            is InferenceAdmission.Blocked -> return Result.failure(
                AiProviderException(AiErrorType.MODEL_NOT_READY, admission.reason)
            )
        }

        val previousModel = LoadedModelSnapshot.capture(session)
        var benchmarkResult: Result<BenchmarkResult>? = null
        var restoreFailure: Throwable? = null
        try {
            benchmarkResult = benchmarkReserved(
                artifactId = artifactId,
                modelPath = modelPath,
                format = format,
                backend = backend,
                gpuLayers = gpuLayers,
                contextSize = contextSize,
                prompt = prompt,
                maxTokens = maxTokens,
                benchmarkRequestId = benchmarkRequestId,
            )
        } finally {
            try {
                restoreFailure = restorePreviousModel(previousModel)
            } finally {
                session.endBenchmark(benchmarkRequestId)
            }
        }
        return restoreFailure?.let { Result.failure(it) }
            ?: checkNotNull(benchmarkResult)
    }

    private suspend fun benchmarkReserved(
        artifactId: String,
        modelPath: String,
        format: LocalModelFormat,
        backend: LocalComputeBackend,
        gpuLayers: Int,
        contextSize: Int,
        prompt: String,
        maxTokens: Int,
        benchmarkRequestId: String,
    ): Result<BenchmarkResult> {
        val loadStart = System.currentTimeMillis()
        val loadConfig = RuntimeLoadConfig(
            contextSize = contextSize,
            computeBackend = backend,
            gpuLayers = gpuLayers,
        )

        val loadResult = try {
            session.loadModel(
                modelId = artifactId,
                modelPath = modelPath,
                format = format,
                config = loadConfig,
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return Result.failure(t)
        }

        if (loadResult.isFailure) {
            val err = loadResult.exceptionOrNull()
            return Result.success(
                BenchmarkResult(
                    artifactId = artifactId,
                    backend = backend,
                    gpuLayers = gpuLayers,
                    contextSize = contextSize,
                    loadMs = System.currentTimeMillis() - loadStart,
                    ttftMs = 0L,
                    promptTokensPerSecond = 0f,
                    generationTokensPerSecond = 0f,
                    totalTokens = 0,
                    durationMs = 0L,
                    isStable = false,
                    failureReason = err?.message ?: "Load failed",
                )
            )
        }

        val effectiveLoadMs = System.currentTimeMillis() - loadStart
        val effectiveBackend = session.activeBackend ?: backend
        val effectiveGpuLayers = session.activeGpuLayers

        // Run generation benchmark
        val genConfig = RuntimeGenerationConfig(
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = 0.0, // deterministic for benchmarks
            priority = InferencePriority.BACKGROUND,
        )

        var firstTokenTimestamp: Long? = null
        val genStart = System.currentTimeMillis()

        return try {
            val genResult = session.generate(
                config = genConfig,
                onToken = { _ ->
                    val now = System.currentTimeMillis()
                    if (firstTokenTimestamp == null) firstTokenTimestamp = now
                },
                benchmarkRequestId = benchmarkRequestId,
            )

            if (genResult.isFailure) {
                val err = genResult.exceptionOrNull()
                Result.success(
                    BenchmarkResult(
                        artifactId = artifactId,
                        backend = effectiveBackend,
                        gpuLayers = effectiveGpuLayers,
                        contextSize = contextSize,
                        loadMs = effectiveLoadMs,
                        ttftMs = 0L,
                        promptTokensPerSecond = 0f,
                        generationTokensPerSecond = 0f,
                        totalTokens = 0,
                        durationMs = System.currentTimeMillis() - genStart,
                        isStable = false,
                        failureReason = err?.message ?: "Generation failed",
                    )
                )
            } else {
                val res = genResult.getOrThrow()
                val totalGenMs = System.currentTimeMillis() - genStart
                val ttft = if (firstTokenTimestamp != null) firstTokenTimestamp!! - genStart else res.timeToFirstTokenMs
                val decodeMs = (totalGenMs - ttft).coerceAtLeast(1L)
                val decodeTps = if (res.tokenCount > 1) {
                    ((res.tokenCount - 1).toDouble() / (decodeMs.toDouble() / 1000.0)).toFloat()
                } else if (res.tokenCount == 1) {
                    1.0f
                } else {
                    0.0f
                }

                // Prompt tokens/sec approximation based on prompt length and TTFT
                val approxPromptTokens = (prompt.length / 4).coerceAtLeast(1)
                val promptTps = if (ttft > 0) {
                    (approxPromptTokens.toDouble() / (ttft.toDouble() / 1000.0)).toFloat()
                } else {
                    0.0f
                }

                Result.success(
                    BenchmarkResult(
                        artifactId = artifactId,
                        backend = effectiveBackend,
                        gpuLayers = effectiveGpuLayers,
                        contextSize = contextSize,
                        loadMs = effectiveLoadMs,
                        ttftMs = ttft,
                        promptTokensPerSecond = promptTps,
                        generationTokensPerSecond = decodeTps,
                        totalTokens = res.tokenCount,
                        durationMs = totalGenMs,
                        isStable = true,
                        failureReason = null,
                    )
                )
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "Benchmark failed on generation", t)
            Result.failure(t)
        }
    }

    private suspend fun restorePreviousModel(previous: LoadedModelSnapshot?): Throwable? {
        val restoreResult = if (previous == null) {
            session.unloadModel()
        } else {
            session.loadModel(
                modelId = previous.modelId,
                modelPath = previous.modelPath,
                format = previous.format,
                config = previous.config,
            ).map { Unit }
        }
        restoreResult.exceptionOrNull()?.let { error ->
            Log.e(TAG, "Unable to restore the model that was active before benchmarking", error)
        }
        return restoreResult.exceptionOrNull()
    }

    private data class LoadedModelSnapshot(
        val modelId: String,
        val modelPath: String,
        val format: LocalModelFormat,
        val config: RuntimeLoadConfig,
    ) {
        companion object {
            fun capture(session: LocalModelSession): LoadedModelSnapshot? {
                val modelId = session.loadedModelId ?: return null
                val modelPath = session.loadedModelPath ?: return null
                val format = session.activeFormat ?: return null
                val config = session.activeConfig ?: return null
                return LoadedModelSnapshot(modelId, modelPath, format, config)
            }
        }
    }

    companion object {
        private const val TAG = "ModelBenchmarkRunner"
        const val BENCHMARK_PROMPT = "Explain in two sentences what happens during photosynthesis."
    }
}
