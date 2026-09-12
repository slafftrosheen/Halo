package com.halo.providers.llamacpp

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
    CognitiveModel("qwen1.5-0.5b", "Qwen 1.5 (0.5B) Chat", "Extremely fast, tiny model. Great for testing pipeline.", "https://huggingface.co/Qwen/Qwen1.5-0.5B-Chat-GGUF/resolve/main/qwen1_5-0_5b-chat-q4_k_m.gguf", 398),
    CognitiveModel("phi3-mini", "Phi-3 Mini (3.8B) Instruct", "Microsoft's highly capable reasoning model.", "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf", 2390),
    CognitiveModel("llama3-8b", "Llama 3 (8B) Instruct", "State-of-the-art 8B model. Pushes 8GB RAM to the limit.", "https://huggingface.co/QuantFactory/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct.Q4_K_M.gguf", 4920)
)

enum class DownloadState { NotDownloaded, Downloading, Downloaded, Failed }

class ModelDownloadManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("halo_models", Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(Dispatchers.IO)
    private val activeDownloads = mutableMapOf<Long, String>()
    private val downloadJobs = mutableMapOf<Long, Job>()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    private val _activeModelId = MutableStateFlow<String?>(prefs.getString("active_model", null))
    val activeModelId: StateFlow<String?> = _activeModelId

    private val _customModels = MutableStateFlow<List<CognitiveModel>>(emptyList())
    val customModels: StateFlow<List<CognitiveModel>> = _customModels

    init {
        loadCustomModels()
        refreshStates()
    }

    fun getAllModels(): List<CognitiveModel> = AvailableModels + _customModels.value

    fun getModelFile(modelId: String): File? {
        val model = getAllModels().find { it.id == modelId }
        val fileName = model?.downloadUrl?.substringAfterLast("/") ?: "$modelId.gguf"
        val candidates = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, fileName) },
            context.getExternalFilesDir(null)?.let { File(it, "Download/$fileName") },
            context.getExternalFilesDir(null)?.let { File(it, fileName) },
            File(context.filesDir, fileName),
            runCatching { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName) }.getOrNull()
        )
        return candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0 }
    }

    fun refreshStates() {
        val states = mutableMapOf<String, DownloadState>()
        getAllModels().forEach { model ->
            states[model.id] = if (getModelFile(model.id) != null) {
                DownloadState.Downloaded
            } else {
                _downloadStates.value[model.id] ?: DownloadState.NotDownloaded
            }
        }
        _downloadStates.value = states
        selectPersistedOrFirstDownloaded()
    }

    private fun selectPersistedOrFirstDownloaded() {
        val persisted = prefs.getString("active_model", null)
        val downloaded = getAllModels().firstOrNull { getModelFile(it.id) != null }
        val selected = when {
            persisted != null && getModelFile(persisted) != null -> persisted
            downloaded != null -> downloaded.id
            else -> null
        }
        if (_activeModelId.value != selected) _activeModelId.value = selected
        if (selected != null) prefs.edit().putString("active_model", selected).apply()
        else prefs.edit().remove("active_model").apply()
    }

    fun downloadModel(modelId: String) {
        getAllModels().find { it.id == modelId }?.let { downloadUrl(it.id, it.name, it.downloadUrl) }
    }

    fun downloadCustomUrl(url: String, name: String) {
        if (url.isBlank()) return
        val id = "custom_" + url.hashCode().toString().replace("-", "n")
        val modelName = name.ifBlank { url.substringAfterLast("/") }
        _customModels.value = _customModels.value.filter { it.id != id } +
            CognitiveModel(id, modelName, "Custom GGUF Model from Hugging Face", url, 0, true)
        saveCustomModels()
        downloadUrl(id, modelName, url)
    }

    private fun downloadUrl(id: String, name: String, url: String) {
        val existing = activeDownloads.entries.firstOrNull { it.value == id }
        if (existing != null) return

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Downloading $name")
            setDescription("Halo on-device GGUF model download.")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, url.substringAfterLast("/"))
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val downloadId = downloadManager.enqueue(request)
        activeDownloads[downloadId] = id
        _downloadStates.value = _downloadStates.value.toMutableMap().apply { put(id, DownloadState.Downloading) }
        pollDownload(downloadId, id)
    }

    private fun pollDownload(downloadId: Long, modelId: String) {
        downloadJobs.remove(downloadId)?.cancel()
        downloadJobs[downloadId] = scope.launch {
            try {
                while (true) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val terminal = downloadManager.query(query).use { cursor ->
                        if (!cursor.moveToFirst()) {
                            setState(modelId, DownloadState.Failed)
                            true
                        } else {
                            when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    refreshStates()
                                    if (getModelFile(modelId) == null) setState(modelId, DownloadState.Failed)
                                    else setState(modelId, DownloadState.Downloaded)
                                    true
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    setState(modelId, DownloadState.Failed)
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                    if (terminal) break
                    delay(1000L)
                }
            } finally {
                activeDownloads.remove(downloadId)
                downloadJobs.remove(downloadId)
                refreshStates()
            }
        }
    }

    private fun setState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply { put(modelId, state) }
    }

    fun setActiveModel(modelId: String) {
        if (getModelFile(modelId) == null) return
        _activeModelId.value = modelId
        prefs.edit().putString("active_model", modelId).apply()
        refreshStates()
    }

    private fun loadCustomModels() {
        val json = prefs.getString("custom_models_json", "") ?: ""
        if (json.isNotBlank()) {
            _customModels.value = json.split(";;;").mapNotNull { entry ->
                val parts = entry.split("|||")
                if (parts.size < 5) return@mapNotNull null
                CognitiveModel(
                    id = parts[0],
                    name = parts[1],
                    description = parts[2],
                    downloadUrl = parts[3],
                    sizeMb = parts[4].toIntOrNull() ?: 0,
                    isCustom = true
                )
            }
        }
    }

    private fun saveCustomModels() {
        val json = _customModels.value.joinToString(";;;") { model ->
            listOf(model.id, model.name, model.description, model.downloadUrl, model.sizeMb.toString()).joinToString("|||")
        }
        prefs.edit().putString("custom_models_json", json).apply()
    }
}
