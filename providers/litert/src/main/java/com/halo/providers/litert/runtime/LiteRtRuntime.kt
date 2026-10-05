package com.halo.providers.litert.runtime

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolCall as LiteRtToolCall
import com.google.ai.edge.litertlm.tool
import com.halo.providers.litert.tools.HaloOpenApiTool
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalInferenceRuntime
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.LocalModelSessionState
import com.halo.sdk.provider.LocalRuntimeBackendReporting
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.RuntimeLoadResult
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

/**
 * Stage at which a LiteRT load/generation failed, so callers never have to guess from a message.
 *
 * ENGINE_INIT, CONVERSATION and FIRST_TOKEN stay independently visible: an engine that
 * initializes but cannot create a conversation is not a verified model, and a model that
 * initializes but never emits a token is not a generation-proven model.
 */
enum class ModelLoadStage { ABI, ARTIFACT, ENGINE_INIT, CONVERSATION, FIRST_TOKEN, GENERATION }

/**
 * Preserves the *first* failure (usually the GPU attempt) alongside the final one (usually the
 * CPU fallback). Without both, a failed on-device load is impossible to diagnose from the field.
 */
class LiteRtModelLoadException(
    val stage: ModelLoadStage,
    val requestedBackend: LocalComputeBackend,
    val effectiveCause: Throwable,
    val primaryCause: Throwable? = null,
    message: String,
) : Exception(message, effectiveCause)

data class EngineLoadOutcome(
    val engine: Engine,
    /** Model-scoped cache root; the per-accelerator subdirectory lives inside it. */
    val cacheRoot: File,
    val cacheDir: File,
    val requestedBackend: LocalComputeBackend,
    val effectiveBackend: LocalComputeBackend,
    val fallbackReason: String?,
    /** Context originally requested, or null when the model default was requested. */
    val requestedContextTokens: Int?,
    /** Context actually allocated, or null when the engine chose its own default. */
    val effectiveContextTokens: Int?,
    val contextFallbackReason: String?,
    val engineInitDurationMs: Long,
    /** SHA-256 of the loaded artifact; feeds crash-attribution records (content-free). */
    val artifactFingerprint: String,
)

data class LiteRtRuntimeSnapshot(
    val modelId: String?,
    val state: LocalModelSessionState,
    val requestedBackend: LocalComputeBackend?,
    val effectiveBackend: LocalComputeBackend?,
    val contextTokens: Int?,
    val requestedContextTokens: Int?,
    val contextFallbackReason: String?,
    val fallbackReason: String?,
    val activeRequestId: String?,
    val cachePath: String?,
    val engineInitDurationMs: Long?,
    val conversationCreateDurationMs: Long?,
)

/** Outcome of one generation attempt. A pre-first-token GPU failure may be retried on CPU. */
private sealed interface GenerationAttempt {
    data class Finished(val result: Result<RuntimeGenerationResult>) : GenerationAttempt
    data class RetryableGpuFailure(val cause: Throwable, val failedEngine: Engine) : GenerationAttempt
}

private data class DetachedRuntimeResources(
    val engine: Engine?,
    val conversation: Conversation?,
)

/** Outcome of initializing one engine at one context size on one backend policy. */
private sealed interface EngineAttempt {
    data class Success(
        val engine: Engine,
        val cacheDir: File,
        val effectiveBackend: LocalComputeBackend,
        val gpuFallbackReason: String?,
    ) : EngineAttempt

    data class Failure(val error: Throwable, val memoryRelated: Boolean) : EngineAttempt
}

