package com.halo.providers.litert.runtime

import android.content.Context
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
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.RuntimeLoadResult
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.ToolCall
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
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

data class EngineLoadOutcome(
    val engine: Engine,
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

        var created: Engine? = null
        try {
            created = Engine(engineConfig)
            created.initialize()
            Result.success(created)
        } catch (error: Throwable) {
            created?.let { runCatching { it.close() } }
            Result.failure(error)
        }
    }

    private suspend fun resolveEngine(
        file: File,
        modelId: String,
        config: RuntimeLoadConfig
    ): EngineLoadOutcome {
        val artifactFingerprint = sha256(file)
        val modelCacheDir = File(context.filesDir, "litertlm_cache/${modelId.replace('/', '_')}-$artifactFingerprint")
        if (!modelCacheDir.exists()) modelCacheDir.mkdirs()

        val reqBackend = config.computeBackend

        when (reqBackend) {
            LocalComputeBackend.CPU -> {
                val engine = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.CPU, config.contextSize, modelCacheDir).getOrThrow()
                return EngineLoadOutcome(engine, modelCacheDir, LocalComputeBackend.CPU, LocalComputeBackend.CPU, null)
            }
            LocalComputeBackend.GPU -> {
                val engine = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.GPU, config.contextSize, modelCacheDir).getOrThrow()
                return EngineLoadOutcome(engine, modelCacheDir, LocalComputeBackend.GPU, LocalComputeBackend.GPU, null)
            }
            LocalComputeBackend.AUTO -> {
                val gpuResult = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.GPU, config.contextSize, modelCacheDir)
                if (gpuResult.isSuccess) {
                    return EngineLoadOutcome(gpuResult.getOrThrow(), modelCacheDir, LocalComputeBackend.AUTO, LocalComputeBackend.GPU, null)
                }

                val fallbackMsg = "GPU engine initialization failed (${gpuResult.exceptionOrNull()?.message}); fell back to CPU"
                Log.w(TAG, fallbackMsg)

                val cpuEngine = createAndInitializeEngine(file.absolutePath, LocalComputeBackend.CPU, config.contextSize, modelCacheDir).getOrThrow()
                return EngineLoadOutcome(cpuEngine, modelCacheDir, LocalComputeBackend.AUTO, LocalComputeBackend.CPU, fallbackMsg)
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
                activeCachePath = outcome.cacheDir.absolutePath
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
                    toolCalls = nativeToolCalls.values.toList(),
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
    }
}
