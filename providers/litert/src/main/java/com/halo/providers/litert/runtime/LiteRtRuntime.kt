package com.halo.providers.litert.runtime

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
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

/** Stage at which a LiteRT load failed, so callers never have to guess from a message. */
enum class ModelLoadStage { ABI, ARTIFACT, ENGINE_INIT, CONVERSATION, GENERATION }

/**
 * Preserves the *first* failure (usually the GPU attempt) alongside the final one (usually the CPU
 * fallback). Without both, a failed on-device load is impossible to diagnose from the field.
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
)

data class LiteRtRuntimeSnapshot(
    val modelId: String?,
    val state: LocalModelSessionState,
    val requestedBackend: LocalComputeBackend?,
    val effectiveBackend: LocalComputeBackend?,
    val contextTokens: Int?,
    val fallbackReason: String?,
    val activeRequestId: String?,
    val cachePath: String?,
)

/** Outcome of one generation attempt. A pre-first-token GPU failure may be retried on CPU. */
private sealed interface GenerationAttempt {
    data class Finished(val result: Result<RuntimeGenerationResult>) : GenerationAttempt
    data class RetryableGpuFailure(val cause: Throwable) : GenerationAttempt
}

class LiteRtRuntime(
    private val context: Context,
    private val scope: CoroutineScope,
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
    private var lastFallbackReason: String? = null
    private var activeCachePath: String? = null

    /** Everything needed to rebuild this exact model on another accelerator. */
    private var activeModelPath: String? = null
    private var activeCacheRootPath: String? = null
    private var activeLoadConfig: RuntimeLoadConfig? = null

    /**
     * Some devices (notably emulators and older GPUs) report a successful GPU `initialize()` and
     * only fail on the first inference. One late rebuild on CPU is allowed per residency.
     */
    private var cpuFallbackConsumed = false

    private var activeRequestId: String? = null

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
        fallbackReason = lastFallbackReason,
        activeRequestId = activeRequestId,
        cachePath = activeCachePath,
    )

    /**
     * LiteRT Android local inference only ships arm64-v8a native libraries. Entering
     * `Engine.initialize()` on an unsupported ABI produces an opaque native abort instead of a
     * diagnosable failure, so it is rejected here.
     */
    private fun abiBlocker(): String? {
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        return if (abis.any { it.equals(SUPPORTED_ABI, ignoreCase = true) }) {
            null
        } else {
            "This device does not support arm64-v8a native inference (SUPPORTED_ABIS=${abis.joinToString(",")})"
        }
    }

    private fun backendName(backend: LocalComputeBackend): String = when (backend) {
        LocalComputeBackend.GPU -> "GPU"
        LocalComputeBackend.CPU -> "CPU"
        LocalComputeBackend.AUTO -> "AUTO"
    }

    private fun createAndInitializeEngine(
        modelPath: String,
        backend: LocalComputeBackend,
        contextTokens: Int?,
        cacheDir: File,
    ): Result<Engine> {
        val litertBackend = when (backend) {
            LocalComputeBackend.GPU -> Backend.GPU()
            LocalComputeBackend.CPU -> Backend.CPU()
            LocalComputeBackend.AUTO -> Backend.GPU()
        }

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = litertBackend,
            // null => the native engine applies the model's own default context window.
            maxNumTokens = contextTokens,
            // Inference compile/weight caches live under filesDir: the OS may purge cacheDir.
            cacheDir = cacheDir.absolutePath,
        )

        var created: Engine? = null
        return try {
            created = Engine(engineConfig)
            created.initialize()
            Result.success(created)
        } catch (error: Throwable) {
            created?.let { runCatching { it.close() } }
            Result.failure(error)
        }
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

    private fun bounded(error: Throwable?): String = error?.let {
        "${it::class.java.simpleName}: ${it.message?.lineSequence()?.firstOrNull()?.take(300)}"
    } ?: "unknown"

    private fun resolveEngine(
        file: File,
        modelId: String,
        config: RuntimeLoadConfig,
    ): EngineLoadOutcome {
        val artifactFingerprint = sha256(file)
        val modelCacheRoot = File(
            context.filesDir,
            "litertlm_cache/${modelId.replace('/', '_')}-$artifactFingerprint",
        )
        // A failed GPU initialization must never leave state that influences the CPU fallback.
        val gpuCacheDir = File(modelCacheRoot, GPU_CACHE_SUBDIR).also { if (!it.exists()) it.mkdirs() }
        val cpuCacheDir = File(modelCacheRoot, CPU_CACHE_SUBDIR).also { if (!it.exists()) it.mkdirs() }
        val contextTokens = if (config.useModelDefaultContext) null else config.contextSize

        when (config.computeBackend) {
            LocalComputeBackend.CPU -> {
                val engine = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.CPU, contextTokens, cpuCacheDir)
                    .getOrElse { error ->
                        Log.e(
                            TAG,
                            "$EVENT_LOAD_FAILED model=$modelId backend=CPU stage=ENGINE_INIT type=${error::class.java.simpleName} " +
                                "message=${bounded(error)} sha=$artifactFingerprint bytes=${file.length()} " +
                                "context=${contextTokens ?: "model-default"}",
                            error,
                        )
                        throw LiteRtModelLoadException(
                            stage = ModelLoadStage.ENGINE_INIT,
                            requestedBackend = LocalComputeBackend.CPU,
                            effectiveCause = error,
                            message = "CPU engine initialization failed (${bounded(error)})",
                        )
                    }
                return EngineLoadOutcome(engine, modelCacheRoot, cpuCacheDir, LocalComputeBackend.CPU, LocalComputeBackend.CPU, null)
            }
            LocalComputeBackend.GPU -> {
                val engine = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.GPU, contextTokens, gpuCacheDir)
                    .getOrElse { error ->
                        Log.e(
                            TAG,
                            "$EVENT_LOAD_FAILED model=$modelId backend=GPU stage=ENGINE_INIT type=${error::class.java.simpleName} " +
                                "message=${bounded(error)} sha=$artifactFingerprint bytes=${file.length()} " +
                                "context=${contextTokens ?: "model-default"}",
                            error,
                        )
                        throw LiteRtModelLoadException(
                            stage = ModelLoadStage.ENGINE_INIT,
                            requestedBackend = LocalComputeBackend.GPU,
                            effectiveCause = error,
                            message = "GPU engine initialization failed (${bounded(error)})",
                        )
                    }
                return EngineLoadOutcome(engine, modelCacheRoot, gpuCacheDir, LocalComputeBackend.GPU, LocalComputeBackend.GPU, null)
            }
            LocalComputeBackend.AUTO -> {
                val gpuResult = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.GPU, contextTokens, gpuCacheDir)
                if (gpuResult.isSuccess) {
                    return EngineLoadOutcome(
                        gpuResult.getOrThrow(),
                        modelCacheRoot,
                        gpuCacheDir,
                        LocalComputeBackend.AUTO,
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
                // runs in its own cache directory.
                val cpuResult = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.CPU, contextTokens, cpuCacheDir)
                if (cpuResult.isFailure) {
                    val cpuError = cpuResult.exceptionOrNull()!!
                    Log.e(
                        TAG,
                        "$EVENT_LOAD_FAILED model=$modelId backend=AUTO stage=ENGINE_INIT gpu=$gpuReason cpu=${bounded(cpuError)}",
                        cpuError,
                    )
                    throw LiteRtModelLoadException(
                        stage = ModelLoadStage.ENGINE_INIT,
                        requestedBackend = LocalComputeBackend.AUTO,
                        effectiveCause = cpuError,
                        primaryCause = gpuError,
                        message = "GPU and CPU engine initialization both failed. " +
                            "GPU: ${bounded(gpuError)}. CPU: ${bounded(cpuError)}.",
                    )
                }

                val cpuEngine = cpuResult.getOrThrow()
                Log.i(TAG, "$EVENT_CPU_FALLBACK model=$modelId phase=INIT reason=$gpuReason")
                return EngineLoadOutcome(
                    cpuEngine,
                    modelCacheRoot,
                    cpuCacheDir,
                    LocalComputeBackend.AUTO,
                    LocalComputeBackend.CPU,
                    gpuReason,
                )
            }
        }
    }

    override suspend fun loadModel(
        modelId: String,
        modelPath: String,
        config: RuntimeLoadConfig,
    ): Result<RuntimeLoadResult> = loadMutex.withLock {
        _state.value = LocalModelSessionState.LOADING
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

            val contextTokens = if (config.useModelDefaultContext) null else config.contextSize
            Log.i(
                TAG,
                "$EVENT_LOAD_START model=$modelId backend=${backendName(config.computeBackend)} " +
                    "bytes=${file.length()} context=${contextTokens ?: "model-default"} path=$modelPath",
            )

            // Close any previous engine before allocating a new one to avoid dual-model OOM.
            val replacedEngine = inferenceMutex.withLock {
                stateMutex.withLock {
                    val old = engine
                    unloadInternal()
                    old
                }
            }
            closeEngineQuietly(replacedEngine)

            val outcome = withContext(Dispatchers.Default) { resolveEngine(file, modelId, config) }

            stateMutex.withLock {
                engine = outcome.engine
                requestedBackend = outcome.requestedBackend
                effectiveBackend = outcome.effectiveBackend
                effectiveContextTokens = contextTokens
                lastFallbackReason = outcome.fallbackReason
                activeCachePath = outcome.cacheDir.absolutePath
                activeCacheRootPath = outcome.cacheRoot.absolutePath
                activeModelPath = file.absolutePath
                activeLoadConfig = config
                cpuFallbackConsumed = false
                _loadedModelId = modelId
                _state.value = LocalModelSessionState.IDLE
            }

            Log.i(
                TAG,
                "$EVENT_LOAD_READY model=$modelId requested=${backendName(outcome.requestedBackend)} " +
                    "effective=${backendName(outcome.effectiveBackend)} fallback=${outcome.fallbackReason != null}",
            )

            Result.success(
                RuntimeLoadResult(
                    activeBackend = outcome.effectiveBackend,
                    activeGpuLayers = if (outcome.effectiveBackend == LocalComputeBackend.GPU) 1 else 0,
                    effectiveContextSize = config.contextSize,
                    fallbackReason = outcome.fallbackReason,
                )
            )
        } catch (e: CancellationException) {
            _state.value = LocalModelSessionState.IDLE
            throw e
        } catch (e: Throwable) {
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
                    "$EVENT_GPU_FAILED model=$_loadedModelId phase=GENERATION type=${attempt.cause::class.java.simpleName} " +
                        "message=${bounded(attempt.cause)}",
                    attempt.cause,
                )
                if (!rebuildResidentEngineOnCpu(reason)) {
                    return Result.failure(
                        LiteRtModelLoadException(
                            stage = ModelLoadStage.GENERATION,
                            requestedBackend = LocalComputeBackend.AUTO,
                            effectiveCause = attempt.cause,
                            message = "$reason; no CPU fallback engine could be installed",
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
     * stays valid. Runs under [loadMutex] and *without* [inferenceMutex], which matches the lock
     * order used by [loadModel].
     */
    private suspend fun rebuildResidentEngineOnCpu(reason: String): Boolean {
        val modelId = stateMutex.withLock { _loadedModelId } ?: return false
        val modelPath = activeModelPath ?: return false
        val cacheRoot = activeCacheRootPath?.let(::File)
            ?: File(context.filesDir, "litertlm_cache/${modelId.replace('/', '_')}").also { it.mkdirs() }
        val cpuCacheDir = File(cacheRoot, CPU_CACHE_SUBDIR).also { if (!it.exists()) it.mkdirs() }
        val contextTokens = activeLoadConfig?.let { if (it.useModelDefaultContext) null else it.contextSize }
            ?: effectiveContextTokens

        return loadMutex.withLock {
            val previous = stateMutex.withLock { engine }
            val cpuEngine = createAndInitializeEngine(modelPath, LocalComputeBackend.CPU, contextTokens, cpuCacheDir)
                .getOrElse { error ->
                    Log.e(
                        TAG,
                        "$EVENT_LOAD_FAILED model=$modelId backend=CPU phase=GENERATION_FALLBACK " +
                            "type=${error::class.java.simpleName} message=${bounded(error)}",
                        error,
                    )
                    return@withLock false
                }

            val swapped = stateMutex.withLock {
                if (engine !== previous) {
                    // A concurrent load already replaced the engine; keep the newer one.
                    false
                } else {
                    engine = cpuEngine
                    requestedBackend = LocalComputeBackend.AUTO
                    effectiveBackend = LocalComputeBackend.CPU
                    effectiveContextTokens = contextTokens
                    lastFallbackReason = reason
                    activeCachePath = cpuCacheDir.absolutePath
                    cpuFallbackConsumed = true
                    _state.value = LocalModelSessionState.IDLE
                    true
                }
            }
            // Both closes happen outside the state lock.
            if (swapped) {
                closeEngineQuietly(previous)
                Log.i(
                    TAG,
                    "$EVENT_LOAD_READY model=$modelId requested=AUTO effective=CPU fallback=true reason=$reason",
                )
            } else {
                closeEngineQuietly(cpuEngine)
                Log.w(TAG, "$EVENT_CPU_FALLBACK model=$modelId phase=GENERATION abandoned=resident-engine-changed")
            }
            swapped
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
                automaticToolCalling = false,
                maxOutputToken = config.maxTokens.takeIf { it > 0 },
            )

            Log.i(
                TAG,
                "$EVENT_GENERATION_START model=$_loadedModelId backend=${backendName(engineBackendAtStart)} " +
                    "tools=${config.availableTools.size} maxTokens=${config.maxTokens}",
            )

            val conversation = currentEngine.createConversation(convConfig)
            stateMutex.withLock { activeConversation = conversation }

            val outgoingContents = buildList<Content> {
                if (config.prompt.isNotEmpty()) add(Content.Text(config.prompt))
                config.imageBytes?.let { add(Content.ImageBytes(it)) }
            }.let(Contents::of)
            val messageFlow = conversation.sendMessageAsync(Message.user(outgoingContents))

            messageFlow.collect { message ->
                if (cancelRequested.get()) return@collect
                val chunk = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                message.toolCalls.forEach { call ->
                    val key = "${call.name}:${call.arguments}"
                    if (key !in nativeToolCalls) {
                        nativeToolCalls[key] = ToolCall(
                            id = "litert:${activeRequestId ?: startTime}:${nativeToolCalls.size + 1}",
                            functionName = call.name,
                            arguments = call.arguments.filterValues { it != null } as Map<String, Any>,
                        )
                    }
                }
                if (chunk.isNotEmpty()) {
                    if (firstTokenTime == null) {
                        firstTokenTime = System.currentTimeMillis()
                        Log.i(
                            TAG,
                            "$EVENT_FIRST_TOKEN model=$_loadedModelId ttftMs=${firstTokenTime!! - startTime}",
                        )
                    }
                    val estimatedTokens = (chunk.length / 3.8).toInt().coerceAtLeast(1)
                    tokenCount += estimatedTokens
                    textBuilder.append(chunk)
                    onToken(chunk)
                }
            }

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
                    Result.failure(IllegalStateException("LiteRT generation was cancelled"))
                )
            }

            _state.value = LocalModelSessionState.IDLE

            Log.i(
                TAG,
                "$EVENT_GENERATION_COMPLETE model=$_loadedModelId durationMs=$durationMs " +
                    "ttftMs=$ttft chars=${textBuilder.length} toolCalls=${nativeToolCalls.size}",
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
                    )
                )
            )
        } catch (e: CancellationException) {
            stateMutex.withLock { activeConversation?.cancelProcess() }
            _state.value = LocalModelSessionState.IDLE
            throw e
        } catch (e: Throwable) {
            Log.e(
                TAG,
                "$EVENT_GENERATION_FAILED model=$_loadedModelId backend=${backendName(engineBackendAtStart)} " +
                    "type=${e::class.java.simpleName} message=${bounded(e)}",
                e,
            )
            if (retryableOnGpu && textBuilder.isEmpty() && !cancelRequested.get()) {
                _state.value = LocalModelSessionState.IDLE
                GenerationAttempt.RetryableGpuFailure(e)
            } else {
                _state.value = LocalModelSessionState.ERROR
                GenerationAttempt.Finished(Result.failure(e))
            }
        } finally {
            stateMutex.withLock {
                runCatching { activeConversation?.close() }
                activeConversation = null
                if (activeRequestId == requestId) activeRequestId = null
            }
        }
    }

    override suspend fun cancelGeneration() {
        cancelRequested.set(true)
        stateMutex.withLock {
            runCatching { activeConversation?.cancelProcess() }
        }
    }

    override suspend fun unloadModel(): Result<Unit> = loadMutex.withLock {
        _state.value = LocalModelSessionState.UNLOADING
        val replacedEngine = inferenceMutex.withLock {
            stateMutex.withLock {
                val old = engine
                unloadInternal()
                _state.value = LocalModelSessionState.IDLE
                old
            }
        }
        closeEngineQuietly(replacedEngine)
        Result.success(Unit)
    }

    private fun unloadInternal() {
        runCatching { activeConversation?.cancelProcess() }
        runCatching { activeConversation?.close() }
        activeConversation = null
        engine = null
        _loadedModelId = null
        effectiveContextTokens = null
        activeModelPath = null
        activeCacheRootPath = null
        activeLoadConfig = null
        cpuFallbackConsumed = false
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
        private const val SUPPORTED_ABI = "arm64-v8a"
        private const val GPU_CACHE_SUBDIR = "gpu"
        private const val CPU_CACHE_SUBDIR = "cpu"

        const val EVENT_LOAD_START = "HALO_LITERT_LOAD_START"
        const val EVENT_LOAD_READY = "HALO_LITERT_LOAD_READY"
        const val EVENT_LOAD_FAILED = "HALO_LITERT_LOAD_FAILED"
        const val EVENT_GPU_FAILED = "HALO_LITERT_GPU_FAILED"
        const val EVENT_CPU_FALLBACK = "HALO_LITERT_CPU_FALLBACK"
        const val EVENT_GENERATION_START = "HALO_LITERT_GENERATION_START"
        const val EVENT_FIRST_TOKEN = "HALO_LITERT_FIRST_TOKEN"
        const val EVENT_GENERATION_COMPLETE = "HALO_LITERT_GENERATION_COMPLETE"
        const val EVENT_GENERATION_FAILED = "HALO_LITERT_GENERATION_FAILED"
    }
}
