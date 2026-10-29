package com.halo.app.ui.screens.settings

import com.halo.sdk.provider.AiModelRef
import com.halo.sdk.provider.AiModelRole
import com.halo.sdk.provider.AiRoleBinding
import com.halo.sdk.provider.AiRoleSelection

/** Existing persisted selection only; an automatic candidate is never a fixed chosen model. */
internal data class ModelBrowserSelectionPresentation(val target: AiModelRef?, val marker: String?, val mode: AiRoleSelection?)

internal fun modelBrowserSelectionPresentation(role: AiModelRole?, binding: AiRoleBinding?,
    conversation: AiModelRef?): ModelBrowserSelectionPresentation {
    fun AiModelRef.valid() = providerId.isNotBlank() && modelId.isNotBlank()
    val default = conversation?.takeIf { it.valid() }
    if (role == null || role == AiModelRole.CHAT)
        return ModelBrowserSelectionPresentation(default, "Conversation default", null)
    val current = binding?.takeIf { it.role == role }
        ?: return ModelBrowserSelectionPresentation(null, null, null)
    return when (current.selection) {
        AiRoleSelection.EXPLICIT -> ModelBrowserSelectionPresentation(
            AiModelRef(current.providerId.orEmpty(), current.modelId.orEmpty()).takeIf { it.valid() }, "Selected", current.selection)
        AiRoleSelection.INHERIT_DEFAULT -> ModelBrowserSelectionPresentation(default, "Uses conversation default", current.selection)
        AiRoleSelection.AUTOMATIC, AiRoleSelection.DISABLED -> ModelBrowserSelectionPresentation(null, null, current.selection)
    }
}
