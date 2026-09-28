package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.LocalModelInventoryRepository
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.RuntimeLoadConfig
import com.halo.sdk.runtime.Storage

/**
 * Warms the *selected* model into residency.
 *
 * Preload may make a model RESIDENT but must never: hold the compute.heavy lease, start a
 * generation, mark a model generation-proven, or block speech after engine initialization. Only
 * the selected/preferred model is ever considered — never every downloaded model.
 */
class LocalModelPreloadCoordinator(
    private val session: LocalModelSession,
    private val inventoryRepository: LocalModelInventoryRepository,
    private val storage: Storage,
    /** Resolves the model-scoped runtime profile; defaults to the descriptor's own values. */
    private val loadConfigFor: (com.halo.providers.litert.models.LocalModelDescriptor) -> RuntimeLoadConfig = { descriptor ->
        RuntimeLoadConfig(
            contextSize = descriptor.contextWindowTokens,
            computeBackend = LocalComputeBackend.AUTO,
            useModelDefaultContext = descriptor.useModelDefaultContext,
        )
    },
) {

    suspend fun preloadIfNeeded(): Boolean {
        val selectedProvider = storage.getString("active_ai_provider") ?: return false
        if (selectedProvider != "provider.litert.local") return false

        val selectedModelId = storage.getString("active_ai_model")
            ?: return false

        val descriptor = inventoryRepository.resolveModel(selectedModelId) ?: return false

        if (session.isLoaded() && session.loadedModelId == descriptor.id) return true

        val config = loadConfigFor(descriptor)

        val result = session.loadModel(
            modelId = descriptor.id,
            modelPath = descriptor.path,
            config = config
        )

        return result.isSuccess
    }
}
