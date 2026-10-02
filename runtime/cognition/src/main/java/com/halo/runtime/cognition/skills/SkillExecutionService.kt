package com.halo.runtime.cognition.skills

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.tools.*
import com.halo.runtime.cognition.turn.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.skills.SkillId

/** Shared admission and execution authority; the existing tool turn owns approval, leases and journal. */
class SkillExecutionService(
    private val skills: SkillRegistry,
    private val tools: ToolRegistry,
    private val availability: ToolAvailabilityResolver,
    private val delegate: ToolTurnExecutor,
    private val isLocked: () -> Boolean = { false },
    private val coordinator: com.halo.runtime.cognition.agent.AgentRunCoordinator? = null,
    private val refreshCapabilities: () -> Unit = {},
) : ToolTurnExecutor {
    fun check(skillId: SkillId): Map<String, String> {
        refreshCapabilities()
        skills.refreshAvailability()
        val skill = skills.get(skillId) ?: return mapOf("skill" to "Skill not registered")
        return skill.tools.associateWith { name ->
            val descriptor = tools.descriptor(name)
            when {
                !skill.enabled -> "Disabled"
                descriptor == null -> "Executor not registered"
                skill.availability is com.halo.sdk.runtime.skills.SkillAvailability.MissingPermission -> "Needs permission: " + (skill.availability as com.halo.sdk.runtime.skills.SkillAvailability.MissingPermission).permission.name
                (skill.availability as? com.halo.sdk.runtime.skills.SkillAvailability.PartiallyAvailable)?.unavailableTools?.contains(name) == true -> "Needs permission or executor setup"
                skills.approvalPolicy(skill.id) == ToolApprovalPolicy.DENY -> "Disabled by approval policy"
                else -> when (val state = availability.checkAvailability(descriptor)) {
                    ToolAvailability.Available -> "Available"
                    is ToolAvailability.MissingCapabilities -> "Needs setup: " + state.missing.joinToString { it.value }
                    is ToolAvailability.Disabled -> state.reason
                    is ToolAvailability.Degraded -> state.reason
                }
            }
        }
    }

    override suspend fun execute(runId: AgentRunId?, interactionId: InteractionId, requestId: String,
        toolCalls: List<ToolCall>, workspaceReadAllowed: Boolean, exposedToolIds: Set<String>?): ToolTurnResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        refreshCapabilities()
        skills.refreshAvailability()
        for (call in toolCalls) {
            val ownerEnabled = (if (call.functionName.startsWith("halo_appfn_")) skills.get(SkillId("halo.current_app"))?.enabled else skills.skillIdForTool(call.functionName)?.let { skills.get(it)?.enabled }) == true
            val descriptor = tools.descriptor(call.functionName)
            val reason = when {
                !ownerEnabled && descriptor?.visibility != ToolVisibility.INTERNAL -> "Skill disabled or not registered"
                descriptor == null -> "Executor not registered"
                isLocked() && call.functionName !in LOCKED_INFORMATION -> "REQUIRES_UNLOCK: Unlock your device to use this skill"
                else -> when (val state = availability.checkAvailability(descriptor)) {
                    ToolAvailability.Available -> null
                    is ToolAvailability.MissingCapabilities -> "Needs permission or setup: " + state.missing.joinToString { it.value }
                    is ToolAvailability.Disabled -> state.reason
                    is ToolAvailability.Degraded -> state.reason
                }
            }
            if (reason != null) {
                val envelope = ToolResultEnvelope.rejected(reasonCode = "NEEDS_USER_ACTION", retryable = false,
                    requiresUserAction = true, details = reason)
                if (runId != null) coordinator?.recordStep(com.halo.runtime.cognition.agent.AgentStep.ToolRejected(
                    com.halo.runtime.cognition.agent.AgentStepId(java.util.UUID.randomUUID().toString()), runId,
                    java.time.Instant.now(), call.id, call.functionName,
                    com.halo.runtime.cognition.agent.ToolRejectionCode.CAPABILITY_UNAVAILABLE, reason, false, true, envelope))
                return@withContext ToolTurnResult.Completed(AiResponse(text = "", providerId = "host"), false,
                    listOf(AIMessage(Role.TOOL, envelope, toolCallId = call.id)))
            }
        }
        delegate.execute(runId, interactionId, requestId, toolCalls, workspaceReadAllowed, exposedToolIds)
    }
    companion object {
        private val LOCKED_INFORMATION = setOf("halo_battery_status", "halo_network_status", "halo_device_info", "halo_current_time")
    }
}

data class SkillRuntimeAuditResult(
    val skillCount: Int, val enabledSkillCount: Int, val toolCount: Int,
    val orphanToolIds: Set<String>, val unresolvedToolIds: Set<String>, val duplicateToolIds: Set<String>,
    val unavailable: Map<String, String>,
    val permissionMismatches: Set<String> = emptySet(),
)

class SkillRuntimeAudit(private val skills: SkillRegistry, private val tools: ToolRegistry,
    private val service: SkillExecutionService) {
    fun inspect(): SkillRuntimeAuditResult {
        val allSkills = skills.skills.value
        val definitions = tools.definitions()
        val declared = allSkills.flatMap { it.tools }.toSet()
        val registered = definitions.map { it.name }.toSet()
        val internal = tools.descriptors().filter { it.visibility == ToolVisibility.INTERNAL }.map { it.name }.toSet()
        return SkillRuntimeAuditResult(allSkills.size, allSkills.count { it.enabled }, registered.size,
            registered - declared - internal - registered.filter { it.startsWith("halo_appfn_") && allSkills.any { skill -> skill.id.value == "halo.current_app" } }.toSet(), declared - registered,
            definitions.groupBy { it.name }.filterValues { it.size > 1 }.keys,
            allSkills.flatMap { service.check(it.id).entries }.filter { it.value != "Available" }.associate { it.key to it.value },
            allSkills.flatMap { skill -> skill.toolDefinitions.mapNotNull { definition ->
                val declaredPermissions = skill.requiredPermissions + definition.requiredPermissions
                val needed = when (definition.toolId) {
                    "halo_find_contact" -> com.halo.sdk.runtime.skills.HostPermission.CONTACTS
                    "halo_calendar_upcoming" -> com.halo.sdk.runtime.skills.HostPermission.CALENDAR_READ
                    "halo_calendar_create" -> com.halo.sdk.runtime.skills.HostPermission.CALENDAR_WRITE
                    "halo_list_notifications", "halo_dismiss_notification", "halo_reply_notification", "halo_replyable_notifications" -> com.halo.sdk.runtime.skills.HostPermission.NOTIFICATION_ACCESS
                    else -> null
                }
                if (needed != null && needed !in declaredPermissions && skill.id.value !in setOf("halo.phone.actions", "halo.phone")) "${skill.id.value}:${definition.toolId}:$needed" else null
            } }.toSet())
    }
}
