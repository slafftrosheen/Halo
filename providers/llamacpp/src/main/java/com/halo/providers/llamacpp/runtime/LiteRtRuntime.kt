package com.halo.providers.llamacpp.runtime

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import com.halo.sdk.provider.AIMessage
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalInferenceRuntime
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.provider.LocalModelSessionState
import com.halo.sdk.provider.Role
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.sdk.provider.RuntimeGenerationResult
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.provider.RuntimeLoadResult
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

/**
 * Google LiteRT-LM runtime used by Halo's Native Local provider.
 *
 * The former implementation was deliberately a stub, which left the Android
 * product dependent on the third-party llama.cpp wrapper even though the SDK
 * already modelled LITERT_LM as a first-class local format.
 *
 * Lifecycle rules mirror Halo's native-session contract and the proven OneBox
 * integration:
 * - one resident Engine at a time;
 * - expensive initialize/close work runs off Main;
 * - inference is serialized, while cancel can interrupt the active Conversation;
 * - AUTO prefers GPU but falls back to CPU;
 * - compilation/inference cache lives under filesDir so Android cache trimming
 *   does not force a full rebuild on every cold start.
 */
class LiteRtRuntime(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val scope: CoroutineScope,
) : LocalInferenceRuntime {

    override val format: LocalModelFormat = LocalModelFormat.LITERT_LM

    private val _state = MutableStateFlow(LocalModelSessionState.IDLE)
    override val state: StateFlow<LocalModelSessionState> = _state.asStateFlow()

    @Volatile
    private var _loadedModelId: String? = null
    override val loadedModelId: String? get() = _loadedModelId

    private val stateMutex = Mutex()
    private val inferenceMutex = Mutex()
    private var engine: Engine? = null
    private var activeBackend: LocalComputeBackend? = null
    private var activeConversation: Conversation? = null

    override fun isLoaded(): Boolean =
        engine?.isInitialized() == true && _state.value != LocalModelSessionState.UNLOADING

    override suspend fun loadModel(
        modelId: String,
        modelPath: String,
        config: RuntimeLoadConfig,
    ): Result<RuntimeLoadResult> = inferenceMutex.withLock {
        val file = File(modelPath)
        if (!file.isFile || file.length() <= 0L) {
            _state.value = LocalModelSessionState.ERROR
            return@withLock Result.failure(
                IllegalArgumentException("LiteRT-LM model file does not exist or is empty: $modelPath")
            )
        }

        val cached = stateMutex.withLock {
            engine?.takeIf { _loadedModelId == modelId && it.isInitialized() }
        }
        if (cached != null) {
            return@withLock Result.success(
                RuntimeLoadResult(
                    activeBackend = activeBackend ?: LocalComputeBackend.CPU,
                    activeGpuLayers = 0,
                    effectiveContextSize = config.contextSize,
                )
            )
        }

        _state.value = LocalModelSessionState.LOADING

        val staleEngine = stateMutex.withLock {
            val old = engine
            engine = null
            _loadedModelId = null
            activeBackend = null
            old
        }
        staleEngine?.let { closeEngine(it) }

        try {
            val candidates = when (config.computeBackend) {
                LocalComputeBackend.CPU -> listOf(LocalComputeBackend.CPU)
                LocalComputeBackend.GPU -> listOf(LocalComputeBackend.GPU, LocalComputeBackend.CPU)
                LocalComputeBackend.AUTO -> listOf(LocalComputeBackend.GPU, LocalComputeBackend.CPU)
            }

            var created: Engine? = null
            var selectedBackend: LocalComputeBackend? = null
            var lastError: Throwable? = null

            for (candidateBackend in candidates) {
                try {
                    created = createEngine(file, config, candidateBackend)
                    if (created != null) {
                        selectedBackend = candidateBackend
                        break
                    }
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    lastError = t
                    Log.w(TAG, "LiteRT-LM init failed on $candidateBackend for $modelId", t)
                }
            }

            val loaded = created ?: throw IllegalStateException(
                "LiteRT-LM failed to initialize model on GPU and CPU",
                lastError,
            )
            val backend = selectedBackend ?: LocalComputeBackend.CPU

            stateMutex.withLock {
                engine = loaded
                _loadedModelId = modelId
                activeBackend = backend
            }
            _state.value = LocalModelSessionState.IDLE

            Result.success(
                RuntimeLoadResult(
                    activeBackend = backend,
                    activeGpuLayers = 0,
                    effectiveContextSize = config.contextSize,
                    fallbackReason = if (
                        backend == LocalComputeBackend.CPU &&
                        config.computeBackend != LocalComputeBackend.CPU
                    ) {
                        "LiteRT-LM GPU initialization was unavailable; using CPU"
                    } else {
                        null
                    },
                )
            )
        } catch (c: CancellationException) {
            _state.value = LocalModelSessionState.IDLE
            throw c
        } catch (t: Throwable) {
            _state.value = LocalModelSessionState.ERROR
            Result.failure(t)
        }
    }

    override suspend fun generate(
        config: RuntimeGenerationConfig,
        onToken: suspend (String) -> Unit,
    ): Result<RuntimeGenerationResult> = inferenceMutex.withLock {
        val currentEngine = stateMutex.withLock {
            engine?.takeIf { it.isInitialized() }
        } ?: return@withLock Result.failure(IllegalStateException("No LiteRT-LM model is loaded"))

        _state.value = LocalModelSessionState.INFERRING
        val startedAt = System.currentTimeMillis()
        var firstTokenAt: Long? = null
        var outputChunks = 0
        val output = StringBuilder()

        val messages = config.messages
        val systemText = messages
            .filter { it.role == Role.SYSTEM }
            .joinToString("\n") { it.content }
            .trim()
        val nonSystem = messages.filterNot { it.role == Role.SYSTEM }
        val outgoingSource = nonSystem.lastOrNull()
        val outgoingText = outgoingSource?.content?.takeIf { it.isNotBlank() } ?: config.prompt
        val initialMessages = nonSystem.dropLast(if (outgoingSource != null) 1 else 0)
            .map(::toLiteRtMessage)

        val conversationConfig = ConversationConfig(
            systemInstruction = systemText.takeIf { it.isNotBlank() }?.let(Contents::of),
            initialMessages = initialMessages,
            samplerConfig = SamplerConfig(
                topK = config.topK,
                topP = config.topP.coerceIn(0.0, 1.0),
                temperature = config.temperature.coerceAtLeast(0.0),
            ),
            automaticToolCalling = false,
            maxOutputToken = config.maxTokens.takeIf { it > 0 },
        )

        val conversation = try {
            withContext(Dispatchers.Default) {
                currentEngine.createConversation(conversationConfig)
            }
        } catch (c: CancellationException) {
            _state.value = LocalModelSessionState.IDLE
            throw c
        } catch (t: Throwable) {
            _state.value = LocalModelSessionState.ERROR
            return@withLock Result.failure(t)
        }

        stateMutex.withLock { activeConversation = conversation }

        try {
            val outgoing = if (config.imageBytes != null) {
                Message.user(
                    Contents.of(
                        Content.ImageBytes(config.imageBytes),
                        Content.Text(outgoingText),
                    )
                )
            } else {
                Message.user(outgoingText)
            }

            val response = withContext(Dispatchers.Default) {
                conversation.sendMessage(
                    outgoing,
                    maxOutputToken = config.maxTokens.takeIf { it > 0 },
                )
            }
            val generated = response.contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
            if (generated.isNotEmpty()) {
                firstTokenAt = System.currentTimeMillis()
                outputChunks = 1
                output.append(generated)
                onToken(generated)
            }

            val completedAt = System.currentTimeMillis()
            val duration = (completedAt - startedAt).coerceAtLeast(1L)
            _state.value = LocalModelSessionState.IDLE
            Result.success(
                RuntimeGenerationResult(
                    text = output.toString(),
                    tokenCount = outputChunks,
                    timeToFirstTokenMs = firstTokenAt?.minus(startedAt) ?: duration,
                    tokensPerSecond = outputChunks.toDouble() / (duration.toDouble() / 1000.0),
                    durationMs = duration,
                )
            )
        } catch (c: CancellationException) {
            withContext(NonCancellable) {
                runCatching { conversation.cancelProcess() }
            }
            _state.value = LocalModelSessionState.IDLE
            throw c
        } catch (t: Throwable) {
            _state.value = LocalModelSessionState.ERROR
            Result.failure(t)
        } finally {
            withContext(NonCancellable + Dispatchers.Default) {
                stateMutex.withLock {
                    if (activeConversation === conversation) activeConversation = null
                }
                runCatching { conversation.close() }
            }
        }
    }

    override suspend fun cancelGeneration() {
        val conversation = stateMutex.withLock { activeConversation }
        conversation?.let { runCatching { it.cancelProcess() } }
    }

    override suspend fun unloadModel(): Result<Unit> {
        cancelGeneration()
        return inferenceMutex.withLock {
            _state.value = LocalModelSessionState.UNLOADING
            val staleEngine = stateMutex.withLock {
                activeConversation = null
                val old = engine
                engine = null
                _loadedModelId = null
                activeBackend = null
                old
            }
            staleEngine?.let { closeEngine(it) }
            _state.value = LocalModelSessionState.IDLE
            Result.success(Unit)
        }
    }

    private suspend fun createEngine(
        file: File,
        config: RuntimeLoadConfig,
        backendKind: LocalComputeBackend,
    ): Engine? = withContext(Dispatchers.Default) {
        val backend = when (backendKind) {
            LocalComputeBackend.GPU -> Backend.GPU()
            LocalComputeBackend.CPU, LocalComputeBackend.AUTO -> Backend.CPU()
        }
        val candidate = Engine(
            EngineConfig(
                modelPath = file.absolutePath,
                backend = backend,
                visionBackend = if (config.visionEnabled) backend else null,
                maxNumTokens = config.contextSize.coerceAtLeast(MIN_CONTEXT_TOKENS),
                maxNumImages = if (config.visionEnabled) 1 else null,
                cacheDir = inferenceCacheDir().absolutePath,
            )
        )
        try {
            candidate.initialize()
            Log.i(TAG, "LiteRT-LM engine initialized backend=$backendKind model=${file.name}")
            candidate
        } catch (c: CancellationException) {
            runCatching { candidate.close() }
            throw c
        } catch (t: Throwable) {
            runCatching { candidate.close() }
            Log.w(TAG, "LiteRT-LM engine initialization failed backend=$backendKind model=${file.name}", t)
            throw t
        }
    }

    private fun toLiteRtMessage(message: AIMessage): Message = when (message.role) {
        Role.ASSISTANT -> Message.model(message.content)
        Role.USER -> Message.user(message.content)
        Role.TOOL -> Message.user("Tool result: ${message.content}")
        // System messages are removed into ConversationConfig.systemInstruction above.
        // Keep this branch defensive without relying on a separate Message.system factory.
        Role.SYSTEM -> Message.user(message.content)
    }

    private fun inferenceCacheDir(): File =
        File(context.filesDir, CACHE_DIRECTORY).apply { mkdirs() }

    private suspend fun closeEngine(target: Engine) {
        withContext(NonCancellable + Dispatchers.Default) {
            if (target.isInitialized()) runCatching { target.close() }
        }
    }

    private companion object {
        const val TAG = "LiteRtRuntime"
        const val CACHE_DIRECTORY = "litertlm_cache"
        const val MIN_CONTEXT_TOKENS = 512
    }
}
