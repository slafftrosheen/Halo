package com.halo.runtime.cognition.workload

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.context.*
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.ObservationOrigin
import com.halo.sdk.runtime.VisualFreshness

data class WorkloadPlanningRequest(
    val request: AiRequest,
    val interactionId: InteractionId,
    val origin: ObservationOrigin? = null,
    val metadata: Map<String, String> = emptyMap(),
)

data class CognitiveWorkloadPlan(
    val inference: AiWorkloadSpec,
    val context: ContextAcquisitionPlan,
)

interface AiWorkloadPlanner {
    fun plan(request: WorkloadPlanningRequest): CognitiveWorkloadPlan
}

class DefaultAiWorkloadPlanner : AiWorkloadPlanner {

    override fun plan(request: WorkloadPlanningRequest): CognitiveWorkloadPlan {
        val text = request.request.text.orEmpty().lowercase()
        val hasExplicitImage = request.request.image != null
        val isGlassesOrigin = request.origin?.source == ObservationSource.HeyCyanGlasses ||
            request.metadata["origin"]?.contains("glasses", ignoreCase = true) == true

        val isAssistantOrigin = request.origin?.source == ObservationSource.AndroidAssist ||
            request.metadata["origin"]?.contains("assistant", ignoreCase = true) == true

        val asksVisual = hasExplicitImage || text.contains("looking at") || text.contains("what do you see") ||
            text.contains("describe what i see") || text.contains("this image") || text.contains("this photo")

        val asksScreen = isAssistantOrigin && (text.contains("this page") || text.contains("this screen") ||
            text.contains("this button") || text.contains("what's on screen"))

        val requiredCaps = mutableSetOf(AiCapability.TEXT)
        val needs = mutableListOf<ContextNeed>()

        var kind = AiWorkloadKind.CONVERSATION

        if (asksVisual) {
            kind = AiWorkloadKind.VISUAL_UNDERSTANDING
            requiredCaps.add(AiCapability.IMAGE)

            val affinity = when {
                hasExplicitImage -> ContextAffinity.EXPLICIT_REFERENCE
                isGlassesOrigin -> ContextAffinity.ORIGIN_DEVICE
                else -> ContextAffinity.EXACT_INTERACTION
            }
            val freshness = if (hasExplicitImage) VisualFreshness.REFERENCE else VisualFreshness.CURRENT_VIEW

            needs.add(
                ContextNeed(
                    kind = ContextKind.VISUAL_IMAGE,
                    requirement = ContextRequirement.REQUIRED,
                    affinity = affinity,
                    visualFreshness = freshness,
                )
            )
        }

        if (asksScreen) {
            kind = AiWorkloadKind.SCREEN_CONTEXT
            needs.add(
                ContextNeed(
                    kind = ContextKind.SCREEN_TEXT,
                    requirement = ContextRequirement.REQUIRED,
                    affinity = ContextAffinity.EXACT_INTERACTION,
                )
            )
        }

        val contextPlan = ContextAcquisitionPlan(
            interactionId = request.interactionId,
            needs = needs,
        )

        val inferenceSpec = AiWorkloadSpec(
            kind = kind,
            requiredCapabilities = requiredCaps,
            tools = AiFeatureRequirement.OPTIONAL,
            streaming = AiFeatureRequirement.OPTIONAL,
            allowRemote = true,
        )

        return CognitiveWorkloadPlan(
            inference = inferenceSpec,
            context = contextPlan,
        )
    }
}
