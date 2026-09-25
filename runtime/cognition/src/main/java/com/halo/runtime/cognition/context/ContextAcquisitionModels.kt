package com.halo.runtime.cognition.context

import com.halo.cognition.language.InteractionId
import com.halo.sdk.runtime.VisualFreshness

enum class ContextKind {
    SCREEN_TEXT,
    VISUAL_IMAGE,
    WORKSPACE_REFERENCE,
    DEVICE_STATE,
    RECENT_EVENTS,
}

enum class ContextAffinity {
    EXACT_INTERACTION,
    ORIGIN_DEVICE,
    EXPLICIT_REFERENCE,
    ANY_RECENT,
}

enum class ContextRequirement {
    REQUIRED,
    OPTIONAL,
    NONE,
}

data class ContextNeed(
    val kind: ContextKind,
    val requirement: ContextRequirement,
    val affinity: ContextAffinity = ContextAffinity.ANY_RECENT,
    val visualFreshness: VisualFreshness? = null,
)

data class ContextAcquisitionPlan(
    val interactionId: InteractionId,
    val needs: List<ContextNeed>,
)

data class ContextAcquisitionResult(
    val candidates: List<ContextCandidate>,
    val omitted: List<OmittedContextCandidate> = emptyList(),
    val unsatisfiedRequired: List<ContextNeed> = emptyList(),
)
