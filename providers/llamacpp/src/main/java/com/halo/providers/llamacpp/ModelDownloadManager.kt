package com.halo.providers.llamacpp

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.productionHaloDispatchers
import java.io.File


data class CognitiveModel(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val sizeMb: Int,
    val isCustom: Boolean = false,
    val tier: LocalModelTier = LocalModelTier.STANDARD,
    val parameterCount: String? = null,
    val quantization: String? = null,
    val expectedContextLength: Int? = null
)

enum class LocalModelTier { FAST, STANDARD, QUALITY }

val AvailableModels = listOf(
    CognitiveModel("qwen1.5-0.5b", "Qwen 1.5 (0.5B) Chat", "Legacy tiny compatibility model.", "https://huggingface.co/Qwen/Qwen1.5-0.5B-Chat-GGUF/resolve/main/qwen1_5-0_5b-chat-q4_k_m.gguf", 398, tier = LocalModelTier.FAST, parameterCount = "0.5B", quantization = "Q4_K_M", expectedContextLength = 32_768),
    CognitiveModel("qwen3-0.6b-q4", "Qwen3 0.6B", "Modern tiny-device candidate; selectable but not the default until Android binding validation is complete.", "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf", 522, tier = LocalModelTier.FAST, parameterCount = "0.6B", quantization = "Q4_K_M", expectedContextLength = 40_960),
    CognitiveModel("qwen3-1.7b-q4", "Qwen3 1.7B", "Higher-quality local tier for devices with sufficient memory.", "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", 1280, tier = LocalModelTier.QUALITY, parameterCount = "1.7B", quantization = "Q4_K_M", expectedContextLength = 40_960),
    CognitiveModel("phi3-mini", "Phi-3 Mini (3.8B) Instruct", "Microsoft's capable reasoning model.", "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf", 2390, tier = LocalModelTier.QUALITY, parameterCount = "3.8B", quantization = "Q4"),
    CognitiveModel("llama3-8b", "Llama 3 (8B) Instruct", "Large local tier; requires substantial memory.", "https://huggingface.co/QuantFactory/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct.Q4_K_M.gguf", 4920, tier = LocalModelTier.QUALITY, parameterCount = "8B", quantization = "Q4_K_M")
)

enum class DownloadState { NotDownloaded, Downloading, Downloaded, Failed }

