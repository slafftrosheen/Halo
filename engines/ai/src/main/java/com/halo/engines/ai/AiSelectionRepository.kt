package com.halo.engines.ai

import com.halo.sdk.runtime.Storage

/** The sole persisted authority for the explicitly selected inference target. */
data class PersistedAiSelection(
    val providerId: String?,
    val modelId: String?,
    val policy: com.halo.sdk.provider.AiRoutingPolicy = com.halo.sdk.provider.AiRoutingPolicy.AUTOMATIC,
)

class AiSelectionRepository(
    private val storage: Storage,
) {
    fun readPersistedSelection(): PersistedAiSelection {
        val providerId = storage.getString(PROVIDER_KEY)?.trim()?.ifBlank { null }
        val modelId = storage.getString(MODEL_KEY)?.trim()?.ifBlank { null }
        val policy = storage.getString(POLICY_KEY)?.let {
            runCatching { enumValueOf<com.halo.sdk.provider.AiRoutingPolicy>(it) }.getOrNull()
        } ?: com.halo.sdk.provider.AiRoutingPolicy.AUTOMATIC
        return PersistedAiSelection(providerId, modelId, policy)
    }

    fun migrateLegacySelectionIfNeeded(): PersistedAiSelection {
        val stored = readPersistedSelection()
        val migrationPending = !storage.getBoolean(MIGRATION_COMPLETE_KEY, false)
        if (!migrationPending) return stored
        val migratedProvider = if (stored.providerId == DefaultAiProviderRouter.LEGACY_LLAMACPP_PROVIDER_ID) {
            DefaultAiProviderRouter.LITERT_PROVIDER_ID
        } else stored.providerId
        val migratedModel = stored.modelId ?: migratedProvider
            ?.let(::legacyModelKey)
            ?.let { key -> storage.getString(key) }
            ?.trim()
            ?.ifBlank { null }
        val migrated = PersistedAiSelection(migratedProvider, migratedModel, stored.policy)
        if (migrated != stored) {
            writeExplicitSelection(migrated)
        }
        return migrated
    }

    fun writeExplicitSelection(selection: PersistedAiSelection) {
        storage.putBatch(
            strings = mapOf(
                PROVIDER_KEY to selection.providerId,
                MODEL_KEY to selection.modelId,
                POLICY_KEY to selection.policy.name,
            ),
            booleans = mapOf(MIGRATION_COMPLETE_KEY to true),
        )
    }

    @Deprecated("Use readPersistedSelection/migrateLegacySelectionIfNeeded")
    fun read(validProviderIds: Set<String>, defaultProviderId: String?): PersistedAiSelection {
        val stored = migrateLegacySelectionIfNeeded()
        return if (stored.providerId == null) stored.copy(providerId = defaultProviderId) else stored
    }

    @Deprecated("Use writeExplicitSelection")
    fun write(selection: PersistedAiSelection) = writeExplicitSelection(selection)

    private fun legacyModelKey(providerId: String): String? = when (providerId) {
        "openrouter" -> "openrouter_model"
        "provider.ollama" -> "ollama_model"
        DefaultAiProviderRouter.LITERT_PROVIDER_ID -> "litert_model"
        else -> null
    }

    companion object {
        const val PROVIDER_KEY = "active_ai_provider"
        const val MODEL_KEY = "active_ai_model"
        const val POLICY_KEY = "ai_routing_policy"
        const val MIGRATION_COMPLETE_KEY = "active_ai_selection_migrated_v1"
    }
}
