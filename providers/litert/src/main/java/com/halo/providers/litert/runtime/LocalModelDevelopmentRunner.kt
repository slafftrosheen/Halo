package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.provider.InferenceAdmission
import com.halo.sdk.provider.InferencePriority
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeLoadConfig
import kotlinx.coroutines.CancellationException
import java.util.UUID

/** One prompt/response pair inside a smoke test. */
data class SmokeStep(
    val prompt: String,
    val responseText: String,
    val estimatedTokenCount: Int,
    val timeToFirstTokenMs: Long,
    val durationMs: Long,
    val failureStage: String? = null,
    val failureDetail: String? = null,
) {
    val producedOutput: Boolean get() = responseText.isNotBlank()
}

/**
 * Result of the two-prompt smoke test.
 *
 * Success means: engine initialized, conversation created, generation began, non-empty output
 * arrived, and a *second* generation succeeded without a process restart. The exact string
 * "HALO_OK" is deliberately not required — small models may not follow the instruction — but the
 * raw response is recorded so the operator can see what actually happened.
 */
data class LocalModelSmokeTestResult(
    val modelId: String,
    val displayName: String,
    val artifactSha256: String?,
    val loaded: Boolean,
    val conversationReady: Boolean,
    val producedOutput: Boolean,
    val repeatedGeneration: Boolean,
    val effectiveBackend: LocalComputeBackend?,
    val failureStage: String?,
    val failureDetail: String?,
    val firstStep: SmokeStep?,
    val secondStep: SmokeStep?,
) {
    val success: Boolean get() = loaded && producedOutput && repeatedGeneration

    fun summaryLine(): String = buildString {
        append("model=$modelId sha=${artifactSha256?.take(12) ?: "unknown"} ")
        append("loaded=$loaded conversationReady=$conversationReady ")
        append("output=$producedOutput repeated=$repeatedGeneration backend=${effectiveBackend?.name ?: "none"}")
        failureStage?.let { append(" failureStage=$it") }
    }
}

/** One row of the development model matrix table. */
data class LocalModelMatrixRow(
    val modelId: String,
    val displayName: String,
    val artifactVerified: Boolean,
    val loadMs: Long?,
    val backend: String?,
    val ttftMs: Long?,
    val estimatedTokenCount: Int?,
    val responsePreview: String,
    val result: String,
    val failureDetail: String? = null,
)

/**
 * Development-only runners.
 *
 * [smokeTest] proves a single model can produce output twice in one residency. [runMatrix] walks
 * every installed model serially, never keeping two models resident, and never lets one failing
 * artifact abort the remaining tests. Neither runner triggers speech synthesis.
 */
