package com.halo.app.ui.screens.settings

import com.halo.app.data.models.ModelRuntimeProfileEntity
import com.halo.app.data.models.ModelRuntimeProfileRepository
import com.halo.sdk.provider.ToolCallProtocol

internal suspend fun markProbeProvenance(repository: ModelRuntimeProfileRepository?,
    profile: ModelRuntimeProfileEntity, protocol: ToolCallProtocol, allPhasesPassed: Boolean) {
    if (!allPhasesPassed || !profile.isStable) return
    when (protocol) {
        ToolCallProtocol.NATIVE -> repository?.markNativeToolCallingProven(profile)
        ToolCallProtocol.HALO_PROMPT -> repository?.markPromptToolLoopProven(profile)
        else -> return
    }
    repository?.markAgenticContinuationProven(profile)
}
