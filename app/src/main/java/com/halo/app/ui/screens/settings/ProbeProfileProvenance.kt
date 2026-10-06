package com.halo.app.ui.screens.settings

import com.halo.app.data.models.ModelRuntimeProfileEntity
import com.halo.app.data.models.ModelRuntimeProfileRepository
import com.halo.sdk.provider.ToolCallProtocol

internal suspend fun markProbeProvenance(repository: ModelRuntimeProfileRepository?,
    profile: ModelRuntimeProfileEntity, protocol: ToolCallProtocol, allPhasesPassed: Boolean): Boolean {
    if (!allPhasesPassed || !profile.isStable || repository == null) return false
    val protocolUpdates = when (protocol) {
        ToolCallProtocol.NATIVE -> repository.markNativeToolCallingProven(profile)
        ToolCallProtocol.HALO_PROMPT -> repository.markPromptToolLoopProven(profile)
        else -> return false
    }
    val continuationUpdates = repository.markAgenticContinuationProven(profile)
    return protocolUpdates > 0 && continuationUpdates > 0
}

/** Called for each physically validated suite, before a matrix advances to its next model. */
internal suspend fun retainRoleQualification(
    repository: ModelRuntimeProfileRepository?, before: ModelRuntimeProfileEntity?,
    row: com.halo.providers.litert.runtime.LocalModelRoleQualification,
): com.halo.providers.litert.runtime.LocalModelRoleQualification {
    val agentic = row.agentic ?: return row
    if (row.failure != null || agentic.verdict != com.halo.providers.litert.runtime.LocalRoleProbeVerdict.PASS ||
        before == null || repository == null) return row
    val retained = try {
        val current = repository.byId(before.id)
        val matches = current != null && current.id == before.id && current.isStable &&
            current.deviceFingerprint == before.deviceFingerprint && current.runtime == before.runtime &&
            current.runtimeVersion == before.runtimeVersion && current.compatibilityEpoch == before.compatibilityEpoch &&
            current.artifactId == row.modelId && current.artifactFingerprint == before.artifactFingerprint &&
            current.artifactFingerprint == row.artifactFingerprint && current.backend == row.backend?.name &&
            current.contextSize == row.contextTokens && current.threads == row.threads && current.gpuLayers == row.gpuLayers
        if (matches) markProbeProvenance(repository, current!!, agentic.protocol, true) else false
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
    catch (_: Exception) { false }
    return row.copy(agentic = agentic.copy(provenanceRetained = retained))
}
