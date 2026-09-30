package com.halo.runtime.cognition.workload

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.context.ContextAcquisitionPlan
import com.halo.runtime.cognition.context.ContextAffinity
import com.halo.runtime.cognition.context.ContextKind
import com.halo.runtime.cognition.context.ContextNeed
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.AiFeatureRequirement
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiWorkloadKind
import com.halo.sdk.provider.AiWorkloadSpec
import com.halo.sdk.runtime.ContextRequirement
import com.halo.sdk.runtime.ObservationOrigin
import com.halo.sdk.runtime.VisualFreshness

data class WorkloadPlanningRequest(
    val request: AiRequest,
    val interactionId: InteractionId,
    val origin: ObservationOrigin? = null,
    val metadata: Map<String, String> = emptyMap(),
    val contextPolicy: AiContextPolicy = AiContextPolicy.CONSERVATIVE,
)

data class CognitiveWorkloadPlan(
    val inference: AiWorkloadSpec,
    val context: ContextAcquisitionPlan,
)

interface AiWorkloadPlanner {
    fun plan(request: WorkloadPlanningRequest): CognitiveWorkloadPlan
}

/**
 * Deterministic workload hints. This planner never calls a model and never
 * chooses a provider. It only expresses what the current interaction requires.
 */
class DefaultAiWorkloadPlanner : AiWorkloadPlanner {

    override fun plan(request: WorkloadPlanningRequest): CognitiveWorkloadPlan {
        val text = request.request.text.orEmpty().trim().lowercase()
        val hasExplicitImage = request.request.image != null

        val hasBoundScreen = request.metadata["hasScreenContext"]?.toBooleanStrictOrNull() == true
        val hasBoundAssistantImage = request.metadata["hasAssistantImage"]?.toBooleanStrictOrNull() == true
        val invocationSource = request.metadata["invocationSource"].orEmpty()
        val typedVisualFreshness = request.metadata["visualContextRequirement"]?.let { raw ->
            VisualFreshness.entries.firstOrNull { it.name == raw }
        }

        val isGlassesOrigin =
            request.origin?.source == ObservationSource.HeyCyanGlasses ||
                request.metadata["inputSource"]?.contains("HeyCyan", ignoreCase = true) == true ||
                request.metadata["origin"]?.contains("glasses", ignoreCase = true) == true

        val isAssistantOrigin =
            request.origin?.source == ObservationSource.AndroidAssist ||
                hasBoundScreen ||
                hasBoundAssistantImage ||
                invocationSource.equals("ANDROID_ASSISTANT", ignoreCase = true) ||
                request.metadata["inputSource"]?.contains("AndroidAssist", ignoreCase = true) == true

        val visualPhrases = listOf(
            "what am i looking at",
            "what do you see",
            "describe what i see",
            "describe this image",
            "describe this photo",
            "this image",
            "this photo",
            "what does this look like",
            "what does this sign say",
            "read this sign",
            "which bottle is this",
        )
        val screenPhrases = listOf(
            "this page",
            "this screen",
            "what's on screen",
            "what is on screen",
            "summarize this page",
            "summarise this page",
            "summarize this screen",
            "summarise this screen",
            "explain this page",
            "explain this screen",
            "this button",
            "this icon",
        )

        val asksScreenVisual = isAssistantOrigin && (
            (text.contains("icon") || text.contains("button")) &&
                (text.contains("look") || text.contains("describe") || text.contains("see"))
            )

        val asksVisual = typedVisualFreshness != null || hasExplicitImage || hasBoundAssistantImage || visualPhrases.any(text::contains) ||
            asksScreenVisual ||
            (isGlassesOrigin && text.trimEnd('.', '?', '!') in setOf("what is this", "what's this", "what is that", "what's that", "read this"))

        val asksScreen = isAssistantOrigin && screenPhrases.any(text::contains)

        val requiredCaps = linkedSetOf(AiCapability.TEXT)
        val needs = mutableListOf<ContextNeed>()
        var kind = AiWorkloadKind.CONVERSATION

        if (asksScreen) {
            kind = AiWorkloadKind.SCREEN_CONTEXT
            needs += ContextNeed(
                kind = ContextKind.SCREEN_TEXT,
                requirement = ContextRequirement.REQUIRED,
                affinity = ContextAffinity.EXACT_INTERACTION,
            )
        }

        if (asksVisual) {
            kind = AiWorkloadKind.VISUAL_UNDERSTANDING
            requiredCaps += AiCapability.IMAGE

            // An image already embedded in AiRequest needs no second acquisition.
            if (!hasExplicitImage) {
                val affinity = when {
                    isGlassesOrigin -> ContextAffinity.ORIGIN_DEVICE
                    isAssistantOrigin -> ContextAffinity.EXACT_INTERACTION
                    else -> ContextAffinity.EXACT_INTERACTION
                }
                needs += ContextNeed(
                    kind = ContextKind.VISUAL_IMAGE,
                    requirement = ContextRequirement.REQUIRED,
                    affinity = affinity,
                    visualFreshness = if (request.metadata["explicitImageReference"] == "true") VisualFreshness.REFERENCE else typedVisualFreshness ?: VisualFreshness.CURRENT_VIEW,
                )
            }
        } else if (asksScreen && (text.contains("icon") || text.contains("button"))) {
            // Non-appearance questions can use structured screen context alone;
            // a screenshot is optional enrichment when the target can consume it.
            needs += ContextNeed(
                kind = ContextKind.VISUAL_IMAGE,
                requirement = ContextRequirement.OPTIONAL,
                affinity = ContextAffinity.EXACT_INTERACTION,
                visualFreshness = VisualFreshness.CURRENT_VIEW,
            )
        }

        request.metadata["workspaceObjectId"]?.takeIf(String::isNotBlank)?.let {
            needs += ContextNeed(
                kind = ContextKind.WORKSPACE_REFERENCE,
                requirement = ContextRequirement.REQUIRED,
                affinity = ContextAffinity.EXPLICIT_REFERENCE,
            )
            if (kind == AiWorkloadKind.CONVERSATION) kind = AiWorkloadKind.DOCUMENT_CONTEXT
        }

        val requiredKinds = needs.filter { it.requirement == ContextRequirement.REQUIRED }
            .map { it.kind }
            .toSet()

        val requiredPrivateContextAllowsRemote = when {
            hasExplicitImage && !request.contextPolicy.allowCloudImages -> false
            ContextKind.VISUAL_IMAGE in requiredKinds && !request.contextPolicy.allowCloudImages -> false
            ContextKind.SCREEN_TEXT in requiredKinds && !request.contextPolicy.allowCloudScreenContext -> false
            ContextKind.WORKSPACE_REFERENCE in requiredKinds && !request.contextPolicy.allowCloudWorkspace -> false
            else -> true
        }

        return CognitiveWorkloadPlan(
            inference = AiWorkloadSpec(
                kind = kind,
                requiredCapabilities = requiredCaps,
                tools = when (request.metadata["requiresTools"]?.lowercase()) {
                    "true", "required" -> AiFeatureRequirement.REQUIRED
                    "false", "none" -> AiFeatureRequirement.NOT_NEEDED
                    else -> AiFeatureRequirement.OPTIONAL
                },
                streaming = AiFeatureRequirement.OPTIONAL,
                allowRemote = requiredPrivateContextAllowsRemote,
            ),
            context = ContextAcquisitionPlan(
                interactionId = request.interactionId,
                needs = needs.distinct(),
            ),
        )
    }
}
