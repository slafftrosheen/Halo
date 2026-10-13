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
 * DOGFOOD-005 GPU stability probe: `requiredConsecutiveSuccesses` complete, isolated,
 * single-prompt control-probe sequences on the GPU. Only a full pass flips the backend from
 * UNQUALIFIED/QUARANTINED to QUALIFIED; any single failure or a CPU fallback inside a sequence
 * fails the probe without penalizing the GPU.
 */
data class LiteRtGpuStabilityProbeResult(
    val modelId: String,
    val artifactSha256: String?,
    val backend: LocalComputeBackend,
    val requiredConsecutiveSuccesses: Int,
    val completedGenerations: Int,
    val passed: Boolean,
    val failureStage: LiteRtControlProbeStage?,
    val failureDetail: String?,
    val startedAtMs: Long,
    val totalDurationMs: Long,
) {
    fun summaryLine(): String = buildString {
        append("gpu-stability-probe model=$modelId result=")
        append(if (passed) "PASSED" else "FAILED")
        append(" backend=${LiteRtEngineConfigFactory.backendName(backend)}")
        append(" sequences=$completedGenerations/$requiredConsecutiveSuccesses")
        failureStage?.let { append(" failedStage=$it") }
        failureDetail?.let { append(" detail=${it.take(160)}") }
        append(" totalMs=$totalDurationMs")
    }
}

/**
 * Pure counter for the stability probe: N consecutive successful sequences are required; one
 * failure resets the streak. Kept standalone so the pass rule is unit-testable without a device.
 */
class LiteRtGpuStabilityTracker(private val requiredConsecutiveSuccesses: Int) {
    init {
        require(requiredConsecutiveSuccesses > 0) { "At least one successful generation is required" }
    }
    private var consecutiveSuccesses = 0
    private var completed = 0

    val completedGenerations: Int get() = completed
    val passed: Boolean get() = consecutiveSuccesses >= requiredConsecutiveSuccesses

    /** Records one passed sequence; returns whether the streak now satisfies the requirement. */
    fun recordSuccess(): Boolean {
        completed++
        consecutiveSuccesses++
        return passed
    }

    /** Records one failed sequence; the consecutive streak restarts from zero. */
    fun recordFailure() {
        completed++
        consecutiveSuccesses = 0
    }

