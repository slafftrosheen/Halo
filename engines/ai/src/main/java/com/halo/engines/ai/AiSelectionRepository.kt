package com.halo.engines.ai

import com.halo.sdk.runtime.Storage

/** The sole persisted authority for the explicitly selected inference target. */
data class PersistedAiSelection(
    val providerId: String?,
    val modelId: String?,
)

class AiSelectionRepository(
    private val storage: Storage,
) {
    fun read(validProviderIds: Set<String>, defaultProviderId: String?): PersistedAiSelection {
        var providerId = storage.getString(PROVIDER_KEY)?.trim()?.ifBlank { null }
        if (providerId == DefaultAiProviderRouter.LEGACY_LLAMACPP_PROVIDER_ID) {
            providerId = DefaultAiProviderRouter.LITERT_PROVIDER_ID
        }
        if (providerId !in validProviderIds) providerId = defaultProviderId

        var modelId = storage.getString(MODEL_KEY)?.trim()?.ifBlank { null }
        if (modelId == null && providerId != null) {
            // Read old provider-specific keys only at this migration boundary. They are never
            // written again and never act as an independent selection authority.
            modelId = legacyModelKey(providerId)
                ?.let { key -> storage.getString(key) }
                ?.trim()
                ?.ifBlank { null }
        }
        val selection = PersistedAiSelection(providerId, modelId)
        write(selection)
        return selection
    }

    fun write(selection: PersistedAiSelection) {
        storage.putString(PROVIDER_KEY, selection.providerId)
        storage.putString(MODEL_KEY, selection.modelId)
    }

    private fun legacyModelKey(providerId: String): String? = when (providerId) {
        "openrouter" -> "openrouter_model"
        "provider.ollama" -> "ollama_model"
        DefaultAiProviderRouter.LITERT_PROVIDER_ID -> "litert_model"
        else -> null
    }

    companion object {
        const val PROVIDER_KEY = "active_ai_provider"
        const val MODEL_KEY = "active_ai_model"
    }
}
