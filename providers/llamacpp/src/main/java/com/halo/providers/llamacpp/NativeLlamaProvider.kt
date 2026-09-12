package com.halo.providers.llamacpp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaHelper
import java.util.UUID

class NativeLlamaProvider(
    private val context: Context,
    private val downloadManager: ModelDownloadManager,
    private val storage: Storage
) : AiInferenceProvider {
    private var engine: LlamaHelper? = null
    private var engineEvents: MutableSharedFlow<LlamaHelper.LLMEvent>? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var loadedModelId: String? = null
    private val modelLoadMutex = Mutex()
    private val inferenceMutex = Mutex()

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val systemPrompt: String get() = storage.getString("ai_system_prompt") ?: "You are Halo, a helpful AI."
    private val contextSize: Int get() = storage.getInt("llamacpp_context", 4096)

    override val id: String = "provider.llamacpp.native"
    override val displayName: String = "llama.cpp (native)"
    override val isLocal: Boolean = true

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING)

    override suspend fun getStatus(): ProviderStatus {
        val activeId = downloadManager.activeModelId.value
        val current = when {
            activeId == null -> ProviderStatus.UNCONFIGURED
            engine != null && loadedModelId == activeId -> ProviderStatus.READY
            downloadManager.getModelFile(activeId) != null ->
                ProviderStatus(ProviderStatus.State.READY, "Model downloaded; ready to load: $activeId")
            else -> ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model not downloaded: $activeId")
        }
        _status.value = current
        return current
    }

    override suspend fun testConnection(): Boolean = try {
        loadModelIfNeeded()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "testConnection error", e)
        false
    }

    suspend fun loadModelIfNeeded(): Boolean = modelLoadMutex.withLock {
        val activeId = downloadManager.activeModelId.value
        if (activeId == null) {
            _status.value = ProviderStatus.UNCONFIGURED
            return@withLock false
        }
        if (loadedModelId == activeId && engine != null) {
            _status.value = ProviderStatus.READY
            return@withLock true
        }

        val file = downloadManager.getModelFile(activeId)
        if (file == null || !file.exists() || !file.isFile || file.length() <= 0L) {
            _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model file not found: $activeId")
            return@withLock false
        }

        _status.value = ProviderStatus(ProviderStatus.State.LOADING, "Loading $activeId...")
        releaseCurrentModel()
        return@withLock try {
            val modelUri = Uri.fromFile(file).toString()
            Log.i(TAG, "Loading GGUF model: uri=$modelUri scheme=${Uri.parse(modelUri).scheme} (${file.length()} bytes)")

            val events = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 64)
            val loadResult = CompletableDeferred<String?>()
            val loadCollector = scope.launch {
                events.collect { event ->
                    when (event) {
                        is LlamaHelper.LLMEvent.Error -> {
                            Log.e(TAG, "Native llama.cpp model load error: ${event.message}")
                            loadResult.complete(event.message ?: "Native llama.cpp model initialization failed")
                        }
                        is LlamaHelper.LLMEvent.Loaded -> {
                            Log.i(TAG, "Native llama.cpp model load event received: ${event.path}")
                        }
                        else -> Unit
                    }
                }
            }

            var helper: LlamaHelper? = null
            var installed = false
            try {
                helper = LlamaHelper(context.contentResolver, scope, events)
                helper.load(modelUri, contextSize, null) { contextId ->
                    Log.i(TAG, "Native llama.cpp model ready contextId=$contextId model=$activeId")
                    loadResult.complete(null)
                }

                val loadOutcome = withTimeoutOrNull(MODEL_LOAD_TIMEOUT_MS) { loadResult.await() }
                if (loadOutcome == null && !loadResult.isCompleted) {
                    Log.e(TAG, "Native llama.cpp model load timed out after ${MODEL_LOAD_TIMEOUT_MS}ms model=$activeId")
                    _status.value = ProviderStatus(
                        ProviderStatus.State.ERROR,
                        "Native llama.cpp model load timed out after ${MODEL_LOAD_TIMEOUT_MS / 1000}s"
                    )
                    false
                } else if (loadOutcome != null) {
                    _status.value = ProviderStatus(
                        ProviderStatus.State.ERROR,
                        "Native llama.cpp model initialization failed: $loadOutcome"
                    )
                    false
                } else if (downloadManager.activeModelId.value != activeId) {
                    Log.w(TAG, "Discarding stale llama.cpp engine: requested=$activeId active=${downloadManager.activeModelId.value}")
                    _status.value = ProviderStatus(
                        ProviderStatus.State.UNAVAILABLE,
                        "Model selection changed while loading"
                    )
                    false
                } else {
                    engine = helper
                    engineEvents = events
                    loadedModelId = activeId
                    installed = true
                    _status.value = ProviderStatus.READY
                    true
                }
            } finally {
                loadCollector.cancel()
                if (!installed) {
                    runCatching { helper?.release() }
                }
            }
        } catch (error: CancellationException) {
            releaseCurrentModel()
            _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model loading cancelled")
            throw error
        } catch (error: Throwable) {
            releaseCurrentModel()
            Log.e(TAG, "Failed loading model ${file.absolutePath}", error)
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Model load failed: ${error.message ?: error::class.simpleName}")
            false
        }
    }

    private fun releaseCurrentModel() {
        runCatching { engine?.release() }
        engine = null
        engineEvents = null
        loadedModelId = null
    }

    suspend fun health(): Boolean =
        downloadManager.activeModelId.value?.let { downloadManager.getModelFile(it) }?.let { it.exists() && it.length() > 0 } == true

    private fun buildPrompt(request: AiRequest, context: AiContext): String {
        val history = context.conversation.joinToString("\n") { "${it.role.name.lowercase()}: ${it.content}" }
        return buildString {
            append("<|im_start|>system\n")
            append(systemPrompt)
            append("<|im_end|>\n")
            if (history.isNotBlank()) append(history).append("\n")
            append("<|im_start|>user\n")
            append(request.text!!.trim())
            append("<|im_end|>\n<|im_start|>assistant\n")
        }
    }

    private suspend fun requireSelectedModel(request: AiRequest): String {
        val activeModelId = downloadManager.activeModelId.value
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "No local llama.cpp model is selected")
        if (request.modelId != null && request.modelId != activeModelId) {
            throw AiProviderException(
                AiErrorType.MODEL_NOT_FOUND,
                "Selected llama.cpp model '${request.modelId}' is not the active local model '$activeModelId'"
            )
        }
        return activeModelId
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        request.validateForTextGeneration()
        val modelId = requireSelectedModel(request)
        if (!loadModelIfNeeded()) {
            throw AiProviderException(AiErrorType.MODEL_LOAD_FAILED, status.value.message ?: "Unable to load local model")
        }
        val startedAt = System.currentTimeMillis()
        val prompt = buildPrompt(request, context)
        return inferenceMutex.withLock {
            val transactionId = UUID.randomUUID().toString().take(8)
            try {
                Log.i(TAG, "[$transactionId] generate start model=$modelId promptChars=${prompt.length} contextSize=$contextSize")
                val text = buildString { streamInternal(prompt, transactionId).collect { append(it) } }.trim()
                if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "llama.cpp returned an empty response")
                Log.i(TAG, "[$transactionId] generate complete outputChars=${text.length} durationMs=${System.currentTimeMillis() - startedAt}")
                AiResponse(
                    text = text,
                    providerId = id,
                    modelId = modelId,
                    finishReason = "stop",
                    metrics = AiGenerationMetrics(startedAtMs = startedAt, completedAtMs = System.currentTimeMillis())
                )
            } catch (error: CancellationException) {
                Log.i(TAG, "[$transactionId] generate cancelled")
                throw error
            } catch (error: AiProviderException) {
                Log.e(TAG, "[$transactionId] generate failed type=${error.type} message=${error.message}", error)
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "[$transactionId] generate failed unexpectedly", error)
                throw AiProviderException(AiErrorType.GENERATION_FAILED, "Native llama.cpp generation failed", error)
            }
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        request.validateForTextGeneration()
        emit(AiStreamEvent.Started)
        val startedAt = System.currentTimeMillis()
        val output = StringBuilder()
        val transactionId = UUID.randomUUID().toString().take(8)
        try {
            val modelId = requireSelectedModel(request)
            if (!loadModelIfNeeded()) {
                throw AiProviderException(AiErrorType.MODEL_LOAD_FAILED, status.value.message ?: "Unable to load llama.cpp model")
            }
            Log.i(TAG, "[$transactionId] stream start model=$modelId requestChars=${request.text?.length ?: 0}")
            inferenceMutex.withLock {
                streamInternal(buildPrompt(request, context), transactionId).collect { token ->
                    output.append(token)
                    emit(AiStreamEvent.Token(token))
                }
            }
            val text = output.toString().trim()
            if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "llama.cpp produced no response text")
            Log.i(TAG, "[$transactionId] stream complete outputChars=${text.length} durationMs=${System.currentTimeMillis() - startedAt}")
            emit(
                AiStreamEvent.Completed(
                    AiResponse(
                        text = text,
                        providerId = id,
                        modelId = modelId,
                        finishReason = "stop",
                        metrics = AiGenerationMetrics(startedAtMs = startedAt, completedAtMs = System.currentTimeMillis())
                    )
                )
            )
        } catch (error: CancellationException) {
            Log.i(TAG, "[$transactionId] stream cancelled")
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "[$transactionId] stream failed type=${error::class.qualifiedName} message=${error.message}", error)
            emit(AiStreamEvent.Failed(error))
        }
    }



    fun stream(prompt: String): Flow<String> = flow {
        val transactionId = UUID.randomUUID().toString().take(8)
        inferenceMutex.withLock { streamInternal(prompt, transactionId).collect { emit(it) } }
    }

    private fun streamInternal(prompt: String, transactionId: String): Flow<String> = kotlinx.coroutines.flow.channelFlow {
        val currentEngine = engine
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_READY, "Native llama.cpp engine is not loaded")
        val currentEvents = engineEvents
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_READY, "Native llama.cpp event stream is not ready")

        val eventChannel = Channel<LlamaHelper.LLMEvent>(Channel.UNLIMITED)
        var eventCount = 0
        var tokenCount = 0
        var terminalEvent: String? = null
        val collector = launch {
            currentEvents.collect { event ->
                eventChannel.send(event)
            }
        }

        try {
            currentEvents.subscriptionCount.first { it > 0 }
            currentCoroutineContext().ensureActive()
            Log.d(TAG, "[$transactionId] native event collector ready; predict begin promptChars=${prompt.length}")
            try {
                currentEngine.predict(prompt, "", true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "[$transactionId] native predict threw before terminal event", error)
                throw AiProviderException(
                    AiErrorType.GENERATION_FAILED,
                    "llama.cpp predict() failed: ${error.message ?: error::class.simpleName}",
                    error
                )
            }
            Log.d(TAG, "[$transactionId] predict returned; awaiting native terminal event")

            val completed = withTimeoutOrNull(GENERATION_TIMEOUT_MS) {
                for (event in eventChannel) {
                    currentCoroutineContext().ensureActive()
                    eventCount++
                    when (event) {
                        is LlamaHelper.LLMEvent.Ongoing -> {
                            if (event.word.isNotEmpty()) {
                                tokenCount++
                                send(event.word)
                            }
                        }
                        is LlamaHelper.LLMEvent.Done -> {
                            terminalEvent = "Done"
                            Log.i(TAG, "[$transactionId] native generation done events=$eventCount tokens=$tokenCount")
                            return@withTimeoutOrNull true
                        }
                        is LlamaHelper.LLMEvent.Error -> {
                            terminalEvent = "Error"
                            Log.e(
                                TAG,
                                "[$transactionId] native generation emitted Error event=${event::class.qualifiedName} " +
                                    "events=$eventCount tokens=$tokenCount message=${event.message}"
                            )
                            throw AiProviderException(
                                AiErrorType.GENERATION_FAILED,
                                "llama.cpp generation error: ${event.message ?: event::class.simpleName}"
                            )
                        }
                        else -> {
                            if (eventCount <= 8) {
                                Log.d(TAG, "[$transactionId] native event #$eventCount type=${event::class.qualifiedName}")
                            }
                        }
                    }
                }
                false
            } ?: false

            if (!completed) {
                throw AiProviderException(
                    AiErrorType.GENERATION_FAILED,
                    "llama.cpp generation timed out after ${GENERATION_TIMEOUT_MS / 1000}s without a terminal event"
                )
            }
        } finally {
            if (terminalEvent == null) {
                Log.w(TAG, "[$transactionId] native event stream ended without terminal event events=$eventCount tokens=$tokenCount")
            }
            collector.cancel()
            eventChannel.close()
        }
    }

    override suspend fun listModels(): List<AiModel> {
        val downloaded = downloadManager.getAllModels().filter { downloadManager.getModelFile(it.id) != null }
        val activeId = downloadManager.activeModelId.value
        return downloaded.sortedWith(compareByDescending<CognitiveModel> { it.id == activeId }.thenBy { it.name }).map { model ->
            AiModel(
                id = model.id,
                displayName = model.name,
                providerId = id,
                inputCapabilities = setOf(AiCapability.TEXT),
                contextLength = 4096,
                supportsStreaming = true,
                supportsTools = false
            )
        }
    }

    suspend fun close() {
        inferenceMutex.withLock { releaseCurrentModel() }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private companion object {
        private const val TAG = "NativeLlamaProvider"
        private const val MODEL_LOAD_TIMEOUT_MS = 120_000L
        private const val GENERATION_TIMEOUT_MS = 180_000L
    }
}
