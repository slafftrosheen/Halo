package com.halo.runtime.cognition.tools

import com.halo.runtime.cognition.context.CognitiveOperatingMode
import com.halo.runtime.cognition.skills.SkillRegistry
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.runtime.skills.SkillId

enum class ToolExposureDenyReason {
    SKILL_DISABLED,
    SKILL_UNAVAILABLE,
    MODE_DISALLOWS_SIDE_EFFECT,
    WORKSPACE_CONTEXT_DISABLED,
    REMOTE_WORKSPACE_DISABLED,
    REMOTE_MEMORY_DISABLED,
    CAPABILITY_UNAVAILABLE,
    TOOL_UNAVAILABLE,
    POLICY_DENIED,
}

data class ToolExposureContext(
    val enabledSkillIds: Set<SkillId> = emptySet(),
    val policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE,
    val mode: CognitiveOperatingMode = CognitiveOperatingMode.AUTO,
    val isLocalTarget: Boolean = true,
    val activatedSkillIds: Set<SkillId> = emptySet(),
)

sealed interface ToolExposureDecision {
    data object Allow : ToolExposureDecision
    data class Deny(val reason: ToolExposureDenyReason) : ToolExposureDecision
}

fun interface ToolExposureGate {
    fun evaluate(
        descriptor: ToolDescriptor,
        context: ToolExposureContext,
    ): ToolExposureDecision
}

class DefaultToolExposureGate(
    private val skillRegistry: SkillRegistry? = null
) : ToolExposureGate {

    override fun evaluate(
        descriptor: ToolDescriptor,
        context: ToolExposureContext,
    ): ToolExposureDecision {
        val toolName = descriptor.name
        val skillId = skillRegistry?.skillIdForTool(toolName)

        // 1. Skill check (if tool belongs to a skill)
        if (skillId != null) {
            if (skillId !in context.enabledSkillIds && skillId !in context.activatedSkillIds) {
                return ToolExposureDecision.Deny(ToolExposureDenyReason.SKILL_DISABLED)
            }
        }

        // 2. Policy tool check
        if (!context.policy.includeAvailableTools) {
            return ToolExposureDecision.Deny(ToolExposureDenyReason.POLICY_DENIED)
        }

        // 3. Cloud privacy checks
        if (!context.isLocalTarget) {
            if ((descriptor.id.value == "halo_search_memory" || descriptor.id.value == "halo_propose_memory") && !context.policy.allowCloudMemories) {
                return ToolExposureDecision.Deny(ToolExposureDenyReason.REMOTE_MEMORY_DISABLED)
            }
            if (descriptor.category == ToolCategory.WORKSPACE && !context.policy.allowCloudWorkspace) {
                return ToolExposureDecision.Deny(ToolExposureDenyReason.REMOTE_WORKSPACE_DISABLED)
            }
        }

        // 4. Operating Mode checks
        val risk = descriptor.security.risk
        when (context.mode) {
            CognitiveOperatingMode.ASK, CognitiveOperatingMode.PLAN -> {
                if (risk != ToolRiskClass.READ_ONLY) {
                    return ToolExposureDecision.Deny(ToolExposureDenyReason.MODE_DISALLOWS_SIDE_EFFECT)
                }
            }
            CognitiveOperatingMode.AUTO, CognitiveOperatingMode.AGENT -> {
                // Full tool policy permitted
            }
        }

        return ToolExposureDecision.Allow
    }
}
