package com.halo.providers.litert.runtime

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.runtime.NativeResourceSnapshot
import com.halo.sdk.runtime.NativeResourceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * MINING-P0-001 the diagnostic ladder below `LiteRtLocalProvider` and below `AiProviderRouter`.
 *
 * A direct control probe answers one question the existing probes cannot: does the raw LiteRT SDK
 * core path (Engine -> Conversation -> first token -> completion) work for this artifact on this
 * device? It deliberately involves no provider router, cognition, memory, tools, agent loop or
 * speech.
 */
enum class LiteRtControlProbeStage {
    ARTIFACT,
    ABI,
    ENGINE_INIT,
    CONVERSATION_CREATE,
    FIRST_TOKEN,
    COMPLETION,
    TEARDOWN,
}

enum class LiteRtControlProbeStatus {
    NOT_RUN,
    RUNNING,
    PASSED,
    FAILED,
    BLOCKED,
}

/**
 * One stage outcome. Never stores generated content: only whether output existed and matched.
 */
data class LiteRtControlProbeStageResult(
    val stage: LiteRtControlProbeStage,
    val status: LiteRtControlProbeStatus,
    val durationMs: Long? = null,
    val exceptionClass: String? = null,
    val boundedDetail: String? = null,
)

/**
 * Structured control-probe outcome. This is a diagnostic fact only: it never auto-selects a model,
 * changes a provider, or marks remote providers. "Raw LiteRT works" stays separate from
 * "Halo provider works".
 */
data class LiteRtControlProbeResult(
    val modelId: String,
    val artifactFingerprint: String?,
    val requestedBackend: LocalComputeBackend,
    val effectiveBackend: LocalComputeBackend?,
    val requestedContextTokens: Int?,
    val effectiveContextTokens: Int?,
    val stages: List<LiteRtControlProbeStageResult>,
    val startedAtMs: Long,
    val totalDurationMs: Long,
    /** Separate diagnostic fact; deliberately not folded into generation-proven state. */
    val controlProbePassed: Boolean,
) {
    val blocked: Boolean get() = stages.any { it.status == LiteRtControlProbeStatus.BLOCKED }

    fun stage(stage: LiteRtControlProbeStage): LiteRtControlProbeStageResult? =
        stages.firstOrNull { it.stage == stage }

    fun summaryLine(): String = buildString {
        append("control-probe model=$modelId result=")
        append(
            when {
                controlProbePassed -> "PASSED"
                blocked -> "BLOCKED"
                else -> "FAILED"
            },
        )
        append(" requested=${LiteRtEngineConfigFactory.backendName(requestedBackend)}")
        effectiveBackend?.let { append(" effective=${LiteRtEngineConfigFactory.backendName(it)}") }
        append(" context=${requestedContextTokens ?: "model-default"}")
        effectiveContextTokens?.let { append("/$it") }
        stage(LiteRtControlProbeStage.ENGINE_INIT)?.durationMs?.let { append(" engineInitMs=$it") }
        stage(LiteRtControlProbeStage.CONVERSATION_CREATE)?.durationMs?.let { append(" conversationMs=$it") }
        stage(LiteRtControlProbeStage.FIRST_TOKEN)?.durationMs?.let { append(" firstTokenMs=$it") }
        append(" totalMs=$totalDurationMs")
        stages.firstOrNull { it.status == LiteRtControlProbeStatus.FAILED }?.let {
            append(" failedStage=${it.stage} type=${it.exceptionClass ?: "unknown"}")
        }
        stages.firstOrNull { it.status == LiteRtControlProbeStatus.BLOCKED }?.let {
            append(" blocked=${it.boundedDetail ?: it.stage.name}")
        }
    }
}

/**
 * Runs the bounded raw-SDK control probe.
 *
 * Resource safety: a direct probe must never spin up a second giant engine while another native
 * inference is active. If any heavyweight native resource is ACQUIRING/LOADING/IN_USE the probe is
 * BLOCKED. If a model is merely warm RESIDENT the probe is also BLOCKED (smallest semantic change:
 * the operator explicitly unloads first) rather than silently duplicating the model in memory.
 */
