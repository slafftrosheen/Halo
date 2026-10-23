package com.halo.app.ai

import com.halo.engines.ai.AiSelectionRepository
import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.sdk.runtime.Storage

/** Read-only projection of existing selection owners, used only to protect rollback targets. */
object LocalModelAcquisitionProtection {
    fun selectedIds(storage: Storage): Set<String> = buildSet {
        val selections = AiSelectionRepository(storage)
        val current = selections.readPersistedSelection()
        if (current.providerId == DefaultAiProviderRouter.LITERT_PROVIDER_ID) current.modelId?.let(::add)
        selections.readRoleBindings().values.filter { it.providerId == DefaultAiProviderRouter.LITERT_PROVIDER_ID }
            .mapNotNull { it.modelId }.forEach(::add)
        storage.getString(LocalIntentModelRouter.MODEL_KEY)?.takeIf { it.isNotBlank() }?.let(::add)
        WorkloadPreferences(storage).selectedEmbedding()?.modelId?.let(::add)
    }
}
