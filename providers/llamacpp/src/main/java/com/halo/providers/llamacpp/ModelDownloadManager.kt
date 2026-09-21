package com.halo.providers.llamacpp

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadsLock = Any()
    private val activeDownloads = mutableMapOf<Long, String>()
    private val downloadJobs = mutableMapOf<Long, Job>()
    @Volatile private var closed = false

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    private val _activeModelId = MutableStateFlow<String?>(prefs.getString("active_model", null))
    val activeModelId: StateFlow<String?> = _activeModelId

    private val _customModels = MutableStateFlow<List<CognitiveModel>>(emptyList())
    val customModels: StateFlow<List<CognitiveModel>> = _customModels

    init {
        loadCustomModels()
        refreshStates()
        recoverPersistedDownloads()
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
        return candidates.firstOrNull { isUsableGguf(it) }
    }

    private fun isUsableGguf(file: File): Boolean =
        file.exists() && file.isFile && file.length() > 0L && GgufMetadataReader.read(file) != null

    fun refreshStates() {
        if (closed) return
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
            persisted != null -> persisted
            downloaded != null -> downloaded.id
            else -> null
        }
        if (_activeModelId.value != selected) _activeModelId.value = selected
        if (selected != null) prefs.edit().putString("active_model", selected).apply()
        else prefs.edit().remove("active_model").apply()
    }

    fun downloadModel(modelId: String) {
        if (closed) return
        getAllModels().find { it.id == modelId }?.let { downloadUrl(it.id, it.name, it.downloadUrl) }
    }

    fun downloadCustomUrl(url: String, name: String) {
        if (closed || url.isBlank()) return
        val id = "custom_" + url.hashCode().toString().replace("-", "n")
        val modelName = name.ifBlank { url.substringAfterLast("/") }
        _customModels.value = _customModels.value.filter { it.id != id } +
            CognitiveModel(id, modelName, "Custom GGUF Model from Hugging Face", url, 0, true)
        saveCustomModels()
        downloadUrl(id, modelName, url)
    }

    private fun downloadUrl(id: String, name: String, url: String) {
        if (closed) return
        synchronized(downloadsLock) {
            if (activeDownloads.values.any { it == id }) return

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
            persistDownload(downloadId, id)
            _downloadStates.value = _downloadStates.value.toMutableMap().apply { put(id, DownloadState.Downloading) }
            downloadJobs[downloadId] = scope.launch { pollDownload(downloadId, id) }
        }
    }

    private fun recoverPersistedDownloads() {
        if (closed) return
        val pending = prefs.all
            .asSequence()
            .filter { it.key.startsWith(DOWNLOAD_PREF_PREFIX) }
            .mapNotNull { entry ->
                val id = entry.key.removePrefix(DOWNLOAD_PREF_PREFIX).toLongOrNull() ?: return@mapNotNull null
                val modelId = entry.value as? String ?: return@mapNotNull null
                id to modelId
            }
            .toList()

        pending.forEach { (downloadId, modelId) ->
            val status = runCatching {
                downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                    if (!cursor.moveToFirst()) null
                    else cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                }
            }.getOrNull()

            when (status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED -> {
                    synchronized(downloadsLock) {
                        if (!closed && !activeDownloads.containsKey(downloadId)) {
                            activeDownloads[downloadId] = modelId
                            _downloadStates.value = _downloadStates.value.toMutableMap().apply { put(modelId, DownloadState.Downloading) }
                            downloadJobs[downloadId] = scope.launch { pollDownload(downloadId, modelId) }
                        }
                    }
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    clearPersistedDownload(downloadId)
                    if (getModelFile(modelId) == null) setState(modelId, DownloadState.Failed)
                    else setState(modelId, DownloadState.Downloaded)
                }
                else -> {
                    clearPersistedDownload(downloadId)
                    if (status != null) setState(modelId, DownloadState.Failed)
                }
            }
        }
    }

    private fun persistDownload(downloadId: Long, modelId: String) {
        prefs.edit().putString(DOWNLOAD_PREF_PREFIX + downloadId, modelId).apply()
    }

    private fun clearPersistedDownload(downloadId: Long) {
        prefs.edit().remove(DOWNLOAD_PREF_PREFIX + downloadId).apply()
    }

    private suspend fun pollDownload(downloadId: Long, modelId: String) {
        var terminal = false
        try {
            while (!closed) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                terminal = downloadManager.query(query).use { cursor ->
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
            synchronized(downloadsLock) {
                activeDownloads.remove(downloadId)
                downloadJobs.remove(downloadId)
            }
            if (terminal) clearPersistedDownload(downloadId)
            if (!closed) refreshStates()
        }
    }

    private fun setState(modelId: String, state: DownloadState) {
        if (closed) return
        _downloadStates.value = _downloadStates.value.toMutableMap().apply { put(modelId, state) }
    }

    fun setActiveModel(modelId: String) {
        if (closed || getModelFile(modelId) == null) return
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

    fun close() {
        closed = true
        val jobs = synchronized(downloadsLock) {
            val current = downloadJobs.values.toList()
            downloadJobs.clear()
            activeDownloads.clear()
            current
        }
        jobs.forEach { it.cancel() }
        scope.coroutineContext[Job]?.cancel()
    }

    private companion object {
        private const val DOWNLOAD_PREF_PREFIX = "active_download_"
    }
}
