package com.halo.providers.llamacpp

import kotlinx.coroutines.flow.StateFlow

enum class ModelInventoryRefreshReason { STARTUP, USER_REQUEST, DOWNLOAD_COMPLETED, INVALIDATED }

data class FileFingerprint(val path: String, val length: Long, val modifiedAt: Long)

data class LocalModelDescriptor(
    val id: String,
    val displayName: String,
    val path: String,
    val bytes: Long,
    val modifiedAt: Long,
    val ggufMetadata: GgufModelMetadata?,
    val valid: Boolean
)

sealed interface LocalModelInventoryState {
    data object Loading : LocalModelInventoryState
    data class Ready(val models: List<LocalModelDescriptor>, val scannedAt: Long) : LocalModelInventoryState
    data class Failed(val message: String) : LocalModelInventoryState
}

interface LocalModelInventoryRepository {
    val inventoryState: StateFlow<LocalModelInventoryState>
    suspend fun refresh(reason: ModelInventoryRefreshReason)
    suspend fun resolveModel(modelId: String): LocalModelDescriptor?
    fun invalidate(reason: String)
}

class GgufMetadataCache {
    private val entries = mutableMapOf<FileFingerprint, GgufModelMetadata?>()

    @Synchronized
    fun getOrRead(fingerprint: FileFingerprint, reader: () -> GgufModelMetadata?): GgufModelMetadata? {
        if (entries.containsKey(fingerprint)) return entries[fingerprint]
        entries.keys.removeAll { it.path == fingerprint.path && it != fingerprint }
        return reader().also { entries[fingerprint] = it }
    }

    @Synchronized
    fun invalidate(path: String? = null) {
        if (path == null) entries.clear() else entries.keys.removeAll { it.path == path }
    }
}
