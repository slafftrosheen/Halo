package com.halo.providers.llamacpp

import android.content.Context
import com.halo.sdk.provider.LocalInferenceReadiness
import com.halo.sdk.provider.ReadinessBlocker
import com.halo.sdk.runtime.NativeMemoryPressure
import com.halo.sdk.runtime.NativeResourceCoordinator
import com.halo.sdk.runtime.NativeResourceKind
import com.halo.sdk.runtime.NativeResourceState

/** Cheap, side-effect-free readiness gate. It never constructs or loads a native runtime. */
class LocalInferenceReadinessService(
    private val context: Context,
    private val models: ModelDownloadManager,
    private val resources: NativeResourceCoordinator? = null,
) {
    fun evaluate(
        providerSelected: Boolean,
        ownerId: String? = null,
    ): LocalInferenceReadiness {
        val blockers = linkedSetOf<ReadinessBlocker>()
        if (!providerSelected) blockers += ReadinessBlocker.PROVIDER_NOT_SELECTED

        val modelId = models.activeModelId.value
        if (modelId == null) blockers += ReadinessBlocker.NO_MODEL_SELECTED
        val downloadState = modelId?.let { models.downloadStates.value[it] }
        if (downloadState == DownloadState.Downloading) blockers += ReadinessBlocker.DOWNLOAD_INCOMPLETE
        if (downloadState == DownloadState.NotDownloaded || downloadState == DownloadState.Failed) {
            blockers += ReadinessBlocker.MODEL_FILE_MISSING
        }

        val file = modelId?.let(models::getModelFile)
        val selectedModelExists = file?.let { it.exists() && it.isFile && it.length() > 0L } == true
        if (modelId != null && !selectedModelExists && downloadState != DownloadState.Downloading) {
            blockers += ReadinessBlocker.MODEL_FILE_MISSING
        }
        val formatSupported = file?.extension?.let { extension ->
            extension.equals("gguf", ignoreCase = true) ||
                extension.equals("litertlm", ignoreCase = true)
        } == true
        if (selectedModelExists && !formatSupported) blockers += ReadinessBlocker.MODEL_UNSUPPORTED

        val memory = AndroidMemorySnapshot.read(context)
        val memoryAllowed = if (file != null && selectedModelExists) {
            ModelMemoryAdmission.evaluate(
                file.length(),
                ModelMemoryAdmission.Snapshot(memory.totalBytes, memory.availableBytes, memory.lowMemory),
            ) is ModelMemoryAdmission.Decision.Allow
        } else {
            false
        }
        if (selectedModelExists && !memoryAllowed) blockers += ReadinessBlocker.LOW_MEMORY

        val budget = resources?.budget?.value
        val conflictingOwner = resources?.snapshot?.value?.any { snapshot ->
            snapshot.ownerId != ownerId &&
                snapshot.state in setOf(NativeResourceState.LOADING, NativeResourceState.RESIDENT, NativeResourceState.IN_USE) &&
                (snapshot.kind == NativeResourceKind.HEAVY_LLM || budget?.allowConcurrentHeavyweights == false)
        } == true
        val resourceAvailable = budget?.pressure != NativeMemoryPressure.CRITICAL && !conflictingOwner
        if (!resourceAvailable) blockers += ReadinessBlocker.RESOURCE_BUSY

        return LocalInferenceReadiness(
            ready = blockers.isEmpty(),
            selectedModelExists = selectedModelExists,
            formatSupported = formatSupported,
            memoryAllowed = memoryAllowed,
            resourceAvailable = resourceAvailable,
            providerSelected = providerSelected,
            blockers = blockers.toList(),
        )
    }
}
