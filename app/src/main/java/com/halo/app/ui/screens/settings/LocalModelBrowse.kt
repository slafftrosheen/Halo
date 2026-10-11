package com.halo.app.ui.screens.settings

import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LocalModelDescriptor

internal enum class LocalModelUse(val label: String) {
    ALL("All models"), TEXT("Text only"), VISION("Image understanding"), AUDIO("Audio understanding"), TOOLS("Tool candidates");
    fun matches(vision: Boolean, audio: Boolean, tools: Boolean): Boolean = when (this) {
        ALL -> true
        TEXT -> !vision && !audio
        VISION -> vision
        AUDIO -> audio
        TOOLS -> tools
    }
}

internal fun localGenerationIsCurrent(descriptor: LocalModelDescriptor?, proven: Boolean): Boolean =
    descriptor?.valid == true && proven

internal fun browseLocalModels(models: List<LiteRtCatalogModel>, query: String, use: LocalModelUse): List<LiteRtCatalogModel> =
    models.filter { model -> use.matches(model.supportsVision, model.supportsAudio, model.supportsTools) &&
        (query.isBlank() || listOf(model.displayName, model.id, model.repoId).any { it.contains(query.trim(), true) }) }
