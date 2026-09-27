package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.provider.InferenceAdmission
import com.halo.sdk.provider.InferencePriority
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeLoadConfig
import java.util.UUID

data class LocalModelBenchmarkResult(
    val modelId: String,
    val loadDurationMs: Long,
    val timeToFirstTokenMs: Long,
    val generationDurationMs: Long,
    val tokenCount: Int,
    val tokensPerSecond: Double,
    val effectiveBackend: LocalComputeBackend,
    val promptText: String,
    val responseText: String,
)

class LocalModelBenchmarkRunner(
    private val session: LocalModelSession,
    private val inventoryRepository: LocalModelInventoryRepository,
) {

    suspend fun runBenchmark(
        modelId: String,
        prompt: String = "Explain the difference between a planet and a star in two concise sentences.",
    ): Result<LocalModelBenchmarkResult> {
        val requestId = UUID.randomUUID().toString()
        val admission = session.tryBeginBenchmark(requestId)
        if (admission !is InferenceAdmission.Granted) {
            return Result.failure(IllegalStateException("Session is busy with another task or benchmark"))
        }

        try {
            val descriptor = inventoryRepository.resolveModel(modelId)
                ?: return Result.failure(IllegalStateException("Model $modelId is not installed in inventory"))

            val loadStart = System.currentTimeMillis()
            val loadConfig = RuntimeLoadConfig(
                contextSize = descriptor.contextWindowTokens,
                computeBackend = LocalComputeBackend.AUTO,
                useModelDefaultContext = descriptor.useModelDefaultContext,
            )

            val loadResult = session.loadModel(
                modelId = descriptor.id,
                modelPath = descriptor.path,
                config = loadConfig
            ).getOrElse { return Result.failure(it) }

            val loadDuration = System.currentTimeMillis() - loadStart

            val genConfig = RuntimeGenerationConfig(
                prompt = prompt,
                maxTokens = 256,
                priority = InferencePriority.INTERACTIVE
            )

            val genResult = session.generate(
                config = genConfig,
                benchmarkRequestId = requestId
            ).getOrElse { return Result.failure(it) }

            return Result.success(
                LocalModelBenchmarkResult(
                    modelId = descriptor.id,
                    loadDurationMs = loadDuration,
                    timeToFirstTokenMs = genResult.timeToFirstTokenMs,
                    generationDurationMs = genResult.durationMs,
                    tokenCount = genResult.tokenCount,
                    tokensPerSecond = genResult.tokensPerSecond,
                    effectiveBackend = loadResult.activeBackend,
                    promptText = prompt,
                    responseText = genResult.text,
                )
            )
        } finally {
            session.endBenchmark(requestId)
        }
    }
}
