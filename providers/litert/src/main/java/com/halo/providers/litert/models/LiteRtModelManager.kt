package com.halo.providers.litert.models

import android.content.Context
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

                // Find legacy GGUF files for optional user cleanup
                val ggufs = modelRoot.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }?.toList().orEmpty()
                _legacyGgufFiles.value = ggufs

                // Scan for valid LiteRT models (.litertlm)
                val litertFiles = modelRoot.listFiles { f -> f.isFile && f.name.endsWith(".litertlm", ignoreCase = true) && f.length() > 0L }?.toList().orEmpty()

                val descriptors = litertFiles.map { file ->
                    val catalogMatch = LiteRtModelCatalog.BUILT_IN_MODELS.firstOrNull { it.id == file.nameWithoutExtension || file.name.startsWith(it.id) }
                    LocalModelDescriptor(
                        id = catalogMatch?.id ?: file.nameWithoutExtension,
                        displayName = catalogMatch?.displayName ?: file.nameWithoutExtension.replace('_', ' ').replace('-', ' '),
                        path = file.absolutePath,
                        bytes = file.length(),
                        modifiedAt = file.lastModified(),
                        sha256 = catalogMatch?.sha256,
                        contextWindowTokens = catalogMatch?.contextWindowTokens ?: 4096,
                        supportsVision = catalogMatch?.supportsVision ?: false,
                        supportsTools = catalogMatch?.supportsTools ?: false,
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
