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
    REMOTE_SCREEN_DISABLED,
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
    companion object {
        internal val CLOUD_SCREEN_GATED_TOOLS = setOf(
            "halo_read_screen", "halo_find_on_screen", "halo_ui_click",
            "halo_ui_scroll", "halo_ui_set_text", "halo_ui_set_checked",
            "halo_ui_wait", "halo_ui_search", "halo_ui_global_action",
            "halo_current_app_capabilities", "halo_current_app_action"
        )
    }

    override fun evaluate(
        descriptor: ToolDescriptor,
        context: ToolExposureContext,
    ): ToolExposureDecision {
        val toolName = descriptor.name
        val skillId = if (toolName.startsWith("halo_appfn_")) com.halo.sdk.runtime.skills.SkillId("halo.current_app") else skillRegistry?.skillIdForTool(toolName)

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
            // Screen tool output contains third-party UI text and accessible controls.
            // Never expose it to remote inference unless the user explicitly enabled
            // cloud screen-context sharing, even when a phone skill is activated.
            if (!context.policy.allowCloudScreenContext &&
                (descriptor.name in CLOUD_SCREEN_GATED_TOOLS || descriptor.name.startsWith("halo_appfn_"))) {
                return ToolExposureDecision.Deny(ToolExposureDenyReason.REMOTE_SCREEN_DISABLED)
            }
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
