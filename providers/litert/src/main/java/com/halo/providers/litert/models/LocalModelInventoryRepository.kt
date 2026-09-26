package com.halo.providers.litert.models

import kotlinx.coroutines.flow.StateFlow

enum class ModelInventoryRefreshReason { STARTUP, USER_REQUEST, DOWNLOAD_COMPLETED, INVALIDATED }

data class LocalModelDescriptor(
    val id: String,
    val displayName: String,
    val path: String,
    val bytes: Long,
    val modifiedAt: Long,
    val sha256: String?,
    val contextWindowTokens: Int,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val valid: Boolean,
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
