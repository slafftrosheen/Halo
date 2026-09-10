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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.nehuatl.llamacpp.LlamaHelper

class NativeLlamaProvider(
    private val context: Context,
    private val downloadManager: ModelDownloadManager,
    private val storage: Storage
) : AiInferenceProvider {
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
    } catch (e: Exception) {
        Log.e("NativeLlamaProvider", "testConnection error", e)
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
            Log.i("NativeLlamaProvider", "Loading GGUF model: ${file.absolutePath} (${file.length()} bytes)")
            val helper = LlamaHelper(context.contentResolver, scope, llamaEventFlow)
            helper.load(file.absolutePath, contextSize, systemPrompt, {})
            engine = helper
            loadedModelId = activeId
            _status.value = ProviderStatus.READY
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
        runCatching { engine?.release() }
        engine = null
        loadedModelId = null
    }

    override suspend fun health(): Boolean =
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
            try {
                val text = buildString { streamInternal(prompt).collect { append(it) } }.trim()
                if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "llama.cpp returned an empty response")
                AiResponse(text = text, providerId = id, modelId = modelId, finishReason = "stop",
                    metrics = AiGenerationMetrics(startedAtMs = startedAt, completedAtMs = System.currentTimeMillis()))
            } catch (error: CancellationException) {
                throw error
            } catch (error: AiProviderException) {
                throw error
            } catch (error: Throwable) {
                throw AiProviderException(AiErrorType.GENERATION_FAILED, "Native llama.cpp generation failed", error)
            }
        }
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        request.validateForTextGeneration()
        emit(AiStreamEvent.Started)
        val startedAt = System.currentTimeMillis()
        val output = StringBuilder()
        try {
            val modelId = requireSelectedModel(request)
            if (!loadModelIfNeeded()) throw AiProviderException(AiErrorType.MODEL_LOAD_FAILED, status.value.message ?: "Unable to load llama.cpp model")
            inferenceMutex.withLock {
                streamInternal(buildPrompt(request, context)).collect { token ->
                    output.append(token)
                    emit(AiStreamEvent.Token(token))
                }
            }
            val text = output.toString().trim()
            if (text.isBlank()) throw AiProviderException(AiErrorType.EMPTY_RESPONSE, "llama.cpp produced no response text")
            emit(AiStreamEvent.Completed(AiResponse(text = text, providerId = id, modelId = modelId, finishReason = "stop",
                metrics = AiGenerationMetrics(startedAtMs = startedAt, completedAtMs = System.currentTimeMillis()))))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(AiStreamEvent.Failed(error))
        }
    }

    override suspend fun complete(request: AIRequestLegacy): AIResponseLegacy = inferenceMutex.withLock {
        val startTime = System.currentTimeMillis()
        try {
            if (!loadModelIfNeeded()) {
                throw AiProviderException(AiErrorType.MODEL_LOAD_FAILED, status.value.message ?: "Unable to load local model")
            }
            val result = buildString {
                streamInternal(request.conversation.messages.joinToString("\n") { "${it.role.name.lowercase()}: ${it.content}" }).collect { append(it) }
            }
            AIResponseLegacy(result, "stop", System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is AiProviderException) throw e
            throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Native Llama completion failed", e)
        }
    }

    override fun stream(prompt: String): Flow<String> = flow {
        inferenceMutex.withLock { streamInternal(prompt).collect { emit(it) } }
    }

    private fun streamInternal(prompt: String): Flow<String> = flow {
        val currentEngine = engine ?: throw AiProviderException(AiErrorType.MODEL_NOT_READY, "Native llama.cpp engine is not loaded")
        try {
            currentEngine.predict(prompt, "", false)
            llamaEventFlow.takeWhile { event ->
                currentCoroutineContext().ensureActive()
                event !is LlamaHelper.LLMEvent.Done && event !is LlamaHelper.LLMEvent.Error
            }.collect { event ->
                currentCoroutineContext().ensureActive()
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> if (event.word.isNotEmpty()) emit(event.word)
                    is LlamaHelper.LLMEvent.Error -> throw AiProviderException(AiErrorType.GENERATION_FAILED, "llama.cpp generation error")
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
        val downloaded = downloadManager.getAllModels().filter { downloadManager.getModelFile(it.id) != null }
        val activeId = downloadManager.activeModelId.value
        return downloaded.sortedWith(compareByDescending<CognitiveModel> { it.id == activeId }.thenBy { it.name }).map { model ->
            AiModel(id = model.id, displayName = model.name, providerId = id,
                inputCapabilities = setOf(AiCapability.TEXT), contextLength = 4096,
                supportsStreaming = true, supportsTools = false)
        }
    }

    override suspend fun close() {
        inferenceMutex.withLock { releaseCurrentModel() }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
