package com.halo.providers.llamacpp

import android.content.Context
import android.util.Log
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
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
        val current = if (health()) ProviderStatus.READY else ProviderStatus.UNAVAILABLE
        _status.value = current
        return current
    }

    suspend fun loadModelIfNeeded(): Boolean {
        val activeId = downloadManager.activeModelId.value
        if (activeId == null) return false

        if (loadedModelId == activeId && engine != null) return true

        val file = downloadManager.getModelFile(activeId)
        if (file == null || !file.exists()) return false
        
        engine?.release()
        engine = null

        return try {
            val helper = LlamaHelper(context.contentResolver, scope, llamaEventFlow)
            helper.load(file.absolutePath, contextSize, systemPrompt, {})
            engine = helper
            loadedModelId = activeId
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun health(): Boolean {
        return downloadManager.activeModelId.value != null && loadModelIfNeeded()
    }

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        val conversation = AIConversation(
            id = "legacy",
            messages = context.conversation.toMutableList()
        )
        val legacyRequest = AIRequestLegacy(conversation)
        val legacyResponse = complete(legacyRequest)
        return AiResponse(text = legacyResponse.text, providerId = id)
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
        if (!loadModelIfNeeded()) throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Native engine not loaded")

        val currentEngine = engine ?: return@flow
        try {
            val formattedPrompt = "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
            currentEngine.predict(formattedPrompt, "", false)
            
            llamaEventFlow.takeWhile { event ->
                currentCoroutineContext().ensureActive()
                event !is LlamaHelper.LLMEvent.Done && event !is LlamaHelper.LLMEvent.Error
            }.collect { event ->
                if (event is LlamaHelper.LLMEvent.Ongoing) emit(event.word)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is AiProviderException) throw e
            throw AiProviderException(AiErrorType.LOCAL_MODEL_UNAVAILABLE, "Native Llama streaming failed", e)
        }
    }

    override suspend fun listModels(): List<AiModel> {
        return AvailableModels.map { model ->
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
            engine?.release()
            engine = null
            loadedModelId = null
        }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
