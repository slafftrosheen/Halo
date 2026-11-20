package com.halo.app.ui.onboarding

import com.halo.app.ai.GenerationProof
import com.halo.app.ai.ProviderConnectionState
import com.halo.app.ai.ProviderControlRow

/** The shared owner invalidates proof on configuration/model changes, including tests run in Settings.
 * The resolved model may differ from the requested alias; it is not a selection identity.
 */
internal fun setupAnswerVerified(row: ProviderControlRow?): Boolean =
    row != null && row.selected && row.configured && !row.lastSelectedModelId.isNullOrBlank() &&
        row.generation == GenerationProof.PROVEN &&
        row.connection in setOf(ProviderConnectionState.READY, ProviderConnectionState.CONFIGURED)
