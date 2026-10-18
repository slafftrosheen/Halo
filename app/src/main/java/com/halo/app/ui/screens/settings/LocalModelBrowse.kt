package com.halo.app.ui.screens.settings

import com.halo.providers.litert.models.LiteRtCatalogModel
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LiteRtArtifactTask
import com.halo.providers.litert.models.LiteRtGenerationAdmission

internal enum class LocalModelUse(val label: String) {
    ALL("All models"), TEXT("Text only"), VISION("Image understanding"), AUDIO("Audio understanding"), EMBEDDINGS("Embeddings / search"), TOOLS("Tool candidates");
    fun matches(vision: Boolean, audio: Boolean, tools: Boolean): Boolean = when (this) {
        ALL -> true
        EMBEDDINGS -> false
        TEXT -> !vision && !audio
        VISION -> vision
        AUDIO -> audio
        TOOLS -> tools
    }
}

internal fun localGenerationIsCurrent(descriptor: LocalModelDescriptor?, proven: Boolean): Boolean =
    descriptor?.valid == true && proven

internal fun browseLocalModels(models: List<LiteRtCatalogModel>, query: String, use: LocalModelUse): List<LiteRtCatalogModel> =
    models.filter { model ->
        (if (use == LocalModelUse.EMBEDDINGS) model.task == LiteRtArtifactTask.EMBEDDING
         else if (use == LocalModelUse.ALL) true
         else LiteRtGenerationAdmission.permitted(model, model.filename) &&
             use.matches(model.supportsVision, model.supportsAudio, model.supportsTools || model.toolCallPreference != com.halo.providers.litert.models.LiteRtToolCallPreference.NONE)) &&
        (query.isBlank() || listOf(model.displayName, model.id, model.repoId, model.filename)
            .any { it.contains(query.trim(), true) })
    }

internal enum class LocalInventoryFilter(val label: String) {
    ALL("All files and downloads"), ON_DEVICE("On this phone"), AVAILABLE("Available to download"),
    TRANSFERRING("In progress"), ATTENTION("Needs attention");
    fun matches(descriptor: LocalModelDescriptor?, state: com.halo.providers.litert.models.DownloadState): Boolean {
        val active = state is com.halo.providers.litert.models.DownloadState.Queued ||
            state is com.halo.providers.litert.models.DownloadState.Preparing ||
            state is com.halo.providers.litert.models.DownloadState.Downloading ||
            state is com.halo.providers.litert.models.DownloadState.Verifying
        return when (this) {
            ALL -> true
            ON_DEVICE -> descriptor != null
            AVAILABLE -> descriptor == null && !active && state !is com.halo.providers.litert.models.DownloadState.Paused
            TRANSFERRING -> active || state is com.halo.providers.litert.models.DownloadState.Paused
            ATTENTION -> descriptor?.valid == false || state is com.halo.providers.litert.models.DownloadState.Failed
        }
    }
}
