package com.halo.providers.litert.runtime

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
    private var activeBackend: LocalComputeBackend = LocalComputeBackend.AUTO

    private val stateMutex = Mutex()
    private val inferenceMutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)

    override fun isLoaded(): Boolean = engine != null && _state.value != LocalModelSessionState.UNLOADING

    override suspend fun loadModel(
        modelId: String,
        modelPath: String,
        config: RuntimeLoadConfig,
    ): Result<RuntimeLoadResult> = stateMutex.withLock {
        _state.value = LocalModelSessionState.LOADING
        try {
            val file = File(modelPath)
            require(file.exists() && file.isFile) { "Model file does not exist: $modelPath" }

            unloadInternal()

            val modelCacheDir = File(context.filesDir, "litertlm_cache/${modelId.replace('/', '_')}-${file.length()}-${file.lastModified()}")
            if (!modelCacheDir.exists()) modelCacheDir.mkdirs()

            var usedBackend = LocalComputeBackend.GPU
            var fallbackReason: String? = null

            val engineConfigGpu = EngineConfig(
                modelPath = file.absolutePath,
                backend = Backend.GPU(),
                maxNumTokens = config.contextSize,
                cacheDir = modelCacheDir.absolutePath,
            )

            var createdEngine: Engine? = runCatching { Engine(engineConfigGpu) }.getOrNull()

            if (createdEngine == null && config.computeBackend != LocalComputeBackend.GPU) {
                usedBackend = LocalComputeBackend.CPU
                fallbackReason = "GPU backend initialization failed; fell back to CPU"
                val engineConfigCpu = EngineConfig(
                    modelPath = file.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = config.contextSize,
                    cacheDir = modelCacheDir.absolutePath,
                )
                createdEngine = runCatching { Engine(engineConfigCpu) }.getOrNull()
            }

            if (createdEngine == null) {
                throw IllegalStateException("Failed to initialize LiteRT-LM engine on both GPU and CPU")
            }

            engine = createdEngine
            activeBackend = usedBackend
            _loadedModelId = modelId
            _state.value = LocalModelSessionState.IDLE

            Result.success(
                RuntimeLoadResult(
                    activeBackend = usedBackend,
                    activeGpuLayers = if (usedBackend == LocalComputeBackend.GPU) 1 else 0,
                    effectiveContextSize = config.contextSize,
                    fallbackReason = fallbackReason,
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
        val currentEngine = engine ?: return Result.failure(IllegalStateException("No LiteRT engine currently loaded"))

        _state.value = LocalModelSessionState.INFERRING
        cancelRequested.set(false)

        val startTime = System.currentTimeMillis()
        var firstTokenTime: Long? = null
        var tokenCount = 0
        val textBuilder = StringBuilder()

        try {
            val handleField = Engine::class.java.getDeclaredField("handle").apply { isAccessible = true }
            val enginePtr = (handleField.get(currentEngine) as? Long)
                ?: throw IllegalStateException("Engine handle is null or closed")

            val conversation = Conversation(enginePtr)
            activeConversation = conversation

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
            activeConversation?.cancelProcess()
            _state.value = LocalModelSessionState.IDLE
            throw e
        } catch (e: Throwable) {
            _state.value = LocalModelSessionState.ERROR
            Result.failure(e)
        } finally {
            runCatching { activeConversation?.close() }
            activeConversation = null
        }
    }

    override suspend fun cancelGeneration() {
        cancelRequested.set(true)
        runCatching { activeConversation?.cancelProcess() }
    }

    override suspend fun unloadModel(): Result<Unit> = stateMutex.withLock {
        _state.value = LocalModelSessionState.UNLOADING
        unloadInternal()
        _state.value = LocalModelSessionState.IDLE
        Result.success(Unit)
    }

    private fun unloadInternal() {
        runCatching { activeConversation?.cancelProcess() }
        runCatching { activeConversation?.close() }
        activeConversation = null
        runCatching { engine?.close() }
        engine = null
        _loadedModelId = null
    }

    companion object {
        private const val TAG = "LiteRtRuntime"
    }
}