    fun reset() {
        consecutiveSuccesses = 0
        completed = 0
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
data class LiteRtResidentEnduranceResult(
    val modelId: String,
    val backend: LocalComputeBackend,
    val requestedTurns: Int,
    val attemptedTurns: Int,
    val passedTurns: Int,
    val blocked: Boolean,
    val boundedFailure: String?,
) {
    val passed: Boolean get() = !blocked && passedTurns == requestedTurns
    fun summaryLine(): String = "resident-endurance model=$modelId backend=$backend " +
        "turns=$passedTurns/$requestedTurns attempted=$attemptedTurns " +
        "verdict=${if (passed) "PASSED" else if (blocked) "BLOCKED" else "FAILED"} " +
        "detail=${boundedFailure ?: "none"}"
}

class LiteRtControlProbeRunner(
    context: Context,
    private val inventoryRepository: LocalModelInventoryRepository,
    private val loadConfigFor: (LocalModelDescriptor) -> RuntimeLoadConfig,
    /** Snapshot of the process-wide native resource fabric; supplied by the caller's coordinator. */
    private val nativeResourceSnapshot: () -> List<NativeResourceSnapshot> = { emptyList() },
    /** Currently warm-resident model id, if any. Resident != active execution. */
    private val residentModelId: () -> String? = { null },
    /**
     * DOGFOOD-005 durable journal: probe sequences cross the same native boundaries as production
     * generation, so a probe crash must be recoverable after restart the same way.
     */
    private val nativeAttemptJournal: LiteRtNativeAttemptJournal? = null,
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

    /** One full bounded control-probe sequence on the developer-selected backend. */
    suspend fun run(modelId: String): LiteRtControlProbeResult = runSequence(modelId, null)


    /**
     * Native endurance: one initialized Engine, N distinct Conversation instances and full teardown.
     * Each turn uses a different short expected sentinel. This tests cross-conversation correctness
     * and GPU delegate reclamation, NOT Halo tool/agent qualification.
     *
     * Caller MUST explicitly request this diagnostic; the same preflight rejects active native
     * workloads or a resident production model. onTurn runs AFTER conversation.close() so a
     * physical test can sample process PSS/native heap between turns without recording user text.
     */
    suspend fun runResidentEndurance(
        modelId: String,
        turns: Int,
        onTurn: suspend (Int, LiteRtControlProbeResult) -> Unit = { _, _ -> },
    ): LiteRtResidentEnduranceResult = withContext(Dispatchers.IO) {
        require(turns in 2..10) { "Resident endurance requires 2..10 bounded turns" }
        val started = System.currentTimeMillis()
        val pre = preflight(modelId, started)
        if (pre.shortCircuit != null) {
            return@withContext LiteRtResidentEnduranceResult(modelId, pre.config.computeBackend,
                turns, 0, 0, pre.shortCircuit.blocked, pre.shortCircuit.summaryLine().take(240))
        }
        val descriptor = pre.descriptor!!
        val config = pre.config
        val backend = config.computeBackend
        require(backend != LocalComputeBackend.AUTO) {
            "Resident endurance must select CPU or GPU explicitly, never silently fallback"
        }
        val requestedContext = if (config.useModelDefaultContext) null else config.contextSize
        val fingerprint = descriptor.sha256 ?: "id:${descriptor.id}:bytes:${descriptor.bytes}"
        val cacheDirs = LiteRtEngineConfigFactory.cacheDirsFor(filesDir, descriptor.id, fingerprint)
        val cacheDir = if (backend == LocalComputeBackend.GPU) cacheDirs.gpu else cacheDirs.cpu
        var completed = 0
        var attempted = 0
        var detail: String? = null
        var engine: Engine? = null
        nativeAttemptJournal?.arm(modelId, fingerprint, backend)
        try {
            val loaded = LiteRtEngineConfigFactory.createAndInitialize(
                modelPath = descriptor.path, backend = backend,
                contextTokens = requestedContext, cacheDir = cacheDir,
                visionEnabled = false, audioEnabled = false,
                cpuThreads = config.cpuThreads.coerceIn(1, 32),
            )
            if (loaded.isFailure) {
                detail = "ENGINE_INIT: ${LiteRtEngineConfigFactory.bounded(loaded.exceptionOrNull())}"
            } else {
                engine = loaded.getOrThrow()
                for (index in 1..turns) {
                    attempted++
                    val marker = if (index % 2 == 0) "HALO_READY" else "HALO_OK"
                    val stages = mutableListOf<LiteRtControlProbeStageResult>()
                    val one = probeConversation(
                        modelId = modelId, engine = engine!!, config = config,
                        requestedContext = requestedContext, effectiveBackend = backend,
                        effectiveContext = requestedContext, stages = stages,
                        startedAt = System.currentTimeMillis(),
                        prompt = "Reply exactly $marker", expected = marker, strictExpected = true,
                    )
                    closeConversationQuietly(one.conversation)
                    onTurn(index, one.result)
                    if (!one.result.controlProbePassed) {
                        detail = one.result.summaryLine().take(240)
                        break
                    }
                    completed++
                }
            }
        } finally {
            nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.TEARDOWN)
            closeEngineQuietly(engine)
            nativeAttemptJournal?.clear()
        }
        LiteRtResidentEnduranceResult(modelId, backend, turns, attempted, completed,
            blocked = false, boundedFailure = detail)
    }

    /**
     * One full bounded control-probe sequence. [configOverride] lets the GPU stability probe force
     * the accelerator under test instead of the developer-selected backend; everything else
     * (preflight, context ladder, teardown) is identical to [run].
     */
    internal suspend fun runSequence(
        modelId: String,
        configOverride: ((LocalModelDescriptor) -> RuntimeLoadConfig)?,
    ): LiteRtControlProbeResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val preflight = preflight(modelId, startedAt, configOverride)
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
            // Durable before the native call: a crash inside initialize() must be attributable
            // to this exact backend after restart.
            nativeAttemptJournal?.arm(modelId, fingerprint, config.computeBackend)
            val attempt = LiteRtEngineConfigFactory.createAndInitialize(
                modelPath = descriptor.path,
                backend = config.computeBackend,
                contextTokens = contextTokens,
                cacheDir = cacheDir,
                visionEnabled = config.visionEnabled,
                audioEnabled = config.audioEnabled,
                cpuThreads = config.cpuThreads.coerceIn(1, 32),
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
            nativeAttemptJournal?.clear()
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
        nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.TEARDOWN)
        val teardownStart = System.currentTimeMillis()
        closeConversationQuietly(probe.conversation)
        closeEngineQuietly(engine)
        // Clean endpoint reached: the pending record must not outlive this attempt.
        nativeAttemptJournal?.clear()
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

    private suspend fun preflight(
        modelId: String,
        startedAt: Long,
        configOverride: ((LocalModelDescriptor) -> RuntimeLoadConfig)? = null,
    ): Preflight {
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

        // Skip only the exact published non-executable INT1 artifact, including explicit
        // direct probes that bypass the normal provider admission path.
        if (com.halo.providers.litert.models.LiteRtCommunityModelGuide.nonRunnableFingerprint(descriptor.sha256)) {
            stages.add(LiteRtControlProbeStageResult(
                LiteRtControlProbeStage.ABI,
                LiteRtControlProbeStatus.FAILED,
                boundedDetail = "Bonsai INT1 proof-of-concept has no LiteRT execution kernel",
            ))
            return Preflight(descriptor, RuntimeLoadConfig(), stages, buildResult(
                modelId, descriptor, RuntimeLoadConfig(), null, null, stages, startedAt, false,
            ))
        }

        // Direct probes cannot bypass task safety just because the file is a .litertlm.
        // EmbeddingGemma and other non-generative models use different SDK runtimes.
        val knownCatalog = (inventoryRepository as?
            com.halo.providers.litert.models.LiteRtModelManager)?.catalogModel(modelId)
        if (!com.halo.providers.litert.models.LiteRtGenerationAdmission.permitted(
                knownCatalog, java.io.File(descriptor.path).name)) {
            stages.add(LiteRtControlProbeStageResult(
                LiteRtControlProbeStage.ARTIFACT, LiteRtControlProbeStatus.FAILED,
                boundedDetail = "Not a LiteRT text generation task; use the appropriate task engine",
            ))
            return Preflight(descriptor, RuntimeLoadConfig(), stages, buildResult(
                modelId, descriptor, RuntimeLoadConfig(), null, null, stages, startedAt, false))
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

        val config = configOverride?.invoke(descriptor) ?: loadConfigFor(descriptor)
        return Preflight(descriptor, config, stages, null)
    }

    /**
     * DOGFOOD-005 GPU stability probe: runs [LITE_RT_GPU_STABILITY_PROBE_RUNS] complete, isolated,
     * single-prompt sequences on the GPU using the existing control-probe path. The GPU is only
     * proven by all sequences completing with a first token on the GPU; a CPU fallback inside a
     * sequence counts as a probe failure, not a success.
     */
    suspend fun runGpuStabilityProbe(
        modelId: String,
        requiredConsecutiveSuccesses: Int = LITE_RT_GPU_STABILITY_PROBE_RUNS,
    ): LiteRtGpuStabilityProbeResult {
        val startedAt = System.currentTimeMillis()
        val tracker = LiteRtGpuStabilityTracker(requiredConsecutiveSuccesses)
        val gpuConfig: (LocalModelDescriptor) -> RuntimeLoadConfig = { descriptor ->
            loadConfigFor(descriptor).copy(computeBackend = LocalComputeBackend.GPU)
        }
        var failureStage: LiteRtControlProbeStage? = null
        var failureDetail: String? = null
        var stoppedByFailure = false

        while (!tracker.passed) {
            val sequence = runSequence(modelId, gpuConfig)
            if (sequence.blocked || !sequence.controlProbePassed ||
                sequence.effectiveBackend != LocalComputeBackend.GPU
            ) {
                stoppedByFailure = true
                failureStage = sequence.stages.lastOrNull {
                    it.status == LiteRtControlProbeStatus.FAILED || it.status == LiteRtControlProbeStatus.BLOCKED
                }?.stage
                    ?: LiteRtControlProbeStage.COMPLETION
                failureDetail = if (sequence.effectiveBackend != LocalComputeBackend.GPU && sequence.effectiveBackend != null) {
                    "effective backend ${LiteRtEngineConfigFactory.backendName(sequence.effectiveBackend)} instead of GPU"
                } else {
                    sequence.stages.lastOrNull {
                        it.status == LiteRtControlProbeStatus.FAILED || it.status == LiteRtControlProbeStatus.BLOCKED
                    }?.boundedDetail ?: sequence.summaryLine().take(300)
                }
                tracker.recordFailure()
                break
            }
            if (!tracker.recordSuccess()) {
                Log.i(
                    TAG,
                    "$EVENT_GPU_STABILITY_PROBE model=$modelId " +
                        "sequence=${tracker.completedGenerations} consecutive=${requiredConsecutiveSuccesses} " +
                        "result=CONTINUE",
                )
                continue
            }
            break
        }

        val result = LiteRtGpuStabilityProbeResult(
            modelId = modelId,
            artifactSha256 = inventoryRepository.resolveModel(modelId)?.sha256,
            backend = LocalComputeBackend.GPU,
            requiredConsecutiveSuccesses = requiredConsecutiveSuccesses,
            completedGenerations = tracker.completedGenerations,
            passed = tracker.passed,
            failureStage = if (stoppedByFailure) failureStage else null,
            failureDetail = if (stoppedByFailure) failureDetail else null,
            startedAtMs = startedAt,
            totalDurationMs = System.currentTimeMillis() - startedAt,
        )
        Log.i(TAG, "$EVENT_GPU_STABILITY_PROBE ${result.summaryLine()}")
        return result
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
        prompt: String = PROBE_PROMPT,
        expected: String = PROBE_EXPECTED,
        strictExpected: Boolean = false,
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
        nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.CONVERSATION_CREATE)
        val conversationStart = System.currentTimeMillis()
        conversation = try {
            engine.createConversation(
                ConversationConfig(
                    chatTemplate = LiteRtChatTemplateCompatibility.overrideFor(descriptor?.sha256),
                    thinkingConfig = if (descriptor?.supportsThinking == true) com.google.ai.edge.litertlm.ThinkingConfig(false, 0) else null,
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
        nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.FIRST_TOKEN_WAIT)
        val generationStart = System.currentTimeMillis()
        var firstTokenAt: Long? = null
        var outputChars = 0
        var reasoningChars = 0
        val text = StringBuilder()
        try {
            val flow = conversation.sendMessageAsync(
                Message.user(Contents.of(Content.Text(prompt))),
                maxOutputToken = PROBE_MAX_OUTPUT_TOKENS,
            )
            kotlinx.coroutines.withTimeout(120_000L) { flow.collect { message ->
                reasoningChars += message.channels.values.sumOf { it.length }
                val chunk = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                if (chunk.isEmpty()) return@collect
                if (firstTokenAt == null) {
                    firstTokenAt = System.currentTimeMillis()
                    nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.GENERATING)
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
            } }
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
                    boundedDetail = "No answer text; reasoningChars=$reasoningChars " +
                        "outputTokenBudget=$PROBE_MAX_OUTPUT_TOKENS",
                ),
            )
            return result(false)
        }

        // Only presence and expectation match are retained: never the generated text.
        val matched = text.toString().trim().contains(expected)
        val passed = outputChars > 0 && (!strictExpected || matched)
        stages.add(
            LiteRtControlProbeStageResult(
                LiteRtControlProbeStage.COMPLETION,
                if (passed) LiteRtControlProbeStatus.PASSED else LiteRtControlProbeStatus.FAILED,
                durationMs = System.currentTimeMillis() - generationStart,
                boundedDetail = "outputNonEmpty=${outputChars > 0} " +
                    "outputMatchedExpected=$matched",
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
        const val EVENT_GPU_STABILITY_PROBE = "HALO_LITERT_GPU_STABILITY_PROBE"
        const val PROBE_PROMPT = "Reply exactly HALO_OK"
        const val PROBE_EXPECTED = "HALO_OK"
        // Reasoning channels consume this budget too; retain a bounded chance to
        // finish thinking and emit an answer. Reasoning alone never passes a probe.
        const val PROBE_MAX_OUTPUT_TOKENS = 256

        /** DOGFOOD-005: complete consecutive GPU sequences required to qualify the accelerator. */
        const val LITE_RT_GPU_STABILITY_PROBE_RUNS = 3
    }
}
