package com.halo.providers.llamacpp

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

data class CognitiveModel(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val sizeMb: Int
)

val AvailableModels = listOf(
    CognitiveModel(
        id = "qwen1.5-0.5b",
        name = "Qwen 1.5 (0.5B) Chat",
        description = "Extremely fast, tiny model. Great for testing pipeline.",
        downloadUrl = "https://huggingface.co/Qwen/Qwen1.5-0.5B-Chat-GGUF/resolve/main/qwen1_5-0_5b-chat-q4_k_m.gguf",
        sizeMb = 398
    ),
    CognitiveModel(
        id = "phi3-mini",
        name = "Phi-3 Mini (3.8B) Instruct",
        description = "Microsoft's highly capable reasoning model.",
        downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
        sizeMb = 2390
    ),
    CognitiveModel(
        id = "llama3-8b",
        name = "Llama 3 (8B) Instruct",
        description = "State-of-the-art 8B model. Pushes 8GB RAM to the limit.",
        downloadUrl = "https://huggingface.co/QuantFactory/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct.Q4_K_M.gguf",
        sizeMb = 4920
    )
)

enum class DownloadState {
    NotDownloaded, Downloading, Downloaded, Failed
}

class ModelDownloadManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("halo_models", Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    
    // Simplistic tracking for Alpha
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates
    
    private val _activeModelId = MutableStateFlow<String?>(prefs.getString("active_model", null))
    val activeModelId: StateFlow<String?> = _activeModelId

    init {
        refreshStates()
    }

    fun getModelFile(modelId: String): File? {
        val model = AvailableModels.find { it.id == modelId } ?: return null
        val fileName = model.downloadUrl.substringAfterLast("/")
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        return if (file.exists()) file else null
    }

    fun refreshStates() {
        val states = mutableMapOf<String, DownloadState>()
        AvailableModels.forEach { model ->
            val file = getModelFile(model.id)
            if (file != null && file.exists()) {
                states[model.id] = DownloadState.Downloaded
            } else {
                states[model.id] = _downloadStates.value[model.id] ?: DownloadState.NotDownloaded
            }
        }
        _downloadStates.value = states
    }

    fun downloadModel(modelId: String) {
        val model = AvailableModels.find { it.id == modelId } ?: return
        
        val uri = Uri.parse(model.downloadUrl)
        val request = DownloadManager.Request(uri).apply {
            setTitle("Downloading ${model.name}")
            setDescription("Halo is downloading a cognitive model.")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, model.downloadUrl.substringAfterLast("/"))
        }
        
        downloadManager.enqueue(request)
        
        val currentStates = _downloadStates.value.toMutableMap()
        currentStates[modelId] = DownloadState.Downloading
        _downloadStates.value = currentStates
    }

    fun setActiveModel(modelId: String) {
        val file = getModelFile(modelId)
        if (file != null && file.exists()) {
            _activeModelId.value = modelId
            prefs.edit().putString("active_model", modelId).apply()
        }
    }
}