class LocalModelDevelopmentRunner(
    private val session: LocalModelSession,
    private val inventoryRepository: LocalModelInventoryRepository,
) {

    suspend fun smokeTest(
        modelId: String,
        loadConfigFor: (LocalModelDescriptor) -> RuntimeLoadConfig,
    ): LocalModelSmokeTestResult {
        val descriptor = inventoryRepository.resolveModel(modelId)
            ?: return LocalModelSmokeTestResult(
                modelId = modelId,
                displayName = modelId,
                artifactSha256 = null,
                loaded = false,
                conversationReady = false,
                producedOutput = false,
                repeatedGeneration = false,
                effectiveBackend = null,
                failureStage = STAGE_LOAD,
                failureDetail = "Model is not installed",
                firstStep = null,
                secondStep = null,
            )

        val requestId = UUID.randomUUID().toString()
        val admission = session.tryBeginBenchmark(requestId)
        if (admission !is InferenceAdmission.Granted) {
            return failedResult(descriptor, STAGE_BUSY, "Session is busy with another task or benchmark")
        }

        try {
            val loadResult = session.loadModel(
                modelId = descriptor.id,
                modelPath = descriptor.path,
                artifactSha256 = descriptor.sha256,
                config = loadConfigFor(descriptor),
            )
            if (loadResult.isFailure) {
                val error = loadResult.exceptionOrNull()
                return failedResult(descriptor, stageName(error), error?.message)
            }

            val first = runStep(
                supportsThinking = descriptor.supportsThinking,
                prompt = SMOKE_PROMPT,
                maxTokens = SMOKE_MAX_OUTPUT_TOKENS,
                temperature = 0.0,
                requestId = requestId,
            )
            val second = runStep(
                supportsThinking = descriptor.supportsThinking,
                prompt = FOLLOW_UP_PROMPT,
                maxTokens = FOLLOW_UP_MAX_OUTPUT_TOKENS,
                temperature = 0.0,
                requestId = requestId,
            )

            val failureStage = first.failureStage ?: second.failureStage
            val conversationReady = failureStage != ModelLoadStage.CONVERSATION.name
            return LocalModelSmokeTestResult(
                modelId = descriptor.id,
                displayName = descriptor.displayName,
                artifactSha256 = descriptor.sha256,
                loaded = true,
                conversationReady = conversationReady,
                producedOutput = first.producedOutput,
                repeatedGeneration = second.producedOutput && second.failureStage == null,
                effectiveBackend = session.activeBackend,
                failureStage = failureStage,
                failureDetail = first.failureDetail ?: second.failureDetail,
                firstStep = first,
                secondStep = second,
            )
        } finally {
            session.endBenchmark(requestId)
            runCatching { session.unloadModel() }
        }
    }

    /**
     * Runs the smoke prompt across every supplied model, serially, unloading between models.
     * A failure returns a row instead of aborting the walk.
     */
    suspend fun runMatrix(
        descriptors: List<LocalModelDescriptor>,
        loadConfigFor: (LocalModelDescriptor) -> RuntimeLoadConfig,
        onRow: (LocalModelMatrixRow) -> Unit = {},
    ): List<LocalModelMatrixRow> {
        val rows = mutableListOf<LocalModelMatrixRow>()
        for (descriptor in descriptors) {
            val row = try {
                runSingle(descriptor, loadConfigFor)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                LocalModelMatrixRow(
                    modelId = descriptor.id,
                    displayName = descriptor.displayName,
                    artifactVerified = descriptor.verified,
                    loadMs = null,
                    backend = null,
                    ttftMs = null,
                    estimatedTokenCount = null,
                    responsePreview = "",
                    result = stageName(error) ?: STAGE_UNKNOWN,
                    failureDetail = error.message,
                )
            }
            rows += row
            onRow(row)
            // Exactly one LiteRT engine may own model weights at a time.
            runCatching { session.unloadModel() }
        }
        return rows
    }

    private suspend fun runSingle(
        descriptor: LocalModelDescriptor,
        loadConfigFor: (LocalModelDescriptor) -> RuntimeLoadConfig,
    ): LocalModelMatrixRow {
        val requestId = UUID.randomUUID().toString()
        val admission = session.tryBeginBenchmark(requestId)
        if (admission !is InferenceAdmission.Granted) {
            return LocalModelMatrixRow(
                modelId = descriptor.id,
                displayName = descriptor.displayName,
                artifactVerified = descriptor.verified,
                loadMs = null,
                backend = null,
                ttftMs = null,
                estimatedTokenCount = null,
                responsePreview = "",
                result = STAGE_BUSY,
                failureDetail = "Session busy",
            )
        }
        try {
            val loadStart = System.currentTimeMillis()
            val loadResult = session.loadModel(
                modelId = descriptor.id,
                modelPath = descriptor.path,
                artifactSha256 = descriptor.sha256,
                config = loadConfigFor(descriptor),
            )
            if (loadResult.isFailure) {
                val error = loadResult.exceptionOrNull()
                return LocalModelMatrixRow(
                    modelId = descriptor.id,
                    displayName = descriptor.displayName,
                    artifactVerified = descriptor.verified,
                    loadMs = System.currentTimeMillis() - loadStart,
                    backend = null,
                    ttftMs = null,
                    estimatedTokenCount = null,
                    responsePreview = "",
                    result = stageName(error) ?: STAGE_LOAD,
                    failureDetail = error?.message?.take(200),
                )
            }
            val loadMs = System.currentTimeMillis() - loadStart

            val step = runStep(
                supportsThinking = descriptor.supportsThinking,
                prompt = SMOKE_PROMPT,
                maxTokens = SMOKE_MAX_OUTPUT_TOKENS,
                temperature = 0.0,
                requestId = requestId,
            )
            return LocalModelMatrixRow(
                modelId = descriptor.id,
                displayName = descriptor.displayName,
                artifactVerified = descriptor.verified,
                loadMs = loadMs,
                backend = session.activeBackend?.name,
                ttftMs = step.timeToFirstTokenMs,
                estimatedTokenCount = step.estimatedTokenCount,
                responsePreview = step.responseText.take(80).replace('\n', ' '),
                result = step.failureStage ?: if (step.producedOutput) "OK" else STAGE_NO_OUTPUT,
                failureDetail = step.failureDetail,
            )
        } finally {
            session.endBenchmark(requestId)
        }
    }

    private suspend fun runStep(
        supportsThinking: Boolean,
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        requestId: String,
    ): SmokeStep {
        val start = System.currentTimeMillis()
        return try {
            val result = session.generate(
                config = RuntimeGenerationConfig(
                    prompt = prompt,
                    thinkingEnabled = false.takeIf { supportsThinking },
                    maxTokens = maxTokens,
                    temperature = temperature,
                    priority = InferencePriority.INTERACTIVE,
                ),
                benchmarkRequestId = requestId,
            )
            result.fold(
                onSuccess = { generated ->
                    SmokeStep(
                        prompt = prompt,
                        responseText = generated.text,
                        estimatedTokenCount = generated.tokenCount,
                        timeToFirstTokenMs = generated.timeToFirstTokenMs,
                        durationMs = generated.durationMs,
                    )
                },
                onFailure = { error ->
                    SmokeStep(
                        prompt = prompt,
                        responseText = "",
                        estimatedTokenCount = 0,
                        timeToFirstTokenMs = 0L,
                        durationMs = System.currentTimeMillis() - start,
                        failureStage = stageName(error) ?: STAGE_GENERATION,
                        failureDetail = error.message?.take(200),
                    )
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            SmokeStep(
                prompt = prompt,
                responseText = "",
                estimatedTokenCount = 0,
                timeToFirstTokenMs = 0L,
                durationMs = System.currentTimeMillis() - start,
                failureStage = stageName(error) ?: STAGE_GENERATION,
                failureDetail = error.message?.take(200),
            )
        }
    }

    private fun failedResult(
        descriptor: LocalModelDescriptor,
        stage: String?,
        detail: String?,
    ) = LocalModelSmokeTestResult(
        modelId = descriptor.id,
        displayName = descriptor.displayName,
        artifactSha256 = descriptor.sha256,
        loaded = false,
        conversationReady = false,
        producedOutput = false,
        repeatedGeneration = false,
        effectiveBackend = null,
        failureStage = stage,
        failureDetail = detail?.take(200),
        firstStep = null,
        secondStep = null,
    )

    /** Maps a native failure to the failure stage Halo reports. */
    internal fun stageName(error: Throwable?): String? = when (error) {
        null -> null
        is LiteRtModelLoadException -> when (error.stage) {
            ModelLoadStage.CONVERSATION -> ModelLoadStage.CONVERSATION.name
            ModelLoadStage.FIRST_TOKEN -> ModelLoadStage.FIRST_TOKEN.name
            ModelLoadStage.GENERATION -> ModelLoadStage.GENERATION.name
            else -> ModelLoadStage.ENGINE_INIT.name
        }
        else -> error::class.java.simpleName
    }

    companion object {
        const val SMOKE_PROMPT = "Reply with exactly: HALO_OK"
        const val SMOKE_MAX_OUTPUT_TOKENS = 16
        const val FOLLOW_UP_PROMPT = "What is 2+2? Answer briefly."
        const val FOLLOW_UP_MAX_OUTPUT_TOKENS = 32
        const val STAGE_LOAD = "LOAD"
        const val STAGE_BUSY = "BUSY"
        const val STAGE_NO_OUTPUT = "NO_OUTPUT"
        const val STAGE_GENERATION = "GENERATION"
        const val STAGE_UNKNOWN = "UNKNOWN"

        /** Compact operator-facing table for logs/screenshots. */
        fun formatTable(rows: List<LocalModelMatrixRow>): String = buildString {
            appendLine("model | verified | loadMs | backend | ttftMs | response | result")
            rows.forEach { row ->
                append(row.modelId)
                append(" | ")
                append(if (row.artifactVerified) "yes" else "no")
                append(" | ")
                append(row.loadMs ?: "-")
                append(" | ")
                append(row.backend ?: "-")
                append(" | ")
                append(row.ttftMs ?: "-")
                append(" | ")
                append(row.responsePreview.ifBlank { "-" })
                append(" | ")
                append(row.result)
                appendLine()
            }
        }
    }
}
