package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.runtime.Storage

class LocalModelPreloadCoordinator(
    private val session: LocalModelSession,
    private val inventoryRepository: LocalModelInventoryRepository,
    private val storage: Storage,
) {

    suspend fun preloadIfNeeded(): Boolean {
        val selectedProvider = storage.getString("active_ai_provider") ?: return false
        if (selectedProvider != "provider.litert.local") return false

        val selectedModelId = storage.getString("active_ai_model")
            ?: return false

        val descriptor = inventoryRepository.resolveModel(selectedModelId) ?: return false

        if (session.isLoaded() && session.loadedModelId == descriptor.id) return true

        val config = RuntimeLoadConfig(
            contextSize = descriptor.contextWindowTokens,
            computeBackend = LocalComputeBackend.AUTO,
            useModelDefaultContext = descriptor.useModelDefaultContext,
        )

        val result = session.loadModel(
            modelId = descriptor.id,
            modelPath = descriptor.path,
            config = config
        )

        return result.isSuccess
    }
}