class LiteRtRuntime(
    private val context: Context,
    private val scope: CoroutineScope,
    /**
     * DOGFOOD-005 durable journal: arms/advances/clears the pending native-attempt record around
     * every native boundary so a hard crash is attributable after restart.
     */
    private val nativeAttemptJournal: LiteRtNativeAttemptJournal? = null,
) : LocalInferenceRuntime, LocalRuntimeBackendReporting {

    override val format: LocalModelFormat = LocalModelFormat.LITERT_LM

    private val _state = MutableStateFlow(LocalModelSessionState.IDLE)
    override val state: StateFlow<LocalModelSessionState> = _state.asStateFlow()

    private var _loadedModelId: String? = null
    override val loadedModelId: String? get() = _loadedModelId

    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    private var requestedBackend: LocalComputeBackend = LocalComputeBackend.AUTO
    private var effectiveBackend: LocalComputeBackend = LocalComputeBackend.AUTO

    /** Null means "the engine is using the model's own default context window". */
    private var effectiveContextTokens: Int? = null
    private var requestedContextTokens: Int? = null
    private var contextFallbackReason: String? = null
    private var lastFallbackReason: String? = null
    private var activeCachePath: String? = null
    private var engineInitDurationMs: Long? = null
    private var engineGenerationAttempted: Boolean = false
    private var conversationCreateDurationMs: Long? = null

    /** Everything needed to rebuild this exact model on another accelerator. */
    private var activeModelPath: String? = null
    /** SHA-256 of the resident artifact; reused for crash-attribution records (no re-hash). */
    private var activeArtifactFingerprint: String? = null
    private var activeCacheRootPath: String? = null
    private var activeLoadConfig: RuntimeLoadConfig? = null
    private var visionInputEnabled: Boolean = false

    /**
     * Some devices (notably emulators and older GPUs) report a successful GPU `initialize()` and
     * only fail on the first inference. One late rebuild on CPU is allowed per residency.
     */
    private var cpuFallbackConsumed = false

    private var activeRequestId: String? = null

    /**
     * MINING-P0-001 content-free observation of the last generation's native tool channel. A model
     * may *emit* a tool call; Halo still decides whether it is structurally usable.
     */
    private val _lastToolChannelObservation = MutableStateFlow<LiteRtNativeToolChannelObservation?>(null)
    val lastToolChannelObservation: StateFlow<LiteRtNativeToolChannelObservation?> =
        _lastToolChannelObservation.asStateFlow()

    private val stateMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val loadMutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)

    override fun isLoaded(): Boolean = engine != null && _state.value != LocalModelSessionState.UNLOADING

    /** Reported accelerator; never claims a backend while nothing is resident. */
    override val activeBackend: LocalComputeBackend?
        get() = if (isLoaded()) effectiveBackend else null

    override val backendFallbackReason: String?
        get() = lastFallbackReason

    fun snapshot(): LiteRtRuntimeSnapshot = LiteRtRuntimeSnapshot(
        modelId = _loadedModelId,
        state = _state.value,
        requestedBackend = requestedBackend,
        effectiveBackend = effectiveBackend,
        contextTokens = effectiveContextTokens,
        requestedContextTokens = requestedContextTokens,
        contextFallbackReason = contextFallbackReason,
        fallbackReason = lastFallbackReason,
        activeRequestId = activeRequestId,
        cachePath = activeCachePath,
        engineInitDurationMs = engineInitDurationMs,
        conversationCreateDurationMs = conversationCreateDurationMs,
    )

    /**
     * LiteRT Android local inference only ships arm64-v8a native libraries. Entering
     * `Engine.initialize()` on an unsupported ABI produces an opaque native abort instead of a
     * diagnosable failure, so it is rejected here. Shared with the direct control probe.
     */
    private fun abiBlocker(): String? = LiteRtEngineConfigFactory.abiBlocker()

    private fun backendName(backend: LocalComputeBackend): String =
        LiteRtEngineConfigFactory.backendName(backend)

    /**
     * MINING-P0-001 the engine-config build rules live in [LiteRtEngineConfigFactory] so the
     * production runtime and the direct control probe cannot slowly diverge.
     */
    private fun createAndInitializeEngine(
        modelPath: String,
        backend: LocalComputeBackend,
        contextTokens: Int?,
        cacheDir: File,
    ): Result<Engine> = LiteRtEngineConfigFactory.createAndInitialize(
        modelPath = modelPath,
        backend = backend,
        contextTokens = contextTokens,
        cacheDir = cacheDir,
        visionEnabled = visionInputEnabled,
    )

    /**
     * DOGFOOD-005: make the pending native-attempt record durable for the accelerator we are
     * about to enter. Must run before [createAndInitializeEngine] because a hard native crash
     * leaves no catchable trace in this process.
     */
    private fun journalEngineAttempt(modelId: String, artifactFingerprint: String?, backend: LocalComputeBackend) {
        nativeAttemptJournal?.arm(modelId, artifactFingerprint, backend)
    }

    /**
     * Releasing weights takes seconds. Running it inside a cancelled coroutine can abort the native
     * close and leak the engine, so teardown is non-cancellable and off the caller's thread.
     */
    private suspend fun closeEngineQuietly(target: Engine?) {
        if (target == null) return
        withContext(NonCancellable + Dispatchers.Default) {
            if (target.isInitialized()) runCatching { target.close() }
        }
    }

    private suspend fun closeConversationQuietly(target: Conversation?, cancel: Boolean) {
        if (target == null) return
        withContext(NonCancellable + Dispatchers.Default) {
            if (cancel) runCatching { target.cancelProcess() }
            runCatching { target.close() }
        }
    }

    private fun bounded(error: Throwable?): String = LiteRtEngineConfigFactory.bounded(error)

    /**
     * True only for failures that a smaller context allocation could plausibly fix. Shared with the
     * direct control probe: a corrupt artifact, unsupported op or ABI mismatch must never trigger a
     * context downshift in either path.
     */
    private fun isMemoryAllocationFailure(error: Throwable?): Boolean =
        LiteRtEngineConfigFactory.isMemoryAllocationFailure(error)

    private fun resolveEngine(
        file: File,
        modelId: String,
        config: RuntimeLoadConfig,
    ): EngineLoadOutcome {
        val artifactFingerprint = sha256(file)
        // Cache-path and context-ladder rules are owned by the shared factory so the production
        // runtime and the direct control probe cannot drift apart.
        val cacheDirs = LiteRtEngineConfigFactory.cacheDirsFor(context.filesDir, modelId, artifactFingerprint)
        val modelCacheRoot = cacheDirs.root
        // A failed GPU initialization must never leave state that influences the CPU fallback.
        val gpuCacheDir = cacheDirs.gpu
        val cpuCacheDir = cacheDirs.cpu
        val requestedContext = if (config.useModelDefaultContext) null else config.contextSize
        val ladder = LiteRtEngineConfigFactory.contextLadder(requestedContext, config.computeBackend)

        val start = System.currentTimeMillis()
        var downshiftReason: String? = null
        var lastFailure: EngineAttempt.Failure? = null

        ladder.forEachIndexed { index, contextTokens ->
            val attempt = attemptEngine(
                file = file,
                modelId = modelId,
                requestedBackend = config.computeBackend,
                contextTokens = contextTokens,
                gpuCacheDir = gpuCacheDir,
                cpuCacheDir = cpuCacheDir,
                artifactFingerprint = artifactFingerprint,
            )
            when (attempt) {
                is EngineAttempt.Success -> {
                    if (downshiftReason != null) {
                        Log.i(
                            TAG,
                            "$EVENT_CONTEXT_READY model=$modelId requested=${requestedContext ?: "model-default"} " +
                                "effective=${contextTokens ?: "model-default"}",
                        )
                    }
                    return EngineLoadOutcome(
                        engine = attempt.engine,
                        cacheRoot = modelCacheRoot,
                        cacheDir = attempt.cacheDir,
                        requestedBackend = config.computeBackend,
                        effectiveBackend = attempt.effectiveBackend,
                        fallbackReason = attempt.gpuFallbackReason,
                        requestedContextTokens = requestedContext,
                        effectiveContextTokens = contextTokens,
                        contextFallbackReason = downshiftReason,
                        engineInitDurationMs = System.currentTimeMillis() - start,
                        artifactFingerprint = artifactFingerprint,
                    )
                }

                is EngineAttempt.Failure -> {
                    lastFailure = attempt
                    if (!attempt.memoryRelated || index == ladder.lastIndex) throw attempt.error
                    downshiftReason = "Engine initialization failed at $contextTokens tokens: ${bounded(attempt.error)}"
                    Log.w(
                        TAG,
                        "$EVENT_CONTEXT_DOWNSHIFT model=$modelId sha=${artifactFingerprint.take(12)} " +
                            "backend=${backendName(config.computeBackend)} failedContext=$contextTokens " +
                            "reason=${bounded(attempt.error)}",
                    )
                }
            }
        }

        throw lastFailure?.error ?: IllegalStateException("LiteRT engine initialization produced no attempt")
    }

    private fun attemptEngine(
        file: File,
        modelId: String,
        requestedBackend: LocalComputeBackend,
        contextTokens: Int?,
        gpuCacheDir: File,
        cpuCacheDir: File,
        artifactFingerprint: String,
    ): EngineAttempt {
        return when (requestedBackend) {
        LocalComputeBackend.CPU -> {
            journalEngineAttempt(modelId, artifactFingerprint, LocalComputeBackend.CPU)
            createAndInitializeEngine(file.absolutePath, LocalComputeBackend.CPU, contextTokens, cpuCacheDir)
                .fold(
                    onSuccess = { EngineAttempt.Success(it, cpuCacheDir, LocalComputeBackend.CPU, null) },
                    onFailure = { error ->
                        logEngineInitFailure(modelId, file, artifactFingerprint, LocalComputeBackend.CPU, contextTokens, error)
                        EngineAttempt.Failure(
                            LiteRtModelLoadException(
                                stage = ModelLoadStage.ENGINE_INIT,
                                requestedBackend = LocalComputeBackend.CPU,
                                effectiveCause = error,
                                message = "CPU engine initialization failed (${bounded(error)})",
                            ),
                            isMemoryAllocationFailure(error),
                        )
                    },
                )
        }

        LocalComputeBackend.GPU -> {
            journalEngineAttempt(modelId, artifactFingerprint, LocalComputeBackend.GPU)
            createAndInitializeEngine(file.absolutePath, LocalComputeBackend.GPU, contextTokens, gpuCacheDir)
                .fold(
                    onSuccess = { EngineAttempt.Success(it, gpuCacheDir, LocalComputeBackend.GPU, null) },
                    onFailure = { error ->
                        logEngineInitFailure(modelId, file, artifactFingerprint, LocalComputeBackend.GPU, contextTokens, error)
                        EngineAttempt.Failure(
                            LiteRtModelLoadException(
                                stage = ModelLoadStage.ENGINE_INIT,
                                requestedBackend = LocalComputeBackend.GPU,
                                effectiveCause = error,
                                message = "GPU engine initialization failed (${bounded(error)})",
                            ),
                            isMemoryAllocationFailure(error),
                        )
                    },
                )
        }

        LocalComputeBackend.AUTO -> {
            journalEngineAttempt(modelId, artifactFingerprint, LocalComputeBackend.GPU)
            val gpuResult = createAndInitializeEngine(
                file.absolutePath,
                LocalComputeBackend.GPU,
                contextTokens,
                gpuCacheDir,
            )
            if (gpuResult.isSuccess) {
                return EngineAttempt.Success(
                    gpuResult.getOrThrow(),
                    gpuCacheDir,
                    LocalComputeBackend.GPU,
                    null,
                )
            }

            val gpuError = gpuResult.exceptionOrNull()
            val gpuReason = "GPU failed: ${bounded(gpuError)}"
            Log.w(
                TAG,
                "$EVENT_GPU_FAILED model=$modelId phase=INIT type=${gpuError?.let { it::class.java.simpleName }} " +
                    "message=${bounded(gpuError)} sha=$artifactFingerprint bytes=${file.length()} " +
                    "context=${contextTokens ?: "model-default"}",
                gpuError,
            )

            // Failed GPU engine is already closed by createAndInitializeEngine; the CPU attempt
            // runs in its own cache directory. The pending attempt record now points at the
            // accelerator actually under test.
            journalEngineAttempt(modelId, artifactFingerprint, LocalComputeBackend.CPU)
            val cpuResult = createAndInitializeEngine(
                file.absolutePath,
                LocalComputeBackend.CPU,
                contextTokens,
                cpuCacheDir,
            )
            if (cpuResult.isFailure) {
                val cpuError = cpuResult.exceptionOrNull()!!
                Log.e(
                    TAG,
                    "$EVENT_LOAD_FAILED model=$modelId backend=AUTO stage=ENGINE_INIT gpu=$gpuReason cpu=${bounded(cpuError)}",
                    cpuError,
                )
                return EngineAttempt.Failure(
                    LiteRtModelLoadException(
                        stage = ModelLoadStage.ENGINE_INIT,
                        requestedBackend = LocalComputeBackend.AUTO,
                        effectiveCause = cpuError,
                        primaryCause = gpuError,
                        message = "GPU and CPU engine initialization both failed. " +
                            "GPU: ${bounded(gpuError)}. CPU: ${bounded(cpuError)}.",
                    ),
                    isMemoryAllocationFailure(cpuError) || isMemoryAllocationFailure(gpuError),
                )
            }

            Log.i(TAG, "$EVENT_CPU_FALLBACK model=$modelId phase=INIT reason=$gpuReason")
            EngineAttempt.Success(cpuResult.getOrThrow(), cpuCacheDir, LocalComputeBackend.CPU, gpuReason)
        }
        }
    }

    private fun logEngineInitFailure(
        modelId: String,
        file: File,
        artifactFingerprint: String,
        backend: LocalComputeBackend,
        contextTokens: Int?,
        error: Throwable,
    ) {
        Log.e(
            TAG,
            "$EVENT_LOAD_FAILED model=$modelId backend=${backendName(backend)} stage=ENGINE_INIT " +
                "type=${error::class.java.simpleName} message=${bounded(error)} " +
                "sha=$artifactFingerprint bytes=${file.length()} context=${contextTokens ?: "model-default"}",
            error,
        )
    }

    override suspend fun loadModel(
        modelId: String,
        modelPath: String,
        config: RuntimeLoadConfig,
    ): Result<RuntimeLoadResult> = loadMutex.withLock {
        _state.value = LocalModelSessionState.LOADING
        // withContext may deliver cancellation after its blocking native body produced an engine
        // but before the result is assigned on this coroutine. Keep an explicit handoff owner so
        // every initialized engine is either published or closed.
        val unpublishedOutcome = java.util.concurrent.atomic.AtomicReference<EngineLoadOutcome?>(null)
        try {
            val file = File(modelPath)
            require(file.exists() && file.isFile) { "Model file does not exist: $modelPath" }

            val abiIssue = abiBlocker()
            if (abiIssue != null) {
                Log.e(TAG, "$EVENT_LOAD_FAILED model=$modelId stage=ABI message=$abiIssue")
                throw LiteRtModelLoadException(
                    stage = ModelLoadStage.ABI,
                    requestedBackend = config.computeBackend,
                    effectiveCause = UnsupportedOperationException(abiIssue),
                    message = abiIssue,
                )
            }

            val requestedContext = if (config.useModelDefaultContext) null else config.contextSize
            Log.i(
                TAG,
                "$EVENT_LOAD_START model=$modelId backend=${backendName(config.computeBackend)} " +
                    "bytes=${file.length()} context=${requestedContext ?: "model-default"} path=$modelPath",
            )

            // Close any previous engine before allocating a new one to avoid dual-model OOM.
            val replacedResources = inferenceMutex.withLock {
                stateMutex.withLock { detachRuntimeState() }
            }
            closeConversationQuietly(replacedResources.conversation, cancel = true)
            closeEngineQuietly(replacedResources.engine)
            visionInputEnabled = config.visionEnabled

            val outcome = withContext(Dispatchers.Default) {
                resolveEngine(file, modelId, config).also(unpublishedOutcome::set)
            }

            stateMutex.withLock {
                engine = outcome.engine
                engineGenerationAttempted = false
                requestedBackend = outcome.requestedBackend
                effectiveBackend = outcome.effectiveBackend
                requestedContextTokens = outcome.requestedContextTokens
                effectiveContextTokens = outcome.effectiveContextTokens
                contextFallbackReason = outcome.contextFallbackReason
                lastFallbackReason = outcome.fallbackReason
                engineInitDurationMs = outcome.engineInitDurationMs
                conversationCreateDurationMs = null
                activeCachePath = outcome.cacheDir.absolutePath
                activeCacheRootPath = outcome.cacheRoot.absolutePath
                activeModelPath = file.absolutePath
                activeArtifactFingerprint = outcome.artifactFingerprint
                activeLoadConfig = config
                cpuFallbackConsumed = false
                _loadedModelId = modelId
                _state.value = LocalModelSessionState.IDLE
            }
            unpublishedOutcome.set(null)
            nativeAttemptJournal?.clear()

            Log.i(
                TAG,
                "$EVENT_LOAD_READY model=$modelId requested=${backendName(outcome.requestedBackend)} " +
                    "effective=${backendName(outcome.effectiveBackend)} " +
                    "requestedContext=${outcome.requestedContextTokens ?: "model-default"} " +
                    "effectiveContext=${outcome.effectiveContextTokens ?: "model-default"} " +
                    "engineInitMs=${outcome.engineInitDurationMs} fallback=${outcome.fallbackReason != null} " +
                    "contextDownshift=${outcome.contextFallbackReason != null}",
            )

            Result.success(
                RuntimeLoadResult(
                    activeBackend = outcome.effectiveBackend,
                    activeGpuLayers = if (outcome.effectiveBackend == LocalComputeBackend.GPU) 1 else 0,
                    effectiveContextSize = outcome.effectiveContextTokens ?: config.contextSize,
                    fallbackReason = outcome.fallbackReason,
                    requestedContextSize = outcome.requestedContextTokens,
                    contextFallbackReason = outcome.contextFallbackReason,
                )
            )
        } catch (e: CancellationException) {
            closeEngineQuietly(unpublishedOutcome.getAndSet(null)?.engine)
            nativeAttemptJournal?.clear()
            _state.value = LocalModelSessionState.IDLE
            throw e
        } catch (e: Throwable) {
            closeEngineQuietly(unpublishedOutcome.getAndSet(null)?.engine)
            nativeAttemptJournal?.clear()
            _state.value = LocalModelSessionState.ERROR
            Result.failure(e)
        }
    }

    override suspend fun generate(
        config: RuntimeGenerationConfig,
        onToken: suspend (String) -> Unit,
    ): Result<RuntimeGenerationResult> {
        when (val attempt = generateAttempt(config, onToken)) {
            is GenerationAttempt.Finished -> return attempt.result
            is GenerationAttempt.RetryableGpuFailure -> {
                val reason = "GPU failed before first token: ${bounded(attempt.cause)}"
                Log.w(
                    TAG,
                    "$EVENT_GPU_FAILED model=$_loadedModelId phase=GENERATION " +
                        "type=${attempt.cause::class.java.simpleName} message=${bounded(attempt.cause)}",
                    attempt.cause,
                )
                val rebuilt = rebuildResidentEngineOnCpu(attempt.failedEngine, attempt.cause, reason)
                if (rebuilt.isFailure) {
                    return Result.failure(
                        LiteRtModelLoadException(
                            stage = ModelLoadStage.GENERATION,
                            requestedBackend = LocalComputeBackend.AUTO,
                            effectiveCause = rebuilt.exceptionOrNull() ?: attempt.cause,
                            primaryCause = attempt.cause,
                            message = "$reason; CPU fallback initialization also failed: " +
                                bounded(rebuilt.exceptionOrNull()),
                        )
                    )
                }
                Log.i(TAG, "$EVENT_CPU_FALLBACK model=$_loadedModelId phase=GENERATION retrying once on CPU")
                return when (val retry = generateAttempt(config, onToken)) {
                    is GenerationAttempt.Finished -> retry.result
                    is GenerationAttempt.RetryableGpuFailure -> Result.failure(
                        LiteRtModelLoadException(
                            stage = ModelLoadStage.GENERATION,
                            requestedBackend = LocalComputeBackend.AUTO,
                            effectiveCause = retry.cause,
                            message = "GPU generation failed outside the retry window (${bounded(retry.cause)})",
                        )
                    )
                }
            }
        }
    }

    /**
     * Rebuilds the resident engine on the CPU in place, keeping the same model id so the session
     * stays valid. Lock order is load -> inference -> short state swaps; native close/initialize
     * never runs under [stateMutex].
     */
    private suspend fun rebuildResidentEngineOnCpu(
        expectedEngine: Engine,
        gpuGenerationFailure: Throwable,
        reason: String,
    ): Result<Unit> {
        val modelId = stateMutex.withLock { _loadedModelId }
            ?: return Result.failure(IllegalStateException("No resident model id"))
        val modelPath = activeModelPath ?: return Result.failure(IllegalStateException("No resident model path"))
        val cacheRoot = activeCacheRootPath?.let(::File)
            ?: File(context.filesDir, "litertlm_cache/${modelId.replace('/', '_')}").also { it.mkdirs() }
        val cpuCacheDir = File(cacheRoot, LiteRtEngineConfigFactory.CPU_CACHE_SUBDIR)
            .also { if (!it.exists()) it.mkdirs() }
        val contextTokens = activeLoadConfig?.let { if (it.useModelDefaultContext) null else it.contextSize }
            ?: effectiveContextTokens

        return loadMutex.withLock loadLock@{
            inferenceMutex.withLock inferenceLock@{
                val previous = stateMutex.withLock {
                    if (engine !== expectedEngine) null else engine.also {
                        engine = null
                        engineGenerationAttempted = false
                        activeConversation = null
                        _state.value = LocalModelSessionState.LOADING
                    }
                } ?: return@inferenceLock Result.failure(
                    IllegalStateException("Resident LiteRT engine changed before CPU fallback")
                )

                // The failed GPU engine must release its model/native allocations before CPU owns
                // any. This keeps the residency invariant at one engine even under memory pressure.
                closeEngineQuietly(previous)

                journalEngineAttempt(modelId, activeArtifactFingerprint, LocalComputeBackend.CPU)
                val cpuEngine = createAndInitializeEngine(modelPath, LocalComputeBackend.CPU, contextTokens, cpuCacheDir)
                    .getOrElse { error ->
                        Log.e(
                            TAG,
                            "$EVENT_LOAD_FAILED model=$modelId backend=CPU phase=GENERATION_FALLBACK " +
                                "type=${error::class.java.simpleName} message=${bounded(error)}",
                            error,
                        )
                        stateMutex.withLock {
                            engine = null
                            engineGenerationAttempted = false
                            activeConversation = null
                            lastFallbackReason = "$reason; CPU rebuild failed: ${bounded(error)}"
                            _state.value = LocalModelSessionState.ERROR
                        }
                        return@inferenceLock Result.failure(
                            LiteRtModelLoadException(
                                stage = ModelLoadStage.GENERATION,
                                requestedBackend = LocalComputeBackend.AUTO,
                                effectiveCause = error,
                                primaryCause = gpuGenerationFailure,
                                message = "GPU generation failed and CPU fallback initialization failed",
                            )
                        )
                    }

                stateMutex.withLock {
                    engine = cpuEngine
                    engineGenerationAttempted = false
                    requestedBackend = LocalComputeBackend.AUTO
                    effectiveBackend = LocalComputeBackend.CPU
                    effectiveContextTokens = contextTokens
                    lastFallbackReason = reason
                    activeCachePath = cpuCacheDir.absolutePath
                    cpuFallbackConsumed = true
                    _state.value = LocalModelSessionState.IDLE
                }
                Log.i(
                    TAG,
                    "$EVENT_LOAD_READY model=$modelId requested=AUTO effective=CPU fallback=true reason=$reason",
                )
                Result.success(Unit)
            }
        }
    }

    private suspend fun generateAttempt(
        config: RuntimeGenerationConfig,
        onToken: suspend (String) -> Unit,
    ): GenerationAttempt = inferenceMutex.withLock {
        val currentEngine = stateMutex.withLock { engine }
            ?: return@withLock GenerationAttempt.Finished(
                Result.failure(IllegalStateException("No LiteRT engine currently loaded"))
            )

        val engineBackendAtStart = stateMutex.withLock { effectiveBackend }
        val coldStart = stateMutex.withLock {
            (!engineGenerationAttempted).also { engineGenerationAttempted = true }
        }
        val retryableOnGpu = requestedBackend == LocalComputeBackend.AUTO &&
            engineBackendAtStart == LocalComputeBackend.GPU &&
            !cpuFallbackConsumed

        _state.value = LocalModelSessionState.INFERRING
        cancelRequested.set(false)
        val requestId = UUID.randomUUID().toString()
        stateMutex.withLock { activeRequestId = requestId }

        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var tokenCount = 0
        val textBuilder = StringBuilder()
        val nativeToolCalls = linkedMapOf<String, ToolCall>()
        val thinkingFilter = LiteRtThinkingFilter()
        var emittedToolCalls = 0
        var rejectedToolCalls = 0
        var duplicateSuppressed = 0
        var callIdComplete = true
        var argumentsStructurallyComplete = true
        val rejections = linkedSetOf<LiteRtToolCallRejection>()

        try {
            val convConfig = ConversationConfig(
                systemInstruction = config.systemInstruction?.let(Contents::of),
                initialMessages = toLiteRtHistory(config),
                tools = config.availableTools.map { tool(HaloOpenApiTool(it)) },
                samplerConfig = SamplerConfig(
                    topK = config.topK,
                    topP = config.topP,
                    temperature = config.temperature,
                ),
                // SECURITY INVARIANT:
                // automaticToolCalling = false
                //
                // LiteRT may DESCRIBE/EMIT a requested tool call; it must never EXECUTE a Halo
                // tool. Execution ownership stays with Halo's runtime (ToolDescriptor ->
                // exposure/policy/approval -> executor -> AgentStep truth). If a future SDK
                // change causes HaloOpenApiTool.execute() to run, it throws loudly on purpose.
                automaticToolCalling = false,
                maxOutputToken = config.maxTokens.takeIf { it > 0 },
            )

            Log.i(
                TAG,
                "$EVENT_GENERATION_START model=$_loadedModelId backend=${backendName(engineBackendAtStart)} " +
                    "tools=${config.availableTools.size} maxTokens=${config.maxTokens} runtimeResidency=${if (coldStart) "COLD" else "WARM"}",
            )

            // Conversation creation is its own failure stage: an engine that initializes but
            // cannot create a conversation is not a verified model.
            nativeAttemptJournal?.arm(
                _loadedModelId ?: "unknown", activeArtifactFingerprint, engineBackendAtStart,
                LiteRtNativeAttemptStage.CONVERSATION_CREATE,
            )
            val conversationCreateStart = System.currentTimeMillis()
            val conversation = try {
                currentEngine.createConversation(convConfig)
            } catch (conversationError: Throwable) {
                Log.e(
                    TAG,
                    "$EVENT_CONVERSATION_FAILED model=$_loadedModelId backend=${backendName(engineBackendAtStart)} " +
                        "type=${conversationError::class.java.simpleName} message=${bounded(conversationError)}",
                    conversationError,
                )
                throw LiteRtModelLoadException(
                    stage = ModelLoadStage.CONVERSATION,
                    requestedBackend = requestedBackend,
                    effectiveCause = conversationError,
                    message = "LiteRT conversation creation failed (${bounded(conversationError)})",
                )
            }
            val conversationCreateMs = System.currentTimeMillis() - conversationCreateStart
            stateMutex.withLock {
                activeConversation = conversation
                conversationCreateDurationMs = conversationCreateMs
            }
            Log.i(TAG, "$EVENT_CONVERSATION_READY model=$_loadedModelId durationMs=$conversationCreateMs")

            val outgoingContents = buildList<Content> {
                if (config.prompt.isNotEmpty()) add(Content.Text(config.prompt))
                config.imageBytes?.let { add(Content.ImageBytes(it)) }
            }.let(Contents::of)
            nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.FIRST_TOKEN_WAIT)
            val messageFlow = conversation.sendMessageAsync(
                Message.user(outgoingContents),
                maxOutputToken = config.maxTokens.takeIf { it > 0 },
            )

            fun emitVisible(text: String) {
                if (text.isEmpty()) return
                if (firstTokenTime == null) {
                    firstTokenTime = System.currentTimeMillis()
                    nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.GENERATING)
                    Log.i(
                        TAG,
                        "$EVENT_FIRST_TOKEN model=$_loadedModelId ttftMs=${firstTokenTime!! - startTime}",
                    )
                }
                tokenCount += estimatedTokens(text)
                textBuilder.append(text)
            }

            messageFlow.collect { message ->
                if (cancelRequested.get()) return@collect
                val chunk = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                message.toolCalls.forEach { call ->
                    emittedToolCalls++
                    // A malformed native call must never execute. It is rejected visibly and
                    // reported through the tool-channel observation instead.
                    val rejection = LiteRtNativeToolCallValidator.rejectionFor(call.name, call.arguments)
                    if (rejection != null) {
                        rejectedToolCalls++
                        rejections += rejection
                        when (rejection) {
                            LiteRtToolCallRejection.MISSING_FUNCTION_NAME -> callIdComplete = false
                            LiteRtToolCallRejection.INCOMPLETE_ARGUMENTS -> argumentsStructurallyComplete = false
                            LiteRtToolCallRejection.CONFLICTING_DUPLICATE_ID -> Unit
                        }
                        Log.i(
                            TAG,
                            "$EVENT_TOOL_CALL_REJECTED model=$_loadedModelId reason=${rejection.label} " +
                                "argsFields=${call.arguments?.size ?: 0}",
                        )
                        return@forEach
                    }
                    val key = "${call.name}:${call.arguments}"
                    if (key !in nativeToolCalls) {
                        nativeToolCalls[key] = ToolCall(
                            id = "litert:${activeRequestId ?: startTime}:${nativeToolCalls.size + 1}",
                            functionName = call.name!!,
                            arguments = call.arguments.orEmpty().filterValues { it != null } as Map<String, Any>,
                        )
                    } else {
                        duplicateSuppressed++
                    }
                }
                if (chunk.isNotEmpty()) {
                    val visible = thinkingFilter.accept(chunk)
                    if (visible.isNotEmpty()) {
                        emitVisible(visible)
                        onToken(visible)
                    }
                }
            }

            val tail = thinkingFilter.finish()
            if (tail.isNotEmpty()) {
                emitVisible(tail)
                onToken(tail)
            }

            val reasoning = thinkingFilter.reasoningText()
            if (reasoning.isNotBlank()) {
                // Raw diagnostic evidence only: internal reasoning must never be spoken.
                Log.i(
                    TAG,
                    "$EVENT_THINKING model=$_loadedModelId rawChars=${thinkingFilter.rawText().length} " +
                        "reasoningChars=${reasoning.length}",
                )
            }

            val (pairedResponses, unpairedModelToolCalls) =
                LiteRtNativeToolCallValidator.pairing(config.initialMessages)
            _lastToolChannelObservation.value = LiteRtNativeToolChannelObservation(
                emittedToolCalls = emittedToolCalls,
                acceptedToolCalls = nativeToolCalls.size,
                rejectedToolCalls = rejectedToolCalls,
                duplicateSuppressed = duplicateSuppressed,
                callIdComplete = callIdComplete,
                argumentsStructurallyComplete = argumentsStructurallyComplete,
                pairedResponseCount = pairedResponses,
                unpairedModelToolCalls = unpairedModelToolCalls,
                rejections = rejections.toList(),
            )

            val endTime = System.currentTimeMillis()
            val durationMs = (endTime - startTime).coerceAtLeast(1L)
            val ttft = firstTokenTime?.let { it - startTime } ?: durationMs
            val tokensPerSec = (tokenCount.toDouble() / (durationMs.toDouble() / 1000.0))

            if (cancelRequested.get()) {
                // cancelProcess() can end the stream without raising: never report a cancelled
                // turn as a completed response.
                Log.w(TAG, "$EVENT_GENERATION_FAILED model=$_loadedModelId reason=CANCELLED chars=${textBuilder.length}")
                _state.value = LocalModelSessionState.IDLE
                return@withLock GenerationAttempt.Finished(
                    Result.failure(CancellationException("LiteRT generation cancelled"))
                )
            }

            _state.value = LocalModelSessionState.IDLE

            Log.i(
                TAG,
                "$EVENT_GENERATION_COMPLETE model=$_loadedModelId durationMs=$durationMs " +
                    "ttftMs=$ttft chars=${textBuilder.length} estimatedTokens=$tokenCount " +
                    "toolCalls=${nativeToolCalls.size} inputEstimatedTokens=${(config.prompt.length + config.systemInstruction.orEmpty().length + config.initialMessages.sumOf { it.content.length } + 2) / 3} " +
                    "historyMessageCount=${config.initialMessages.size} engineLoadMs=${engineInitDurationMs ?: 0} conversationCreateMs=${conversationCreateDurationMs ?: 0} " +
                    "generationMs=$durationMs outputEstimatedTokens=$tokenCount effectiveBackend=$engineBackendAtStart runtimeResidency=${if (coldStart) "COLD" else "WARM"}",
            )

            GenerationAttempt.Finished(
                Result.success(
                    RuntimeGenerationResult(
                        text = textBuilder.toString(),
                        toolCalls = nativeToolCalls.values.toList(),
                        tokenCount = tokenCount,
                        timeToFirstTokenMs = ttft,
                        tokensPerSecond = tokensPerSec,
                        durationMs = durationMs,
                        reasoningText = reasoning.ifBlank { null },
                        coldStart = coldStart,
                    )
                )
            )
        } catch (e: CancellationException) {
            val conversation = stateMutex.withLock { activeConversation }
            if (conversation != null) {
                withContext(NonCancellable + Dispatchers.Default) {
                    runCatching { conversation.cancelProcess() }
                }
            }
            _state.value = LocalModelSessionState.IDLE
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "$EVENT_GENERATION_FAILED model=$_loadedModelId backend=${backendName(engineBackendAtStart)} " +
                    "type=${e::class.java.simpleName} message=${bounded(e)}",
                e,
            )
            val staged = e as? LiteRtModelLoadException ?: LiteRtModelLoadException(
                stage = if (textBuilder.isEmpty() && firstTokenTime == null && !cancelRequested.get()) {
                    ModelLoadStage.FIRST_TOKEN
                } else {
                    ModelLoadStage.GENERATION
                },
                requestedBackend = requestedBackend,
                effectiveCause = e,
                message = "LiteRT generation failed (${bounded(e)})",
            )
            if (retryableOnGpu && textBuilder.isEmpty() && !cancelRequested.get()) {
                _state.value = LocalModelSessionState.IDLE
                GenerationAttempt.RetryableGpuFailure(staged, currentEngine)
            } else {
                _state.value = LocalModelSessionState.ERROR
                GenerationAttempt.Finished(Result.failure(staged))
            }
        } finally {
            val conversation = withContext(NonCancellable) { stateMutex.withLock {
                val detached = activeConversation
                activeConversation = null
                if (activeRequestId == requestId) activeRequestId = null
                detached
            } }
            nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.TEARDOWN)
            closeConversationQuietly(conversation, cancel = false)
            nativeAttemptJournal?.clear()
        }
    }

    override suspend fun cancelGeneration() {
        cancelRequested.set(true)
        val conversation = stateMutex.withLock { activeConversation }
        if (conversation != null) {
            withContext(Dispatchers.Default) {
                runCatching { conversation.cancelProcess() }
            }
        }
    }

    override suspend fun unloadModel(): Result<Unit> = loadMutex.withLock {
        _state.value = LocalModelSessionState.UNLOADING
        val unloadingModelId = _loadedModelId
        if (unloadingModelId != null) nativeAttemptJournal?.arm(
            unloadingModelId, activeArtifactFingerprint, effectiveBackend, LiteRtNativeAttemptStage.TEARDOWN,
        )
        val replacedResources = inferenceMutex.withLock {
            stateMutex.withLock {
                val detached = detachRuntimeState()
                _state.value = LocalModelSessionState.IDLE
                detached
            }
        }
        nativeAttemptJournal?.advance(LiteRtNativeAttemptStage.TEARDOWN)
        closeConversationQuietly(replacedResources.conversation, cancel = true)
        closeEngineQuietly(replacedResources.engine)
        // Clean endpoint reached: the pending native-attempt record must not outlive the attempt.
        nativeAttemptJournal?.clear()
        Log.i(TAG, "$EVENT_UNLOAD model=$unloadingModelId")
        Result.success(Unit)
    }

    /** Detach ownership while locked; native teardown must happen after releasing stateMutex. */
    private fun detachRuntimeState(): DetachedRuntimeResources {
        val detached = DetachedRuntimeResources(engine, activeConversation)
        activeConversation = null
        engine = null
        engineGenerationAttempted = false
        _loadedModelId = null
        effectiveContextTokens = null
        requestedContextTokens = null
        contextFallbackReason = null
        engineInitDurationMs = null
        conversationCreateDurationMs = null
        activeModelPath = null
        activeArtifactFingerprint = null
        activeCacheRootPath = null
        activeLoadConfig = null
        cpuFallbackConsumed = false
        activeRequestId = null
        return detached
    }

    private fun toLiteRtHistory(config: RuntimeGenerationConfig): List<Message> {
        val functionByCallId = buildMap {
            config.initialMessages.forEach { message ->
                message.toolCalls.forEach { call -> put(call.id, call.functionName) }
            }
        }
        return config.initialMessages.map { message ->
            when (message.role) {
                Role.ASSISTANT -> Message.model(
                    Contents.of(message.content),
                    message.toolCalls.map { LiteRtToolCall(it.functionName, it.arguments) },
                    emptyMap(),
                )
                Role.TOOL -> {
                    val name = message.toolCallId?.let(functionByCallId::get) ?: "halo_tool"
                    Message.tool(Contents.of(Content.ToolResponse(name, message.content)))
                }
                Role.SYSTEM -> Message.system(message.content)
                Role.USER -> Message.user(message.content)
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "LiteRtRuntime"

        /** Character-based estimate: NOT tokenizer-accurate, reported as estimatedTokens. */
        internal fun estimatedTokens(text: String): Int =
            (text.length / 3.8).toInt().coerceAtLeast(1)

        const val EVENT_LOAD_START = "HALO_LITERT_LOAD_START"
        const val EVENT_LOAD_READY = "HALO_LITERT_LOAD_READY"
        const val EVENT_LOAD_FAILED = "HALO_LITERT_LOAD_FAILED"
        const val EVENT_GPU_FAILED = "HALO_LITERT_GPU_FAILED"
        const val EVENT_CPU_FALLBACK = "HALO_LITERT_CPU_FALLBACK"
        const val EVENT_CONTEXT_DOWNSHIFT = "HALO_LITERT_CONTEXT_DOWNSHIFT"
        const val EVENT_CONTEXT_READY = "HALO_LITERT_CONTEXT_READY"
        const val EVENT_CONVERSATION_READY = "HALO_LITERT_CONVERSATION_READY"
        const val EVENT_CONVERSATION_FAILED = "HALO_LITERT_CONVERSATION_FAILED"
        const val EVENT_GENERATION_START = "HALO_LITERT_GENERATION_START"
        const val EVENT_FIRST_TOKEN = "HALO_LITERT_FIRST_TOKEN"
        const val EVENT_GENERATION_COMPLETE = "HALO_LITERT_GENERATION_COMPLETE"
        const val EVENT_GENERATION_FAILED = "HALO_LITERT_GENERATION_FAILED"
        const val EVENT_THINKING = "HALO_LITERT_THINKING"
        const val EVENT_UNLOAD = "HALO_LITERT_UNLOAD"
        const val EVENT_TOOL_CALL_REJECTED = "HALO_LITERT_TOOL_CALL_REJECTED"
    }
}
