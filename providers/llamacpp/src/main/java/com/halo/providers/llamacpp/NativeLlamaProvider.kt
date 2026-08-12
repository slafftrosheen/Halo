package com.halo.providers.llamacpp

import android.content.Context

import org.nehuatl.llamacpp.LlamaHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import android.util.Log

import com.halo.sdk.provider.InferenceProvider
import com.halo.sdk.provider.AIRequestLegacy
import com.halo.sdk.provider.AIResponseLegacy
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NativeLlamaProvider(
    private val context: Context,
    private val downloadManager: ModelDownloadManager,
    private val systemPrompt: String = "You are Halo, a helpful AI.",
    private val contextSize: Int = 4096
) : InferenceProvider {
    private val TAG = "NativeLlamaProvider"
    private var engine: LlamaHelper? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val llamaEventFlow = MutableSharedFlow<LlamaHelper.LLMEvent>()
    private var loadedModelId: String? = null
    private val inferenceMutex = Mutex()

    override val metadata = com.halo.sdk.provider.ProviderMetadata(
        id = "provider.llamacpp.native",
        displayName = "llama.cpp (native)",
        local = true,
        supportsStreaming = true
    )

    init {
        // Initialize the library        // Removed LlamaLog for now
    }

    suspend fun loadModelIfNeeded(): Boolean {
        val activeId = downloadManager.activeModelId.value
        if (activeId == null) {
            Log.w(TAG, "No active model set.")
            return false
        }

        if (loadedModelId == activeId && engine != null) {
            return true // Already loaded
        }

        val file = downloadManager.getModelFile(activeId)
        if (file == null || !file.exists()) {
            Log.e(TAG, "Model file for $activeId not found.")
            return false
        }
        // Unload old engine if any
        engine?.release()
        engine = null

        return try {
            val helper = LlamaHelper(context.contentResolver, scope, llamaEventFlow)
            helper.load(file.absolutePath, contextSize, systemPrompt, {})
            engine = helper
            loadedModelId = activeId
            Log.i(TAG, "Successfully loaded $activeId via LlamaHelper")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            false
        }
    }

    override suspend fun health(): Boolean {
        return downloadManager.activeModelId.value != null && loadModelIfNeeded()
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AIResponseLegacy("", "error", System.currentTimeMillis() - startTime)
            }
        }
    }

    override fun stream(prompt: String): Flow<String> = flow {
        inferenceMutex.withLock {
            streamInternal(prompt).collect { emit(it) }
        }
    }

    private fun streamInternal(prompt: String): Flow<String> = flow {
        if (!loadModelIfNeeded()) {
            throw IllegalStateException("Engine not loaded or no active model")
        }

        val currentEngine = engine ?: return@flow
        
        try {
            val formattedPrompt = "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
            currentEngine.predict(formattedPrompt, "", false)
            
            // Collect the shared flow for Ongoing events
            llamaEventFlow.takeWhile { event ->
                currentCoroutineContext().ensureActive()
                event !is LlamaHelper.LLMEvent.Done && event !is LlamaHelper.LLMEvent.Error
            }.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> emit(event.word)
                    else -> {} // ignore Started/Loaded
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Inference error", e)
            throw e
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
