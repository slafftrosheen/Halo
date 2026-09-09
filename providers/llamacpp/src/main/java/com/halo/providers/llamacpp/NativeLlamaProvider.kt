package com.halo.providers.llamacpp

import android.content.Context
import android.util.Log
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nehuatl.llamacpp.LlamaHelper

class NativeLlamaProvider(
    private val context: Context,
    private val downloadManager: ModelDownloadManager,
    private val storage: Storage
) : InferenceProvider {
    private var engine: LlamaHelper? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val llamaEventFlow = MutableSharedFlow<LlamaHelper.LLMEvent>()
    private var loadedModelId: String? = null
    
    private val modelLoadMutex = Mutex()
    private val inferenceMutex = Mutex()

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val systemPrompt: String get() = storage.getString("ai_system_prompt") ?: "You are Halo, a helpful AI."
    private val contextSize: Int get() = storage.getInt("llamacpp_context", 4096)

    override val metadata = ProviderMetadata(
        id = "provider.llamacpp.native",
        displayName = "llama.cpp (native)",
        local = true,
        supportsStreaming = true
    )

    override suspend fun getStatus(): ProviderStatus {
        val activeId = downloadManager.activeModelId.value
        val current = if (activeId == null) {
            ProviderStatus.UNCONFIGURED
        } else if (engine != null && loadedModelId == activeId) {
            ProviderStatus.READY
        } else {
            val file = downloadManager.getModelFile(activeId)
            if (file != null && file.exists() && file.length() > 0) {
                ProviderStatus(ProviderStatus.State.STANDBY, "Model downloaded (${file.length() / 1024 / 1024} MB), ready to load")
            } else {
                ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model not downloaded")
            }
        }
        _status.value = current
        return current
    }

    override suspend fun testConnection(): Boolean {
        return try {
            val loaded = loadModelIfNeeded()
            Log.d("NativeLlamaProvider", "testConnection loadModelIfNeeded result=$loaded")
            loaded
        } catch (e: Exception) {
            Log.e("NativeLlamaProvider", "testConnection error", e)
            false
        }
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
        if (file == null) {
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Model file not found: $activeId")
            return@withLock false
        }

        if (!file.exists()) {
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Model file does not exist: ${file.absolutePath}")
            return@withLock false
        }

        if (!file.isFile) {
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Model path is not a file: ${file.absolutePath}")
            return@withLock false
        }

        if (file.length() <= 0L) {
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Model file is empty: ${file.absolutePath}")
            return@withLock false
        }

        _status.value = ProviderStatus(ProviderStatus.State.LOADING, "Loading $activeId...")
        releaseCurrentModel()

        return@withLock try {
            Log.i("NativeLlamaProvider", "Loading GGUF model: ${file.absolutePath} (${file.length()} bytes)")
            val helper = LlamaHelper(context.contentResolver, scope, llamaEventFlow)
            helper.load(file.absolutePath, contextSize, systemPrompt, {})
            engine = helper
            loadedModelId = activeId
            _status.value = ProviderStatus.READY
            Log.i("NativeLlamaProvider", "Successfully loaded GGUF model: $activeId")
            true
        } catch (error: CancellationException) {
            releaseCurrentModel()
            _status.value = ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Model loading cancelled")
            throw error
        } catch (error: Throwable) {
            releaseCurrentModel()
            Log.e("NativeLlamaProvider", "Failed loading model ${file.absolutePath}", error)
            _status.value = ProviderStatus(ProviderStatus.State.ERROR, "Model load failed: ${error.message ?: error::class.simpleName}")
            false
        }
    }

    private fun releaseCurrentModel() {
        runCatching {
            engine?.release()
        }
        engine = null
        loadedModelId = null
    }

    override suspend fun health(): Boolean {
        val activeId = downloadManager.activeModelId.value ?: return false
        val file = downloadManager.getModelFile(activeId)
        return file != null && file.exists() && file.length() > 0
    }

    private fun buildPrompt(
        request: AiRequest,
        context: AiContext
    ): String {
        val system = systemPrompt
        val history = context.conversation.joinToString("\n") {
            "${it.role.name.lowercase()}: ${it.content}"
        }
        return buildString {
            append("<|im_start|>system\n")
            append(system)
            append("<|im_end|>\n")
            if (history.isNotBlank()) {
                append(history)
                append("\n")
            }
            append("<|im_start|>user\n")
            append(request.text!!.trim())
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }
    }

    override suspend fun generate(
        request: AiRequest,
        context: AiContext
    ): AiResponse {
        request.validateForTextGeneration()

        val modelId = request.modelId
            ?: downloadManager.activeModelId.value
            ?: throw AiProviderException(
                AiErrorType.MODEL_NOT_SELECTED,
                "No local llama.cpp model is selected"
            )

        if (loadedModelId != modelId || engine == null) {
            val loaded = loadModelIfNeeded()
            if (!loaded) {
                throw AiProviderException(
                    AiErrorType.MODEL_LOAD_FAILED,
                    status.value.message ?: "Unable to load local model"
                )
            }
        }

        val startedAt = System.currentTimeMillis()
        val prompt = buildPrompt(request, context)

        return inferenceMutex.withLock {
            try {
                val text = buildString {
                    streamInternal(prompt).collect { token ->
                        append(token)
                    }
                }.trim()

                if (text.isBlank()) {
                    throw AiProviderException(
                        AiErrorType.EMPTY_RESPONSE,
                        "llama.cpp returned an empty response"
                    )
                }

                val completedAt = System.currentTimeMillis()

                AiResponse(
                    text = text,
                    providerId = id,
                    modelId = modelId,
                    finishReason = "stop",
                    metrics = AiGenerationMetrics(
                        startedAtMs = startedAt,
                        completedAtMs = completedAt
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: AiProviderException) {
                throw error
            } catch (error: Throwable) {
                Log.e("NativeLlamaProvider", "Generation failed", error)
                throw AiProviderException(
                    AiErrorType.GENERATION_FAILED,
                    "Native llama.cpp generation failed",
                    error
                )
            }
        }
    }

    override fun stream(
        request: AiRequest,
        context: AiContext
    ): Flow<AiStreamEvent> = flow {
        request.validateForTextGeneration()

        emit(AiStreamEvent.Started)

        val startedAt = System.currentTimeMillis()
        var firstTokenAt: Long? = null
        val output = StringBuilder()

        try {
            val modelId = request.modelId
                ?: downloadManager.activeModelId.value
                ?: throw AiProviderException(
                    AiErrorType.MODEL_NOT_SELECTED,
                    "No llama.cpp model selected"
                )

            if (loadedModelId != modelId || engine == null) {
                if (!loadModelIfNeeded()) {
                    throw AiProviderException(
                        AiErrorType.MODEL_LOAD_FAILED,
                        status.value.message ?: "Unable to load llama.cpp model"
                    )
                }
            }

            val prompt = buildPrompt(request, context)

            inferenceMutex.withLock {
                streamInternal(prompt).collect { token ->
                    if (firstTokenAt == null) {
                        firstTokenAt = System.currentTimeMillis()
                    }
                    output.append(token)
                    emit(AiStreamEvent.Token(token))
                }
            }

            val text = output.toString().trim()

            if (text.isBlank()) {
                throw AiProviderException(
                    AiErrorType.EMPTY_RESPONSE,
                    "llama.cpp produced no response text"
                )
            }

            val response = AiResponse(
                text = text,
                providerId = id,
                modelId = modelId,
                finishReason = "stop",
                metrics = AiGenerationMetrics(
                    startedAtMs = startedAt,
                    firstTokenAtMs = firstTokenAt,
                    completedAtMs = System.currentTimeMillis()
                )
            )

            emit(AiStreamEvent.Completed(response))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(AiStreamEvent.Failed(error))
        }
    }

    override suspend fun complete(request: AIRequestLegacy): AIResponseLegacy {
        return inferenceMutex.withLock {
            val startTime = System.currentTimeMillis()
            val prompt = request.conversation.messages.joinToString("\n") {
                "${it.role.name.lowercase()}: ${it.content}"
            }
            try {
                val result = buildString {
                    streamInternal(prompt).collect { append(it) }
                }
                AIResponseLegacy(result, "stop", System.currentTimeMillis() - startTime)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is AiProviderException) throw e
                throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Native Llama completion failed", e)
            }
        }
    }

    override fun stream(prompt: String): Flow<String> = flow {
        inferenceMutex.withLock {
            streamInternal(prompt).collect { emit(it) }
        }
    }

    private fun streamInternal(prompt: String): Flow<String> = flow {
        val currentEngine = engine ?: throw AiProviderException(
            AiErrorType.MODEL_NOT_READY,
            "Native llama.cpp engine is not loaded"
        )

        try {
            currentEngine.predict(prompt, "", false)
            
            llamaEventFlow.takeWhile { event ->
                currentCoroutineContext().ensureActive()
                event !is LlamaHelper.LLMEvent.Done && event !is LlamaHelper.LLMEvent.Error
            }.collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        if (event.word.isNotEmpty()) {
                            emit(event.word)
                        }
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        throw AiProviderException(
                            AiErrorType.GENERATION_FAILED,
                            "llama.cpp generation error"
                        )
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is AiProviderException) throw e
            throw AiProviderException(AiErrorType.GENERATION_FAILED, "Native llama.cpp streaming failed", e)
        }
    }

    override suspend fun listModels(): List<AiModel> {
        return downloadManager.getAllModels().map { model ->
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

    override suspend fun close() {
        inferenceMutex.withLock {
            releaseCurrentModel()
        }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
