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
import com.halo.sdk.provider.AIRequest
import com.halo.sdk.provider.AIResponse
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.map

class NativeLlamaProvider(
    private val context: Context,
    private val downloadManager: ModelDownloadManager
) : InferenceProvider {
    private val TAG = "NativeLlamaProvider"
    private var engine: LlamaHelper? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val llamaEventFlow = MutableSharedFlow<LlamaHelper.LLMEvent>()
    private var loadedModelId: String? = null

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
            helper.load(file.absolutePath, 4096, "You are Halo, a helpful AI.", {})
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

    override suspend fun complete(request: AIRequest): AIResponse {
        val startTime = System.currentTimeMillis()
        val prompt = request.conversation.messages.joinToString("\n") { it.content }
        var result = ""
        try {
            stream(prompt).collect { chunk ->
                result += chunk
            }
            return AIResponse(
                text = result,
                finishReason = "stop",
                latencyMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            return AIResponse(
                text = "[Error: ${e.message}]",
                finishReason = "error",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }

    override fun stream(prompt: String): Flow<String> = flow {
        if (!loadModelIfNeeded()) {
            emit("[Error: Engine not loaded or no active model]")
            return@flow
        }

        val currentEngine = engine ?: return@flow
        
        try {
            val formattedPrompt = "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
            currentEngine.predict(formattedPrompt, "", false)
            
            // Collect the shared flow for Ongoing events
            llamaEventFlow.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> emit(event.word)
                    is LlamaHelper.LLMEvent.Done -> return@collect // Or break
                    is LlamaHelper.LLMEvent.Error -> throw Exception("LLM Error: ${event.message}")
                    else -> {} // ignore Started/Loaded
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            emit("[Error during inference: ${e.message}]")
        }
    }

    fun close() {
        engine?.release()
        engine = null
        loadedModelId = null
    }
}
