package com.halo.engines.ai

import com.halo.sdk.provider.AiModelRole
import com.halo.sdk.provider.AiRoleBinding
import com.halo.sdk.provider.AiRoleSelection
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
        // A retired provider id is repaired on every start, not only while the marker is unset.
        // The marker exists so a provider-specific legacy model key cannot rewrite the canonical
        // selection on a later start; it must not be able to preserve an id the app no longer
        // ships. Without this, any start that ran before the legacy key was restored (backup
        // restore, an earlier process, a copied preference file) would leave a dead provider
        // selected forever and the marker would silently block the repair.
        val retiredProvider =
            stored.providerId == DefaultAiProviderRouter.LEGACY_LLAMACPP_PROVIDER_ID
        if (!migrationPending && !retiredProvider) return stored
        val migratedProvider = if (retiredProvider) {
            DefaultAiProviderRouter.LITERT_PROVIDER_ID
        } else {
            stored.providerId
        }
        // The legacy per-provider model key is only read once, while the migration is pending.
        val migratedModel = stored.modelId ?: if (migrationPending) {
            migratedProvider
                ?.let(::legacyModelKey)
                ?.let { key -> storage.getString(key) }
                ?.trim()
                ?.ifBlank { null }
        } else {
            null
        }
        val migrated = PersistedAiSelection(migratedProvider, migratedModel, stored.policy)
        // Mark the migration complete even when no legacy value existed, so a fresh install is not
        // permanently "migration pending".
        writeExplicitSelection(migrated)
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

    fun readRoleBindings(): Map<AiModelRole, AiRoleBinding> {
        val bindings = mutableMapOf<AiModelRole, AiRoleBinding>()
        AiModelRole.entries.forEach { role ->
            if (role == AiModelRole.CHAT) return@forEach // CHAT is handled by PersistedAiSelection
            
            val selectionRaw = storage.getString("role_${role.name}_selection")
            val selection = selectionRaw?.let { 
                runCatching { enumValueOf<AiRoleSelection>(it) }.getOrNull()
            } ?: if (role == AiModelRole.VISION) AiRoleSelection.AUTOMATIC else AiRoleSelection.INHERIT_DEFAULT
            
            val providerId = storage.getString("role_${role.name}_provider")?.trim()?.ifBlank { null }
            val modelId = storage.getString("role_${role.name}_model")?.trim()?.ifBlank { null }
            
            bindings[role] = AiRoleBinding(role, providerId, modelId, selection)
        }
        return bindings
    }

    fun writeRoleBinding(binding: AiRoleBinding) {
        storage.putBatch(
            strings = mapOf(
                "role_${binding.role.name}_selection" to binding.selection.name,
                "role_${binding.role.name}_provider" to binding.providerId,
                "role_${binding.role.name}_model" to binding.modelId
            )
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
