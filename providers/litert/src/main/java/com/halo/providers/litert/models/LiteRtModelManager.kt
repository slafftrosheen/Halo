package com.halo.providers.litert.models

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

sealed interface DownloadState {
    data object NotDownloaded : DownloadState
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState
    data object Downloaded : DownloadState
    data class Failed(val reason: String) : DownloadState
}

class LiteRtModelManager(
    private val context: Context,
    private val modelRoot: File = File(context.noBackupFilesDir, "models"),
) : LocalModelInventoryRepository {

    @Volatile
    private var residentModelIdProvider: () -> String? = { null }

    fun observeResidentModel(provider: () -> String?) {
        residentModelIdProvider = provider
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inventoryMutex = Mutex()
    private val _inventoryState = MutableStateFlow<LocalModelInventoryState>(LocalModelInventoryState.Loading)
    override val inventoryState: StateFlow<LocalModelInventoryState> = _inventoryState.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val _legacyGgufFiles = MutableStateFlow<List<File>>(emptyList())
    val legacyGgufFiles: StateFlow<List<File>> = _legacyGgufFiles.asStateFlow()

    init {
        scope.launch {
            refresh(ModelInventoryRefreshReason.STARTUP)
        }
    }

    override fun invalidate(reason: String) {
        scope.launch { refresh(ModelInventoryRefreshReason.INVALIDATED) }
    }

    override suspend fun refresh(reason: ModelInventoryRefreshReason) = inventoryMutex.withLock {
        _inventoryState.value = LocalModelInventoryState.Loading
        runCatching {
            withContext(Dispatchers.IO) {
                if (!modelRoot.exists()) modelRoot.mkdirs()

                // 1. Scan legacy GGUF files for user cleanup
                val ggufs = modelRoot.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }?.toList().orEmpty()
                _legacyGgufFiles.value = ggufs

                // 2. Scan valid LiteRT models (.litertlm, ignoring .part)
                val litertFiles = modelRoot.listFiles { f ->
                    f.isFile && f.name.endsWith(".litertlm", ignoreCase = true) && !f.name.endsWith(".part", ignoreCase = true) && f.length() > 0L
                }?.toList().orEmpty()

                val descriptors = litertFiles.map { file ->
                    val modelId = file.nameWithoutExtension
                    val sidecarFile = File(modelRoot, "$modelId.halo-model.json")
                    val sidecar = if (sidecarFile.exists()) InstalledLiteRtModelMetadata.fromJson(sidecarFile.readText()) else null
                    val catalogMatch = LiteRtModelCatalog.BUILT_IN_MODELS.firstOrNull { it.id == modelId || file.name.startsWith(it.id) }

                    LocalModelDescriptor(
                        id = catalogMatch?.id ?: sidecar?.id ?: modelId,
                        displayName = catalogMatch?.displayName ?: sidecar?.displayName ?: modelId.replace('_', ' ').replace('-', ' '),
                        path = file.absolutePath,
                        bytes = file.length(),
                        modifiedAt = file.lastModified(),
                        sha256 = catalogMatch?.sha256 ?: sidecar?.sha256,
                        contextWindowTokens = catalogMatch?.contextWindowTokens ?: sidecar?.contextWindowTokens ?: 4096,
                        supportsVision = catalogMatch?.supportsVision ?: sidecar?.supportsVision ?: false,
                        supportsTools = catalogMatch?.supportsTools ?: sidecar?.supportsTools ?: false,
                        valid = true
                    )
                }
                descriptors
            }
        }.onSuccess { descriptors ->
            val states = descriptors.associate { it.id to DownloadState.Downloaded }
            _downloadStates.value = _downloadStates.value + states
            _inventoryState.value = LocalModelInventoryState.Ready(descriptors, System.currentTimeMillis())
        }.onFailure { err ->
            _inventoryState.value = LocalModelInventoryState.Failed(err.message ?: "Model inventory scan failed")
        }
        Unit
    }

    override suspend fun resolveModel(modelId: String): LocalModelDescriptor? {
        val ready = inventoryState.value as? LocalModelInventoryState.Ready
        return ready?.models?.firstOrNull { it.id == modelId }
    }

    suspend fun importModelFromUri(uri: Uri, displayName: String): Result<LocalModelDescriptor> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver
            val tempFile = File(modelRoot, "import_${System.currentTimeMillis()}.part")
            val digest = MessageDigest.getInstance("SHA-256")

            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                        output.write(buffer, 0, bytesRead)
                    }
                }
            } ?: throw IllegalStateException("Unable to open URI input stream")

            if (tempFile.length() == 0L) {
                tempFile.delete()
                throw IllegalStateException("Imported file was empty")
            }

            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val modelId = displayName.lowercase().replace(Regex("[^a-z0-9_]"), "_").trim('_')
            val finalFile = File(modelRoot, "$modelId.litertlm")

            if (tempFile.renameTo(finalFile)) {
                val metadata = InstalledLiteRtModelMetadata(
                    id = modelId,
                    displayName = displayName,
                    sourceUrl = null,
                    sha256 = sha256,
                    bytes = finalFile.length(),
                )
                File(modelRoot, "$modelId.halo-model.json").writeText(metadata.toJson())
                refresh(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)
                resolveModel(modelId) ?: throw IllegalStateException("Model import failed to register")
            } else {
                tempFile.delete()
                throw IllegalStateException("Failed to rename temporary import file")
            }
        }
    }

    fun downloadCatalogModel(model: LiteRtCatalogModel) {
        scope.launch {
            _downloadStates.value = _downloadStates.value + (model.id to DownloadState.Downloading(0f, 0L, model.expectedBytes ?: 0L))
            val tempFile = File(modelRoot, "${model.id}.part")
            runCatching {
                val finalFile = File(modelRoot, "${model.id}.litertlm")
                if (!modelRoot.exists() && !modelRoot.mkdirs()) {
                    throw IllegalStateException("Unable to create model directory")
                }
                if (tempFile.exists() && !tempFile.delete()) {
                    throw IllegalStateException("Unable to remove stale partial download")
                }
                val url = URL(model.downloadUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                try {
                    conn.connect()
                    if (conn.responseCode !in 200..299) {
                        throw IllegalStateException("Model download failed with HTTP ${conn.responseCode}")
                    }

                    val responseBytes = conn.contentLengthLong.takeIf { it >= 0 }
                    val expectedBytes = model.expectedBytes
                    if (expectedBytes != null && responseBytes != null && responseBytes != expectedBytes) {
                        throw IllegalStateException("Model size header mismatch: expected $expectedBytes bytes, received $responseBytes")
                    }
                    val progressTotal = expectedBytes ?: responseBytes ?: 1L
                    val digest = MessageDigest.getInstance("SHA-256")

                    conn.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var downloaded = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                digest.update(buffer, 0, bytesRead)
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                val progress = (downloaded.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)
                                _downloadStates.value = _downloadStates.value + (model.id to DownloadState.Downloading(progress, downloaded, progressTotal))
                            }
                        }
                    }

                    if (tempFile.length() == 0L) throw IllegalStateException("Downloaded file was empty")
                    if (expectedBytes != null && tempFile.length() != expectedBytes) {
                        throw IllegalStateException("Model size mismatch: expected $expectedBytes bytes, received ${tempFile.length()}")
                    }

                    val calculatedSha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!calculatedSha.equals(model.sha256, ignoreCase = true)) {
                        throw IllegalStateException("Model SHA-256 mismatch")
                    }

                    Files.move(
                        tempFile.toPath(),
                        finalFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                    val metadata = InstalledLiteRtModelMetadata(
                        id = model.id,
                        displayName = model.displayName,
                        sourceUrl = model.downloadUrl,
                        sha256 = model.sha256.lowercase(),
                        bytes = finalFile.length(),
                        contextWindowTokens = model.contextWindowTokens,
                        supportsVision = model.supportsVision,
                        supportsTools = model.supportsTools
                    )
                    File(modelRoot, "${model.id}.halo-model.json").writeText(metadata.toJson())
                    _downloadStates.value = _downloadStates.value + (model.id to DownloadState.Downloaded)
                    refresh(ModelInventoryRefreshReason.DOWNLOAD_COMPLETED)
                } finally {
                    conn.disconnect()
                }
            }.onFailure { err ->
                if (tempFile.exists()) tempFile.delete()
                _downloadStates.value = _downloadStates.value + (model.id to DownloadState.Failed(err.message ?: "Download failed"))
            }
        }
    }

    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        if (residentModelIdProvider() == modelId) return@withContext false
        val descriptor = resolveModel(modelId) ?: return@withContext false
        val modelFile = File(descriptor.path)
        val sidecarFile = File(modelRoot, "$modelId.halo-model.json")
        val cacheFolder = File(context.filesDir, "litertlm_cache")

        var deleted = false
        if (modelFile.exists()) {
            deleted = modelFile.delete()
        }
        if (sidecarFile.exists()) sidecarFile.delete()

        if (cacheFolder.exists()) {
            val cachePrefix = "${modelId.replace('/', '_')}-"
            cacheFolder.listFiles { f -> f.isDirectory && f.name.startsWith(cachePrefix) }?.forEach {
                it.deleteRecursively()
            }
        }

        refresh(ModelInventoryRefreshReason.INVALIDATED)
        deleted
    }

    suspend fun deleteLegacyGgufFiles(): Long = withContext(Dispatchers.IO) {
        var deletedBytes = 0L
        _legacyGgufFiles.value.forEach { file ->
            if (file.exists()) {
                deletedBytes += file.length()
                file.delete()
            }
        }
        _legacyGgufFiles.value = emptyList()
        deletedBytes
    }

    fun getAllCatalogModels(): List<LiteRtCatalogModel> = LiteRtModelCatalog.BUILT_IN_MODELS
}
