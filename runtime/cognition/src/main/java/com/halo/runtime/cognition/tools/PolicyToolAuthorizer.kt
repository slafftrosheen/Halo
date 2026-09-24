package com.halo.runtime.cognition.tools

import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.skills.SkillRisk

enum class ToolApprovalPolicy {
    ALWAYS_ALLOW,
    ASK_EVERY_TIME,
    ALLOW_WHILE_APP_OPEN,
    DENY
}

data class ToolActionPreview(
    val title: String,
    val description: String,
    val toolName: String,
    val risk: SkillRisk,
    val target: String? = null
)

interface ToolAuthorizationCoordinator {
    suspend fun authorizeRequest(toolCall: ToolCall, preview: ToolActionPreview): ToolAuthorization
}

class PolicyToolAuthorizer(
    private val coordinator: ToolAuthorizationCoordinator? = null,
    private val defaultPolicyProvider: (String) -> ToolApprovalPolicy = { ToolApprovalPolicy.ASK_EVERY_TIME }
) : ToolAuthorizer {

    override suspend fun authorize(toolCall: ToolCall): ToolAuthorization {
        val toolName = toolCall.functionName
        val policy = defaultPolicyProvider(toolName)

        if (policy == ToolApprovalPolicy.DENY) {
            return ToolAuthorization.Rejected(toolCall, "Tool execution denied by user policy")
        }

        val risk = mapRisk(toolName)

        if (policy == ToolApprovalPolicy.ALWAYS_ALLOW &&
            risk != SkillRisk.DESTRUCTIVE && risk != SkillRisk.SENSITIVE) {
            return ToolAuthorization.Authorized(toolCall)
        }

        val preview = generatePreview(toolCall, risk)

        if (coordinator != null) {
            return coordinator.authorizeRequest(toolCall, preview)
        }

        return ToolAuthorization.Rejected(toolCall, "Interactive confirmation required for $toolName")
    }

    private fun mapRisk(toolName: String): SkillRisk = when {
        toolName.startsWith("halo_search_") || toolName.startsWith("halo_get") ||
            toolName.startsWith("halo_list") || toolName == "halo_storage_status" || toolName == "halo_notification_digest" || toolName == "halo.current_time" || toolName == "halo_current_time" || toolName == "halo_read_text_file" -> SkillRisk.READ_ONLY
        toolName == "halo_volume" || toolName == "halo_flashlight" ||
            toolName == "halo_media" || toolName == "halo_add_to_working_set" ||
            toolName == "halo_remove_from_working_set" -> SkillRisk.REVERSIBLE
        toolName == "halo_propose_memory" -> SkillRisk.REVERSIBLE
        toolName == "halo_create_note" || toolName == "halo_remember_text" || toolName == "halo_create_reminder" -> SkillRisk.REVERSIBLE
        toolName == "halo_open_app" || toolName == "halo_navigation" ||
            toolName == "halo_browser" || toolName == "halo_search" || toolName == "halo_share_text" ||
            toolName == "halo_open_settings" || toolName == "halo_prepare_contact" -> SkillRisk.EXTERNAL_SIDE_EFFECT
        else -> SkillRisk.SENSITIVE
    }

    private fun generatePreview(toolCall: ToolCall, risk: SkillRisk): ToolActionPreview {
        val name = toolCall.functionName
        val args = toolCall.arguments
        val target = args.values.firstOrNull()?.toString()

        val (title, description) = when (name) {
            "halo_create_note" -> "Create note" to "Create a local note in Notebook: ${target ?: "Untitled"}"
            "halo_open_app" -> "Open app" to "Launch application package: $target"
            "halo_navigation" -> "Open navigation" to "Open route to destination: $target"
            "halo_browser" -> "Open browser" to "Navigate to web address: $target"
            "halo_flashlight" -> "Flashlight" to "Turn flashlight $target"
            "halo_volume" -> "Adjust volume" to "Change volume $target"
            "halo_create_reminder" -> "Create reminder" to "${args["message"]} at ${args["at"]} (${args["precision"] ?: "FLEXIBLE"})"
            "halo_propose_memory" -> "Save memory" to "Remember: ${args["content"]}"
            "halo_share_text" -> "Share text" to "Open the share sheet with: ${args["text"]}"
            "halo_prepare_contact" -> "Prepare contact" to "Open contact draft: ${args["name"]}, ${args["phone"] ?: ""}, ${args["email"] ?: ""}"
            else -> "Execute $name" to "Perform action: $args"
        }

        return ToolActionPreview(
            title = title,
            description = description,
            toolName = name,
            risk = risk,
            target = target
        )
    }
}