class LiteRtControlProbeRunner(
    context: Context,
    private val inventoryRepository: LocalModelInventoryRepository,
    private val loadConfigFor: (LocalModelDescriptor) -> RuntimeLoadConfig,
    /** Snapshot of the process-wide native resource fabric; supplied by the caller's coordinator. */
    private val nativeResourceSnapshot: () -> List<NativeResourceSnapshot> = { emptyList() },
    /** Currently warm-resident model id, if any. Resident != active execution. */
    private val residentModelId: () -> String? = { null },
) {
    private val filesDir = context.applicationContext.filesDir

    /** Stages recorded before the conversation probe, plus the first short-circuit result if any. */
    private class Preflight(
        val descriptor: LocalModelDescriptor?,
        val config: RuntimeLoadConfig,
        val stages: MutableList<LiteRtControlProbeStageResult>,
        val shortCircuit: LiteRtControlProbeResult?,
    )

    private class ConversationProbe(
        val result: LiteRtControlProbeResult,
        val conversation: Conversation?,
    )

    suspend fun run(modelId: String): LiteRtControlProbeResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val preflight = preflight(modelId, startedAt)
        preflight.shortCircuit?.let { return@withContext it }

        val descriptor = preflight.descriptor!!
        val config = preflight.config
        val stages = preflight.stages
        val requestedContext = if (config.useModelDefaultContext) null else config.contextSize
        val fingerprint = descriptor.sha256 ?: "id:${descriptor.id}:bytes:${descriptor.bytes}"
        val cacheDirs = LiteRtEngineConfigFactory.cacheDirsFor(filesDir, descriptor.id, fingerprint)

        // Engine init, with the same bounded context ladder the runtime uses.
        var engine: Engine? = null
        var effectiveBackend: LocalComputeBackend? = null
        var effectiveContext: Int? = null
        val engineStart = System.currentTimeMillis()
        var engineFailure: Throwable? = null
        val ladder = LiteRtEngineConfigFactory.contextLadder(requestedContext, config.computeBackend)
        for ((index, contextTokens) in ladder.withIndex()) {
            val cacheDir = when (config.computeBackend) {
                LocalComputeBackend.CPU -> cacheDirs.cpu
                LocalComputeBackend.GPU -> cacheDirs.gpu
                LocalComputeBackend.AUTO -> cacheDirs.gpu
            }
            val attempt = LiteRtEngineConfigFactory.createAndInitialize(
                modelPath = descriptor.path,
                backend = config.computeBackend,
                contextTokens = contextTokens,
                cacheDir = cacheDir,
                visionEnabled = config.visionEnabled,
            )
            if (attempt.isSuccess) {
                engine = attempt.getOrThrow()
                effectiveBackend = config.computeBackend
                effectiveContext = contextTokens
                break
            }
            val error = attempt.exceptionOrNull()
            engineFailure = error
            Log.w(
                TAG,
                "$EVENT_PROBE model=$modelId stage=ENGINE_INIT result=FAILED " +
                    "context=${contextTokens ?: "model-default"} type=${error?.let { it::class.java.simpleName }}",
            )
            val retryable = LiteRtEngineConfigFactory.isMemoryAllocationFailure(error) &&
                config.computeBackend == LocalComputeBackend.AUTO &&
                index != ladder.lastIndex
            if (!retryable) break
        }
        val engineDuration = System.currentTimeMillis() - engineStart
        if (engine == null) {
            stages.add(
                LiteRtControlProbeStageResult(
                    LiteRtControlProbeStage.ENGINE_INIT,
                    LiteRtControlProbeStatus.FAILED,
                    durationMs = engineDuration,
                    exceptionClass = engineFailure?.let { it::class.java.simpleName },
                    boundedDetail = LiteRtEngineConfigFactory.bounded(engineFailure),
                ),
            )
            return@withContext buildResult(
                modelId, descriptor, config, requestedContext, null, stages, startedAt, false,
            )
        }
        stages.add(
            LiteRtControlProbeStageResult(
                LiteRtControlProbeStage.ENGINE_INIT,
                LiteRtControlProbeStatus.PASSED,
                durationMs = engineDuration,
                boundedDetail = "effective=${LiteRtEngineConfigFactory.backendName(effectiveBackend!!)} " +
                    "context=${effectiveContext ?: "model-default"}",
            ),
        )

        val probe = probeConversation(
            modelId = modelId,
            engine = engine,
            config = config,
            requestedContext = requestedContext,
            effectiveBackend = effectiveBackend,
            effectiveContext = effectiveContext,
            stages = stages,
            startedAt = startedAt,
        )

        // Teardown always runs and is always recorded, including for blocked/failed probes.
        val teardownStart = System.currentTimeMillis()
        closeConversationQuietly(probe.conversation)
        closeEngineQuietly(engine)
        val withTeardown = probe.result.copy(
            stages = probe.result.stages + LiteRtControlProbeStageResult(
                LiteRtControlProbeStage.TEARDOWN,
                LiteRtControlProbeStatus.PASSED,
                durationMs = System.currentTimeMillis() - teardownStart,
            ),
        )
        Log.i(
            TAG,
            "$EVENT_PROBE model=$modelId completed passed=${withTeardown.controlProbePassed} " +
                "totalMs=${withTeardown.totalDurationMs}",
        )
        withTeardown
    }

    private suspend fun preflight(modelId: String, startedAt: Long): Preflight {
        val stages = mutableListOf<LiteRtControlProbeStageResult>()

        // Explicit artifact resolution. "Direct" never means "skip safety".
        val artifactStart = System.currentTimeMillis()
        val descriptor = inventoryRepository.resolveModel(modelId)
        if (descriptor == null) {
            stages.add(
                LiteRtControlProbeStageResult(
                    LiteRtControlProbeStage.ARTIFACT,
                    LiteRtControlProbeStatus.FAILED,
                    durationMs = System.currentTimeMillis() - artifactStart,
                    boundedDetail = "Model is not installed",
                ),
            )
            return Preflight(null, RuntimeLoadConfig(), stages, buildResult(
                modelId, null, RuntimeLoadConfig(), null, null, stages, startedAt, false,
            ))
        }
        if (!descriptor.valid) {
            stages.add(
                LiteRtControlProbeStageResult(
                    LiteRtControlProbeStage.ARTIFACT,
                    LiteRtControlProbeStatus.FAILED,
                    durationMs = System.currentTimeMillis() - artifactStart,
                    boundedDetail = descriptor.validationDetail ?: "Model file failed integrity verification",
                ),
            )
            return Preflight(descriptor, RuntimeLoadConfig(), stages, buildResult(
                modelId, descriptor, RuntimeLoadConfig(), null, null, stages, startedAt, false,
            ))
        }
        stages.add(
            LiteRtControlProbeStageResult(
                LiteRtControlProbeStage.ARTIFACT,
                LiteRtControlProbeStatus.PASSED,
                durationMs = System.currentTimeMillis() - artifactStart,
                boundedDetail = "verified bytes=${descriptor.bytes}",
            ),
        )

        // Native resource safety. Never kill an active interaction to run a diagnostic.
        val busy = nativeResourceSnapshot().filter {
            it.state == NativeResourceState.ACQUIRING ||
                it.state == NativeResourceState.LOADING ||
                it.state == NativeResourceState.IN_USE
        }
        if (busy.isNotEmpty()) {
            Log.i(TAG, "$EVENT_PROBE model=$modelId result=BLOCKED reason=heavy_resource_active")
            stages.add(
                LiteRtControlProbeStageResult(
                    LiteRtControlProbeStage.ENGINE_INIT,
                    LiteRtControlProbeStatus.BLOCKED,
                    boundedDetail = "Heavy native resource currently active " +
                        "owner=${busy.first().ownerId} state=${busy.first().state}",
                ),
            )
            return Preflight(descriptor, RuntimeLoadConfig(), stages, buildResult(
                modelId, descriptor, RuntimeLoadConfig(), null, null, stages, startedAt, false,
            ))
        }
        if (residentModelId() != null) {
            Log.i(TAG, "$EVENT_PROBE model=$modelId result=BLOCKED reason=resident_model")
            stages.add(
                LiteRtControlProbeStageResult(
                    LiteRtControlProbeStage.ENGINE_INIT,
                    LiteRtControlProbeStatus.BLOCKED,
                    boundedDetail = "A local model is resident; unload it before a direct runtime probe",
                ),
            )
            return Preflight(descriptor, RuntimeLoadConfig(), stages, buildResult(
                modelId, descriptor, RuntimeLoadConfig(), null, null, stages, startedAt, false,
            ))
        }

        // ABI preflight, shared with the production runtime.
        val abiIssue = LiteRtEngineConfigFactory.abiBlocker()
        if (abiIssue != null) {
            stages.add(
                LiteRtControlProbeStageResult(
                    LiteRtControlProbeStage.ABI,
                    LiteRtControlProbeStatus.FAILED,
                    boundedDetail = abiIssue,
                ),
            )
            return Preflight(descriptor, RuntimeLoadConfig(), stages, buildResult(
                modelId, descriptor, RuntimeLoadConfig(), null, null, stages, startedAt, false,
            ))
        }
        stages.add(LiteRtControlProbeStageResult(LiteRtControlProbeStage.ABI, LiteRtControlProbeStatus.PASSED))

        return Preflight(descriptor, loadConfigFor(descriptor), stages, null)
    }

    /**
     * Conversation create + prompt + first token + bounded completion. Never closes native
     * resources: the caller owns teardown so it is always recorded.
     */
    private suspend fun probeConversation(
        modelId: String,
        engine: Engine,
        config: RuntimeLoadConfig,
        requestedContext: Int?,
        effectiveBackend: LocalComputeBackend?,
        effectiveContext: Int?,
        stages: MutableList<LiteRtControlProbeStageResult>,
        startedAt: Long,
    ): ConversationProbe {
        val descriptor = inventoryRepository.resolveModel(modelId)
        var conversation: Conversation? = null
        fun result(passed: Boolean) = ConversationProbe(
            buildResult(
                modelId, descriptor, config,
                requestedContext, effectiveContext, stages, startedAt, passed, effectiveBackend,
            ),
            conversation,
        )

        // Conversation create. Deterministic sampler, no tools, no history, no memory.
        val conversationStart = System.currentTimeMillis()
        conversation = try {
            engine.createConversation(
                ConversationConfig(
                    systemInstruction = null,
                    initialMessages = emptyList(),
                    tools = emptyList(),
                    // Deterministic equivalent: greedy sampling.
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                    // SECURITY INVARIANT: a diagnostic probe must not become a tool executor.
                    automaticToolCalling = false,
                    maxOutputToken = PROBE_MAX_OUTPUT_TOKENS,
                ),
            )
        } catch (error: Throwable) {
            stages.add(
                LiteRtControlProbeStageResult(
                    LiteRtControlProbeStage.CONVERSATION_CREATE,
                    LiteRtControlProbeStatus.FAILED,
                    durationMs = System.currentTimeMillis() - conversationStart,
                    exceptionClass = error::class.java.simpleName,
                    boundedDetail = LiteRtEngineConfigFactory.bounded(error),
                ),
            )
            return result(false)
        }
        stages.add(
            LiteRtControlProbeStageResult(
                LiteRtControlProbeStage.CONVERSATION_CREATE,
                LiteRtControlProbeStatus.PASSED,
                durationMs = System.currentTimeMillis() - conversationStart,
            ),
        )

        // First token + bounded completion.
        val generationStart = System.currentTimeMillis()
        var firstTokenAt: Long? = null
        var outputChars = 0
        val text = StringBuilder()
        try {
            val flow = conversation.sendMessageAsync(
                Message.user(Contents.of(Content.Text(PROBE_PROMPT))),
                maxOutputToken = PROBE_MAX_OUTPUT_TOKENS,
            )
            flow.collect { message ->
                val chunk = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                if (chunk.isEmpty()) return@collect
                if (firstTokenAt == null) {
                    firstTokenAt = System.currentTimeMillis()
                    stages.add(
                        LiteRtControlProbeStageResult(
                            LiteRtControlProbeStage.FIRST_TOKEN,
                            LiteRtControlProbeStatus.PASSED,
                            durationMs = firstTokenAt!! - generationStart,
                        ),
                    )
                }
                outputChars += chunk.length
                text.append(chunk)
            }
        } catch (error: Throwable) {
            stages.add(
                LiteRtControlProbeStageResult(
                    if (firstTokenAt == null) LiteRtControlProbeStage.FIRST_TOKEN
                    else LiteRtControlProbeStage.COMPLETION,
                    LiteRtControlProbeStatus.FAILED,
                    durationMs = System.currentTimeMillis() - generationStart,
                    exceptionClass = error::class.java.simpleName,
                    boundedDetail = LiteRtEngineConfigFactory.bounded(error),
                ),
            )
            return result(false)
        }

        if (firstTokenAt == null) {
            stages.add(
                LiteRtControlProbeStageResult(
                    LiteRtControlProbeStage.FIRST_TOKEN,
                    LiteRtControlProbeStatus.FAILED,
                    durationMs = System.currentTimeMillis() - generationStart,
                    boundedDetail = "The engine produced no token",
                ),
            )
            return result(false)
        }

        // Only presence and expectation match are retained: never the generated text.
        val passed = outputChars > 0
        stages.add(
            LiteRtControlProbeStageResult(
                LiteRtControlProbeStage.COMPLETION,
                LiteRtControlProbeStatus.PASSED,
                durationMs = System.currentTimeMillis() - generationStart,
                boundedDetail = "outputNonEmpty=${outputChars > 0} " +
                    "outputMatchedExpected=${text.toString().trim().contains(PROBE_EXPECTED)}",
            ),
        )
        return result(passed)
    }

    private fun buildResult(
        modelId: String,
        descriptor: LocalModelDescriptor?,
        config: RuntimeLoadConfig,
        requestedContext: Int?,
        effectiveContext: Int?,
        stages: List<LiteRtControlProbeStageResult>,
        startedAt: Long,
        passed: Boolean,
        effectiveBackend: LocalComputeBackend? = null,
    ): LiteRtControlProbeResult = LiteRtControlProbeResult(
        modelId = modelId,
        artifactFingerprint = descriptor?.sha256,
        requestedBackend = config.computeBackend,
        effectiveBackend = effectiveBackend,
        requestedContextTokens = requestedContext,
        effectiveContextTokens = effectiveContext,
        stages = stages.toList(),
        startedAtMs = startedAt,
        totalDurationMs = System.currentTimeMillis() - startedAt,
        controlProbePassed = passed,
    )

    /** Releasing weights takes seconds; running it in a cancelled coroutine can abort the native close. */
    private suspend fun closeEngineQuietly(target: Engine?) {
        if (target == null) return
        withContext(NonCancellable + Dispatchers.Default) {
            if (target.isInitialized()) runCatching { target.close() }
        }
    }

    private suspend fun closeConversationQuietly(target: Conversation?) {
        if (target == null) return
        withContext(NonCancellable + Dispatchers.Default) {
            runCatching { target.cancelProcess() }
            runCatching { target.close() }
        }
    }

    companion object {
        private const val TAG = "LiteRtControlProbe"

        const val EVENT_PROBE = "HALO_LITERT_CONTROL_PROBE"
        const val PROBE_PROMPT = "Reply exactly HALO_OK"
        const val PROBE_EXPECTED = "HALO_OK"
        const val PROBE_MAX_OUTPUT_TOKENS = 8
    }
}
