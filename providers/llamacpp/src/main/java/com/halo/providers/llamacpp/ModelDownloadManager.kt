package com.halo.providers.llamacpp

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.halo.sdk.provider.LocalModelFormat
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.productionHaloDispatchers
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.security.MessageDigest


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
    val expectedContextLength: Int? = null,
    val format: LocalModelFormat = LocalModelFormat.GGUF,
    val supportsVision: Boolean = false,
)

enum class LocalModelTier { FAST, STANDARD, QUALITY }

val AvailableModels = listOf(
    CognitiveModel(
        id = "qwen3-0.6b-litert",
        name = "Qwen3 0.6B · LiteRT",
        description = "Recommended Android local model. Google LiteRT-LM runtime with GPU→CPU fallback; open download.",
        downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
        sizeMb = 586,
        tier = LocalModelTier.FAST,
        parameterCount = "0.6B",
        quantization = "dynamic INT8",
        expectedContextLength = 4_096,
        format = LocalModelFormat.LITERT_LM,
    ),
    CognitiveModel("qwen1.5-0.5b", "Qwen 1.5 (0.5B) Chat · GGUF", "Compatibility GGUF model for the llama.cpp runtime.", "https://huggingface.co/Qwen/Qwen1.5-0.5B-Chat-GGUF/resolve/main/qwen1_5-0_5b-chat-q4_k_m.gguf", 398, tier = LocalModelTier.FAST, parameterCount = "0.5B", quantization = "Q4_K_M", expectedContextLength = 32_768),
    CognitiveModel("qwen3-0.6b-q4", "Qwen3 0.6B · GGUF", "Power-user GGUF variant. Uses the third-party llama.cpp Android wrapper.", "https://huggingface.co/ggml-org/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf", 522, tier = LocalModelTier.FAST, parameterCount = "0.6B", quantization = "Q4_K_M", expectedContextLength = 40_960),
    CognitiveModel("qwen3-1.7b-q4", "Qwen3 1.7B · GGUF", "Higher-quality GGUF tier for devices with sufficient memory.", "https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf", 1280, tier = LocalModelTier.QUALITY, parameterCount = "1.7B", quantization = "Q4_K_M", expectedContextLength = 40_960),
    CognitiveModel("phi3-mini", "Phi-3 Mini (3.8B) · GGUF", "Compatibility GGUF model; requires substantial Android RAM.", "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf", 2390, tier = LocalModelTier.QUALITY, parameterCount = "3.8B", quantization = "Q4"),
    CognitiveModel("llama3-8b", "Llama 3 (8B) · GGUF", "Large expert GGUF tier; requires substantial memory.", "https://huggingface.co/QuantFactory/Meta-Llama-3-8B-Instruct-GGUF/resolve/main/Meta-Llama-3-8B-Instruct.Q4_K_M.gguf", 4920, tier = LocalModelTier.QUALITY, parameterCount = "8B", quantization = "Q4_K_M")
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
    private val workerJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    @Volatile private var closed = false

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates
    private val _downloadErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val downloadErrors: StateFlow<Map<String, String>> = _downloadErrors
    private val enqueueMutex = Mutex()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId

    private val _customModels = MutableStateFlow<List<CognitiveModel>>(emptyList())
    val customModels: StateFlow<List<CognitiveModel>> = _customModels

    private val initialization = scope.launch {
        loadCustomModels()
        refresh(ModelInventoryRefreshReason.STARTUP)
        recoverPersistedDownloads()
        recoverWorkerDownloads()
    }

    fun getAllModels(): List<CognitiveModel> = AvailableModels + _customModels.value

    /** Cached snapshot access only. This method never touches the filesystem. */
    fun getModelFile(modelId: String): File? = resolvedFiles.value[modelId]

    private fun findModelFile(modelId: String): Pair<File, GgufModelMetadata?>? {
        val model = getAllModels().find { it.id == modelId } ?: return null
        val candidates = resolveModelDirectories().flatMap { directory ->
            listOf(
                File(directory, fileNameFor(model)),
                File(directory, model.downloadUrl.substringAfterLast("/").substringBefore("?")),
            )
        }.distinctBy { it.absolutePath }

        return candidates.firstNotNullOfOrNull { file ->
            when (model.format) {
                LocalModelFormat.LITERT_LM -> {
                    if (isUsableLiteRtArtifact(file)) file to null else null
                }
                LocalModelFormat.GGUF -> {
                    val metadata = readUsableMetadata(file) ?: return@firstNotNullOfOrNull null
                    val role = GgufArtifactClassifier.classifyLocalFile(file)
                    if (role != GgufArtifactRole.BASE_MODEL) null else file to metadata
                }
            }
        }
    }

    private fun isUsableLiteRtArtifact(file: File): Boolean =
        file.isFile &&
            file.length() >= MIN_LITERT_MODEL_BYTES &&
            file.extension.equals("litertlm", ignoreCase = true)

    private fun isUsableDownloadedFile(model: CognitiveModel, file: File): Boolean = when (model.format) {
        LocalModelFormat.LITERT_LM -> isUsableLiteRtArtifact(file)
        LocalModelFormat.GGUF ->
            readUsableMetadata(file) != null &&
                GgufArtifactClassifier.classifyLocalFile(file) == GgufArtifactRole.BASE_MODEL
    }

    /** Called only from the repository's IO-confined inventory scan. */
    private fun resolveModelDirectories(): List<File> = modelDirectories ?: listOfNotNull(
        modelRoot,
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
    ).distinctBy { it.absolutePath }.also { modelDirectories = it }

    private fun readUsableMetadata(file: File): GgufModelMetadata? {
        if (!file.exists() || !file.isFile) return null
        val length = file.length()
        if (length <= 0L) return null
        val fingerprint = FileFingerprint(file.absolutePath, length, file.lastModified())
        return metadataCache.getOrRead(fingerprint) {
            // Metadata enrichment is advisory; the native loader owns compatibility.
            GgufMetadataReader.read(file)
        }
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
                            format = model.format,
                            ggufMetadata = metadata,
                            valid = true
                        )
                        states[model.id] = DownloadState.Downloaded
                    } else states[model.id] = _downloadStates.value[model.id] ?: DownloadState.NotDownloaded
                }
                val selected = selectPersistedDownloaded(files)
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

    private fun selectPersistedDownloaded(files: Map<String, File>): String? {
        val persisted = prefs.getString("active_model", null)
        val selected = persisted?.takeIf(files::containsKey)
        if (selected != null) prefs.edit().putString("active_model", selected).apply()
        else prefs.edit().remove("active_model").apply()
        return selected
    }

    fun downloadModel(modelId: String) {
        if (closed) return
        scope.launch {
            initialization.join()
            getAllModels().find { it.id == modelId }?.let { downloadUrl(it.id, it.name, it.downloadUrl) }
        }
    }

    fun downloadCustomUrl(url: String, name: String) {
        if (closed || url.isBlank()) return
        scope.launch {
            initialization.join()
            val id = "custom_" + url.hashCode().toString().replace("-", "n")
            val modelName = name.ifBlank { url.substringAfterLast("/") }
            val format = if (url.substringBefore('?').endsWith(".litertlm", ignoreCase = true)) {
                LocalModelFormat.LITERT_LM
            } else {
                LocalModelFormat.GGUF
            }
            _customModels.value = _customModels.value.filter { it.id != id } +
                CognitiveModel(
                    id = id,
                    name = modelName,
                    description = if (format == LocalModelFormat.LITERT_LM) "Custom LiteRT-LM model" else "Custom GGUF model",
                    downloadUrl = url,
                    sizeMb = 0,
                    isCustom = true,
                    format = format,
                )
            saveCustomModels()
            downloadUrl(id, modelName, url)
        }
    }

    fun downloadCatalogModel(repoId: String, revision: String, filename: String,
                             bytes: Long, quantization: String) {
        val id = catalogModelId(repoId, revision, filename)
        if (closed) return
        if (!Regex("[0-9a-fA-F]{40}").matches(revision) || !filename.endsWith(".gguf", true) || bytes <= 0L) {
            _downloadErrors.value = _downloadErrors.value + (id to "Model metadata is incomplete; refresh the catalog")
            setState(id, DownloadState.Failed)
            return
        }
        setState(id, DownloadState.Downloading)
        scope.launch {
            try {
                initialization.join()
                enqueueMutex.withLock {
                    val url = "https://huggingface.co/$repoId/resolve/$revision/" + filename.split('/').joinToString("/") {
                        java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                    }
                    val model = CognitiveModel(id, repoId.substringAfterLast('/'), "Hugging Face GGUF",
                        url, ((bytes + 1024L * 1024L - 1) / (1024L * 1024L)).toInt(), true, quantization = quantization)
                    _customModels.value = _customModels.value.filter { it.id != id } + model
                    saveCustomModels()
                    val manager = WorkManager.getInstance(context)
                    val existing = manager.getWorkInfosForUniqueWork("halo_model_$id").get().firstOrNull { !it.state.isFinished }
                    val workId = if (existing != null) existing.id else {
                        val work = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                            .setInputData(workDataOf(ModelDownloadWorker.KEY_URL to url,
                                ModelDownloadWorker.KEY_FILENAME to fileNameFor(model), ModelDownloadWorker.KEY_BYTES to bytes))
                            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                            .addTag("halo_model_$id").build()
                        manager.enqueueUniqueWork("halo_model_$id", ExistingWorkPolicy.KEEP, work).result.get()
                        work.id
                    }
                    check(prefs.edit().putString("worker_$id", workId.toString()).commit()) { "Could not persist download identity" }
                    _downloadErrors.value = _downloadErrors.value - id
                    startWorkerMonitor(id)
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
              catch (error: Exception) {
                _downloadErrors.value = _downloadErrors.value + (id to (error.message ?: "Unable to enqueue model download"))
                setState(id, DownloadState.Failed)
            }
        }
    }

    private fun workerInfo(modelId: String): WorkInfo? {
        val manager = WorkManager.getInstance(context)
        val saved = prefs.getString("worker_$modelId", null)?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
        if (saved != null) return manager.getWorkInfoById(saved).get()
        return manager.getWorkInfosForUniqueWork("halo_model_$modelId").get().firstOrNull { !it.state.isFinished }
    }

    private suspend fun recoverWorkerDownloads() {
        _customModels.value.filter { it.id.startsWith("hf_") }.forEach { model ->
            val info = workerInfo(model.id)
            if (info?.state in listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)) {
                setState(model.id, DownloadState.Downloading)
                if (workerJobs[model.id]?.isActive != true) {
                    startWorkerMonitor(model.id)
                }
            }
        }
    }

    private fun startWorkerMonitor(modelId: String) {
        workerJobs.remove(modelId)?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) { monitorWorker(modelId) }
        workerJobs[modelId] = job
        job.start()
    }

    private suspend fun monitorWorker(modelId: String) {
        try {
            while (!closed) {
                val info = workerInfo(modelId)
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        refresh(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)
                        setState(modelId, if (getModelFile(modelId) != null)
                            DownloadState.Downloaded else DownloadState.Failed)
                        return
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _downloadErrors.value = _downloadErrors.value + (modelId to
                            (info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download stopped; tap Download to retry"))
                        setState(modelId, DownloadState.Failed)
                        return
                    }
                    null -> {
                        _downloadErrors.value = _downloadErrors.value + (modelId to "Download work is missing; tap Download to retry")
                        setState(modelId, DownloadState.Failed)
                        return
                    }
                    else -> delay(1_000L)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
          catch (error: Exception) {
            _downloadErrors.value = _downloadErrors.value + (modelId to (error.message ?: "Unable to read download status"))
            setState(modelId, DownloadState.Failed)
        } finally {
            currentCoroutineContext()[Job]?.let { workerJobs.remove(modelId, it) }
        }
    }

    fun catalogModelId(repoId: String, revision: String, filename: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$repoId:$revision:$filename".toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
        return "hf_$digest"
    }

    private fun downloadUrl(id: String, name: String, url: String) {
        try {
            _downloadErrors.update { it - id }
            enqueuePlatformDownload(id, name, url)
        } catch (error: Exception) {
            _downloadErrors.update { it + (id to (error.message ?: "Unable to start model download")) }
            setState(id, DownloadState.Failed)
        }
    }

    private fun enqueuePlatformDownload(id: String, name: String, url: String) {
        if (closed) return
        synchronized(downloadsLock) {
            if (activeDownloads.values.any { it == id }) return
            val model = getAllModels().firstOrNull { it.id == id } ?: return

            // Failed DownloadManager destinations must not poison every retry with ERROR_FILE_ALREADY_EXISTS.
            val previous = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileNameFor(model))
            if (previous.exists() && !isUsableDownloadedFile(model, previous)) previous.delete()
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("Downloading $name")
                setDescription("Halo on-device local model download.")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileNameFor(model))
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
                                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                _downloadErrors.update { it + (modelId to "Android download failed (code $reason); tap Download to retry") }
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
        _downloadStates.update { it + (modelId to state) }
    }

    private fun finalizeDownloadedModel(modelId: String) {
        // DownloadManager writes directly to the app-specific model directory.
        // The inventory scan validates the selected runtime format before exposing it to the native provider.
        modelDirectories = null
    }

    private fun fileNameFor(model: CognitiveModel): String {
        if (model.id.startsWith("hf_")) {
            val path = model.downloadUrl.substringAfter("/resolve/").substringAfter('/')
            val safePath = URLDecoder.decode(path, "UTF-8").replace('/', '_')
            return "${model.id}_$safePath"
        }
        val basename = model.downloadUrl.substringAfterLast('/').substringBefore('?')
        return if (model.isCustom) "${model.id}_$basename" else basename
    }

    fun setActiveModel(modelId: String) {
        if (closed || getModelFile(modelId) == null) return
        _activeModelId.value = modelId
        scope.launch { prefs.edit().putString("active_model", modelId).apply() }
    }

    private fun loadCustomModels() {
        val json = prefs.getString("custom_models_json", "") ?: ""
        if (json.startsWith("[")) {
            val array = runCatching { JSONArray(json) }.getOrNull() ?: return
            _customModels.value = (0 until array.length()).mapNotNull { index ->
                val row = array.optJSONObject(index) ?: return@mapNotNull null
                runCatching {
                    CognitiveModel(
                        id = row.getString("id"),
                        name = row.getString("name"),
                        description = row.optString("description"),
                        downloadUrl = row.getString("url"),
                        sizeMb = row.optInt("sizeMb"),
                        isCustom = true,
                        quantization = row.optString("quantization").takeIf { it.isNotBlank() && it != "null" },
                        format = row.optString("format").takeIf { it.isNotBlank() }?.let {
                            runCatching { LocalModelFormat.valueOf(it) }.getOrNull()
                        } ?: if (row.getString("url").substringBefore('?').endsWith(".litertlm", ignoreCase = true)) {
                            LocalModelFormat.LITERT_LM
                        } else {
                            LocalModelFormat.GGUF
                        },
                    )
                }.getOrNull()
            }
        } else if (json.isNotBlank()) {
            _customModels.value = json.split(";;;").mapNotNull { entry ->
                val parts = entry.split("|||")
                if (parts.size < 5) return@mapNotNull null
                CognitiveModel(
                    id = parts[0],
                    name = parts[1],
                    description = parts[2],
                    downloadUrl = parts[3],
                    sizeMb = parts[4].toIntOrNull() ?: 0,
                    isCustom = true,
                    format = if (parts[3].substringBefore('?').endsWith(".litertlm", ignoreCase = true)) {
                        LocalModelFormat.LITERT_LM
                    } else {
                        LocalModelFormat.GGUF
                    }
                )
            }
            saveCustomModels()
        }
    }

    private fun saveCustomModels() {
        val json = JSONArray().apply {
            _customModels.value.forEach { model ->
                put(JSONObject().put("id", model.id).put("name", model.name)
                    .put("description", model.description).put("url", model.downloadUrl)
                    .put("sizeMb", model.sizeMb).put("quantization", model.quantization)
                    .put("format", model.format.name))
            }
        }
        prefs.edit().putString("custom_models_json", json.toString()).apply()
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
        workerJobs.values.forEach { it.cancel() }
        workerJobs.clear()
        scope.coroutineContext[Job]?.cancel()
    }

    private companion object {
        private const val DOWNLOAD_PREF_PREFIX = "active_download_"
        private const val MIN_LITERT_MODEL_BYTES = 16L * 1024L * 1024L
    }

    private data class InventoryResult(
        val files: Map<String, File>,
        val descriptors: List<LocalModelDescriptor>,
        val states: Map<String, DownloadState>,
        val selectedModelId: String?
    )
}
