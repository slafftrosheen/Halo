package com.halo.providers.litert.runtime

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalInferenceRuntime
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.LocalModelSessionState
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.RuntimeLoadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class EngineLoadOutcome(
    val engine: Engine,
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

class LiteRtRuntime(
    private val context: Context,
    private val scope: CoroutineScope,
) : LocalInferenceRuntime {

    override val format: LocalModelFormat = LocalModelFormat.LITERT_LM

    private val _state = MutableStateFlow(LocalModelSessionState.IDLE)
    override val state: StateFlow<LocalModelSessionState> = _state.asStateFlow()

    private var _loadedModelId: String? = null
    override val loadedModelId: String? get() = _loadedModelId

    private var engine: Engine? = null
    private var activeConversation: Conversation? = null
    private var requestedBackend: LocalComputeBackend = LocalComputeBackend.AUTO
    private var effectiveBackend: LocalComputeBackend = LocalComputeBackend.AUTO
    private var effectiveContextSize: Int = 4096
    private var lastFallbackReason: String? = null
    private var activeCachePath: String? = null
    private var activeRequestId: String? = null

    private val stateMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val loadMutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)

    override fun isLoaded(): Boolean = engine != null && _state.value != LocalModelSessionState.UNLOADING

    fun snapshot(): LiteRtRuntimeSnapshot = LiteRtRuntimeSnapshot(
        modelId = _loadedModelId,
        state = _state.value,
        requestedBackend = requestedBackend,
        effectiveBackend = effectiveBackend,
        contextTokens = effectiveContextSize,
        fallbackReason = lastFallbackReason,
        activeRequestId = activeRequestId,
        cachePath = activeCachePath
    )

    private suspend fun createAndInitializeEngine(
        modelPath: String,
        backend: LocalComputeBackend,
        contextSize: Int,
        cacheDir: File
    ): Result<Engine> = withContext(Dispatchers.Default) {
        val litertBackend = when (backend) {
            LocalComputeBackend.GPU -> Backend.GPU()
            LocalComputeBackend.CPU -> Backend.CPU()
            LocalComputeBackend.AUTO -> Backend.GPU()
        }

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = litertBackend,
            maxNumTokens = contextSize,
            cacheDir = cacheDir.absolutePath,
        )

        runCatching {
            val created = Engine(engineConfig)
            created.initialize()
            created
        }
    }

    private suspend fun resolveEngine(
        file: File,
        modelId: String,
        config: RuntimeLoadConfig
    ): EngineLoadOutcome {
        val modelCacheDir = File(context.filesDir, "litertlm_cache/${modelId.replace('/', '_')}-${file.length()}-${file.lastModified()}")
        if (!modelCacheDir.exists()) modelCacheDir.mkdirs()

        val reqBackend = config.computeBackend

        when (reqBackend) {
            LocalComputeBackend.CPU -> {
                val engine = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.CPU, config.contextSize, modelCacheDir).getOrThrow()
                return EngineLoadOutcome(engine, LocalComputeBackend.CPU, LocalComputeBackend.CPU, null)
            }
            LocalComputeBackend.GPU -> {
                val engine = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.GPU, config.contextSize, modelCacheDir).getOrThrow()
                return EngineLoadOutcome(engine, LocalComputeBackend.GPU, LocalComputeBackend.GPU, null)
            }
            LocalComputeBackend.AUTO -> {
                val gpuResult = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.GPU, config.contextSize, modelCacheDir)
                if (gpuResult.isSuccess) {
                    return EngineLoadOutcome(gpuResult.getOrThrow(), LocalComputeBackend.AUTO, LocalComputeBackend.GPU, null)
                }

                val fallbackMsg = "GPU engine initialization failed (${gpuResult.exceptionOrNull()?.message}); fell back to CPU"
                Log.w(TAG, fallbackMsg)

                val cpuEngine = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.CPU, config.contextSize, modelCacheDir).getOrThrow()
                return EngineLoadOutcome(cpuEngine, LocalComputeBackend.AUTO, LocalComputeBackend.CPU, fallbackMsg)
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

            val outcome = resolveEngine(file, modelId, config)

            var oldEngineToClose: Engine? = null
            stateMutex.withLock {
                oldEngineToClose = engine
                unloadInternal()
                engine = outcome.engine
                requestedBackend = outcome.requestedBackend
                effectiveBackend = outcome.effectiveBackend
                effectiveContextSize = config.contextSize
                lastFallbackReason = outcome.fallbackReason
                activeCachePath = file.absolutePath
                _loadedModelId = modelId
                _state.value = LocalModelSessionState.IDLE
            }

            oldEngineToClose?.let { runCatching { it.close() } }

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
    ): Result<RuntimeGenerationResult> = inferenceMutex.withLock {
        val currentEngine = stateMutex.withLock { engine }
            ?: return Result.failure(IllegalStateException("No LiteRT engine currently loaded"))

        _state.value = LocalModelSessionState.INFERRING
        cancelRequested.set(false)

        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var tokenCount = 0
        val textBuilder = StringBuilder()

        try {
            val convConfig = ConversationConfig(
                systemInstruction = config.systemInstruction?.let { com.google.ai.edge.litertlm.Contents.of(it) },
                automaticToolCalling = false,
            )

            val conversation = currentEngine.createConversation(convConfig)
            stateMutex.withLock { activeConversation = conversation }

            val messageFlow = conversation.sendMessageAsync(Message.user(config.prompt))

            messageFlow.collect { message ->
                if (cancelRequested.get()) return@collect
                val chunk = message.toString()
                if (chunk.isNotEmpty()) {
                    if (firstTokenTime == null) firstTokenTime = System.currentTimeMillis()
                    tokenCount++
                    textBuilder.append(chunk)
                    onToken(chunk)
                }
            }

            val endTime = System.currentTimeMillis()
            val durationMs = (endTime - startTime).coerceAtLeast(1L)
            val ttft = firstTokenTime?.let { it - startTime } ?: durationMs
            val tokensPerSec = (tokenCount.toDouble() / (durationMs.toDouble() / 1000.0))

            _state.value = LocalModelSessionState.IDLE

            Result.success(
                RuntimeGenerationResult(
                    text = textBuilder.toString(),
                    tokenCount = tokenCount,
                    timeToFirstTokenMs = ttft,
                    tokensPerSecond = tokensPerSec,
                    durationMs = durationMs,
                )
            )
        } catch (e: CancellationException) {
            stateMutex.withLock { activeConversation?.cancelProcess() }
            _state.value = LocalModelSessionState.IDLE
            throw e
        } catch (e: Throwable) {
            _state.value = LocalModelSessionState.ERROR
            Result.failure(e)
        } finally {
            stateMutex.withLock {
                runCatching { activeConversation?.close() }
                activeConversation = null
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
        var engineToClose: Engine? = null
        stateMutex.withLock {
            engineToClose = engine
            unloadInternal()
            _state.value = LocalModelSessionState.IDLE
        }
        engineToClose?.let { runCatching { it.close() } }
        Result.success(Unit)
    }

    private fun unloadInternal() {
        runCatching { activeConversation?.cancelProcess() }
        runCatching { activeConversation?.close() }
        activeConversation = null
        engine = null
        _loadedModelId = null
    }

    companion object {
        private const val TAG = "LiteRtRuntime"
    }
}
