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
    val sizeMb: Int,
    val isCustom: Boolean = false
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
    
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates
    
    private val _activeModelId = MutableStateFlow<String?>(prefs.getString("active_model", "qwen1.5-0.5b"))
    val activeModelId: StateFlow<String?> = _activeModelId

    private val _customModels = MutableStateFlow<List<CognitiveModel>>(emptyList())
    val customModels: StateFlow<List<CognitiveModel>> = _customModels

    init {
        loadCustomModels()
        refreshStates()
    }

    fun getAllModels(): List<CognitiveModel> {
        return AvailableModels + _customModels.value
    }

    fun getModelFile(modelId: String): File? {
        val model = getAllModels().find { it.id == modelId }
        val fileName = if (model != null) {
            model.downloadUrl.substringAfterLast("/")
        } else {
            "$modelId.gguf"
        }
        
        val candidates = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName),
            File(context.getExternalFilesDir(null), "Download/$fileName"),
            File(context.getExternalFilesDir(null), fileName),
            File(context.filesDir, fileName),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
        )
        
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    fun refreshStates() {
        val states = mutableMapOf<String, DownloadState>()
        getAllModels().forEach { model ->
            val file = getModelFile(model.id)
            if (file != null && file.exists() && file.length() > 0) {
                states[model.id] = DownloadState.Downloaded
            } else {
                states[model.id] = _downloadStates.value[model.id] ?: DownloadState.NotDownloaded
            }
        }
        _downloadStates.value = states
    }

    fun downloadModel(modelId: String) {
        val model = getAllModels().find { it.id == modelId } ?: return
        downloadUrl(model.id, model.name, model.downloadUrl)
    }

    fun downloadCustomUrl(url: String, name: String) {
        if (url.isBlank()) return
        val id = "custom_" + url.hashCode().toString().replace("-", "n")
        val modelName = name.ifBlank { url.substringAfterLast("/") }
        
        val customModel = CognitiveModel(
            id = id,
            name = modelName,
            description = "Custom GGUF Model from Hugging Face",
            downloadUrl = url,
            sizeMb = 0,
            isCustom = true
        )
        
        val list = _customModels.value.filter { it.id != id } + customModel
        _customModels.value = list
        saveCustomModels()

        downloadUrl(id, modelName, url)
    }

    private fun downloadUrl(id: String, name: String, url: String) {
        val uri = Uri.parse(url)
        val fileName = url.substringAfterLast("/")
        val request = DownloadManager.Request(uri).apply {
            setTitle("Downloading $name")
            setDescription("Halo on-device GGUF model download.")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        
        downloadManager.enqueue(request)
        
        val currentStates = _downloadStates.value.toMutableMap()
        currentStates[id] = DownloadState.Downloading
        _downloadStates.value = currentStates
    }

    fun setActiveModel(modelId: String) {
        _activeModelId.value = modelId
        prefs.edit().putString("active_model", modelId).apply()
        refreshStates()
    }

    private fun loadCustomModels() {
        val json = prefs.getString("custom_models_json", "") ?: ""
        if (json.isNotBlank()) {
            val items = json.split(";;;").mapNotNull { entry ->
                val parts = entry.split("|||")
                if (parts.size >= 3) {
                    CognitiveModel(
                        id = parts[0],
                        name = parts[1],
                        description = "Custom GGUF Model",
                        downloadUrl = parts[2],
                        sizeMb = 0,
                        isCustom = true
                    )
                } else null
            }
            _customModels.value = items
        }
    }

    private fun saveCustomModels() {
        val encoded = _customModels.value.joinToString(";;;") { "${it.id}|||${it.name}|||${it.downloadUrl}" }
        prefs.edit().putString("custom_models_json", encoded).apply()
    }
}