class ModelDownloadManager(
    private val context: Context,
    private val modelRoot: File = File(context.noBackupFilesDir, "models"),
    private val dispatchers: HaloDispatchers = productionHaloDispatchers()
) : LocalModelInventoryRepository {
    private val prefs = context.getSharedPreferences("halo_models", Context.MODE_PRIVATE)
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val inventoryMutex = Mutex()
    private val metadataCache = GgufMetadataCache()
    @Volatile private var modelDirectories: List<File>? = null
    private val resolvedFiles = MutableStateFlow<Map<String, File>>(emptyMap())
    private val _inventoryState = MutableStateFlow<LocalModelInventoryState>(LocalModelInventoryState.Loading)
    override val inventoryState: StateFlow<LocalModelInventoryState> = _inventoryState
    private val downloadsLock = Any()
    private val activeDownloads = mutableMapOf<Long, String>()
    private val downloadJobs = mutableMapOf<Long, Job>()
    @Volatile private var closed = false

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId

    private val _customModels = MutableStateFlow<List<CognitiveModel>>(emptyList())
    val customModels: StateFlow<List<CognitiveModel>> = _customModels

    init {
        scope.launch {
            loadCustomModels()
            refresh(ModelInventoryRefreshReason.STARTUP)
            recoverPersistedDownloads()
        }
    }

    fun getAllModels(): List<CognitiveModel> = AvailableModels + _customModels.value

    /** Cached snapshot access only. This method never touches the filesystem. */
    fun getModelFile(modelId: String): File? = resolvedFiles.value[modelId]

    private fun findModelFile(modelId: String): Pair<File, GgufModelMetadata>? {
        val model = getAllModels().find { it.id == modelId }
        val fileName = model?.downloadUrl?.substringAfterLast("/") ?: "$modelId.gguf"
        val candidates = resolveModelDirectories().map { directory -> File(directory, fileName) }
        return candidates.firstNotNullOfOrNull { file ->
            val metadata = readUsableMetadata(file) ?: return@firstNotNullOfOrNull null
            file to metadata
        }
    }

    /** Called only from the repository's IO-confined inventory scan. */
    private fun resolveModelDirectories(): List<File> = modelDirectories ?: listOfNotNull(
        modelRoot,
    ).distinctBy { it.absolutePath }.also { modelDirectories = it }

    private fun readUsableMetadata(file: File): GgufModelMetadata? {
        if (!file.exists() || !file.isFile) return null
        val length = file.length()
        if (length <= 0L) return null
        val fingerprint = FileFingerprint(file.absolutePath, length, file.lastModified())
        return metadataCache.getOrRead(fingerprint) { GgufMetadataReader.read(file) }
    }

    fun refreshStates() {
        if (!closed) scope.launch { refresh(ModelInventoryRefreshReason.USER_REQUEST) }
    }

    override suspend fun refresh(reason: ModelInventoryRefreshReason) = inventoryMutex.withLock {
        if (closed) return@withLock
        _inventoryState.value = LocalModelInventoryState.Loading
        runCatching {
            withContext(dispatchers.io) {
                val descriptors = mutableListOf<LocalModelDescriptor>()
                val files = mutableMapOf<String, File>()
                val states = mutableMapOf<String, DownloadState>()
                getAllModels().forEach { model ->
                    val resolved = findModelFile(model.id)
                    if (resolved != null) {
                        val (file, metadata) = resolved
                        files[model.id] = file
                        descriptors += LocalModelDescriptor(
                            id = model.id,
                            displayName = model.name,
                            path = file.absolutePath,
                            bytes = file.length(),
                            modifiedAt = file.lastModified(),
                            ggufMetadata = metadata,
                            valid = true
                        )
                        states[model.id] = DownloadState.Downloaded
                    } else states[model.id] = _downloadStates.value[model.id] ?: DownloadState.NotDownloaded
                }
                val selected = selectPersistedOrFirstDownloaded(files)
                InventoryResult(files.toMap(), descriptors.toList(), states.toMap(), selected)
            }
        }.onSuccess { (files, descriptors, states, selected) ->
            resolvedFiles.value = files
            _downloadStates.value = states
            _activeModelId.value = selected
            _inventoryState.value = LocalModelInventoryState.Ready(descriptors, System.currentTimeMillis())
        }.onFailure { error ->
            _inventoryState.value = LocalModelInventoryState.Failed(error.message ?: "Model inventory scan failed")
        }
    }

    override suspend fun resolveModel(modelId: String): LocalModelDescriptor? {
        val ready = inventoryState.value as? LocalModelInventoryState.Ready
        return ready?.models?.firstOrNull { it.id == modelId }
    }

    override fun invalidate(reason: String) {
        metadataCache.invalidate()
        _inventoryState.value = LocalModelInventoryState.Loading
        if (!closed) scope.launch { refresh(ModelInventoryRefreshReason.INVALIDATED) }
    }

    private fun selectPersistedOrFirstDownloaded(files: Map<String, File>): String? {
        val persisted = prefs.getString("active_model", null)
        val downloaded = getAllModels().firstOrNull { files.containsKey(it.id) }
        val selected = when {
            persisted != null && files.containsKey(persisted) -> persisted
            downloaded != null -> downloaded.id
            else -> null
        }
        if (selected != null) prefs.edit().putString("active_model", selected).apply()
        else prefs.edit().remove("active_model").apply()
        return selected
    }

    fun downloadModel(modelId: String) {
        if (closed) return
        scope.launch {
            getAllModels().find { it.id == modelId }?.let { downloadUrl(it.id, it.name, it.downloadUrl) }
        }
    }

    fun downloadCustomUrl(url: String, name: String) {
        if (closed || url.isBlank()) return
        scope.launch {
            val id = "custom_" + url.hashCode().toString().replace("-", "n")
            val modelName = name.ifBlank { url.substringAfterLast("/") }
            _customModels.value = _customModels.value.filter { it.id != id } +
                CognitiveModel(id, modelName, "Custom GGUF Model from Hugging Face", url, 0, true)
            saveCustomModels()
            downloadUrl(id, modelName, url)
        }
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

    private suspend fun recoverPersistedDownloads() {
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
                    finalizeDownloadedModel(modelId)
                    refresh(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)
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
                                finalizeDownloadedModel(modelId)
                                refresh(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)
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
            if (!closed) refresh(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)
        }
    }

    private fun setState(modelId: String, state: DownloadState) {
        if (closed) return
        _downloadStates.value = _downloadStates.value.toMutableMap().apply { put(modelId, state) }
    }

    private fun finalizeDownloadedModel(modelId: String) {
        val model = getAllModels().firstOrNull { it.id == modelId } ?: return
        val fileName = model.downloadUrl.substringAfterLast("/")
        val source = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, fileName) },
            context.getExternalFilesDir(null)?.let { File(File(it, "Download"), fileName) },
        ).firstOrNull(File::isFile) ?: return
        modelRoot.mkdirs()
        val destination = File(modelRoot, fileName)
        val temporary = File(modelRoot, ".$fileName.download")
        source.inputStream().use { input -> temporary.outputStream().use(input::copyTo) }
        if (temporary.length() == source.length()) {
            if (!destination.exists() && temporary.renameTo(destination)) source.delete()
            else temporary.delete()
        } else temporary.delete()
        modelDirectories = null
    }

    fun setActiveModel(modelId: String) {
        if (closed || getModelFile(modelId) == null) return
        _activeModelId.value = modelId
        scope.launch { prefs.edit().putString("active_model", modelId).apply() }
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

    private data class InventoryResult(
        val files: Map<String, File>,
        val descriptors: List<LocalModelDescriptor>,
        val states: Map<String, DownloadState>,
        val selectedModelId: String?
    )
}
