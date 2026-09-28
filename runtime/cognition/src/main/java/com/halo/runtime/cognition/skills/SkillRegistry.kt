package com.halo.runtime.cognition.skills

import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.skills.SkillCategory
import com.halo.sdk.runtime.skills.SkillDescriptor
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillRisk
import com.halo.sdk.runtime.skills.SkillSource
import com.halo.sdk.runtime.skills.SkillAvailability
import com.halo.runtime.cognition.tools.ToolApprovalPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SkillRegistry {
    val skills: StateFlow<List<SkillDescriptor>>
    fun get(id: SkillId): SkillDescriptor?
    fun enabledTools(allTools: List<ToolDefinition>): List<ToolDefinition>
    suspend fun setEnabled(id: SkillId, enabled: Boolean)
    fun skillIdForTool(toolName: String): SkillId? = null
    fun refreshAvailability() {}
    fun replaceCustomSkills(skills: List<SkillDescriptor>) {}
    fun approvalPolicy(id: SkillId): ToolApprovalPolicy = ToolApprovalPolicy.ASK_EVERY_TIME
    fun approvalPolicyForTool(toolName: String): ToolApprovalPolicy = ToolApprovalPolicy.ASK_EVERY_TIME
    fun authorizationGroupForTool(toolName: String): String = toolName
    suspend fun setApprovalPolicy(id: SkillId, policy: ToolApprovalPolicy) {}
}

class DefaultSkillRegistry(
    private val storage: Storage,
    initialBuiltIns: List<SkillDescriptor> = defaultBuiltInSkills(),
    private val availabilityResolver: SkillAvailabilityResolver = SkillAvailabilityResolver()
) : SkillRegistry {

    private val _skills = MutableStateFlow<List<SkillDescriptor>>(emptyList())
    override val skills: StateFlow<List<SkillDescriptor>> = _skills.asStateFlow()

    init {
        val list = initialBuiltIns.map { skill ->
            val savedEnabled = storage.getBoolean("skill_enabled_${skill.id.value}", skill.enabled)
            skill.copy(enabled = savedEnabled)
        }
        _skills.value = list.map { it.copy(availability = availabilityResolver.resolve(it)) }
    }

    override fun get(id: SkillId): SkillDescriptor? {
        return _skills.value.firstOrNull { it.id == id }
    }

    override fun enabledTools(allTools: List<ToolDefinition>): List<ToolDefinition> {
        refreshAvailability()
        val enabledToolNames = _skills.value
            .filter { it.enabled && (it.availability == SkillAvailability.Available || it.availability is SkillAvailability.PartiallyAvailable) }
            .flatMap { it.tools }
            .toSet()

        return allTools.filter { tool ->
            tool.name in enabledToolNames || tool.id in enabledToolNames
        }
    }

    override fun skillIdForTool(toolName: String): SkillId? {
        return _skills.value.firstOrNull { skill ->
            toolName in skill.tools
        }?.id
    }

    override suspend fun setEnabled(id: SkillId, enabled: Boolean) {
        storage.putBoolean("skill_enabled_${id.value}", enabled)
        _skills.value = _skills.value.map { skill ->
            val next = if (skill.id == id) skill.copy(enabled = enabled) else skill
            next.copy(availability = availabilityResolver.resolve(next))
        }
    }

    override fun refreshAvailability() {
        val current = _skills.value
        val refreshed = current.map { it.copy(availability = availabilityResolver.resolve(it)) }
        if (refreshed != current) _skills.value = refreshed
    }

    override fun replaceCustomSkills(skills: List<SkillDescriptor>) {
        val custom = skills
            .filter { it.source == SkillSource.CUSTOM }
            .distinctBy { it.id }
            .map { skill ->
                val savedEnabled = storage.getBoolean("skill_enabled_${skill.id.value}", skill.enabled)
                skill.copy(
                    enabled = savedEnabled,
                    availability = availabilityResolver.resolve(skill.copy(enabled = savedEnabled)),
                )
            }
        _skills.value = (_skills.value.filterNot { it.source == SkillSource.CUSTOM } + custom)
            .sortedWith(compareBy<SkillDescriptor> { it.category.name }.thenBy { it.name.lowercase() })
    }

    override fun approvalPolicy(id: SkillId): ToolApprovalPolicy {
        val skill = get(id) ?: return ToolApprovalPolicy.DENY
        return storage.getString("skill_approval_${id.value}")?.let {
            runCatching { ToolApprovalPolicy.valueOf(it) }.getOrNull()
        } ?: defaultApprovalPolicy(skill.risk)
    }

    override fun approvalPolicyForTool(toolName: String): ToolApprovalPolicy {
        refreshAvailability()
        val skill = _skills.value.firstOrNull { toolName in it.tools }
            ?: return ToolApprovalPolicy.DENY
        if (!skill.enabled) return ToolApprovalPolicy.DENY
        when (val availability = skill.availability) {
            SkillAvailability.Available -> Unit
            is SkillAvailability.PartiallyAvailable -> if (toolName in availability.unavailableTools) return ToolApprovalPolicy.DENY
            else -> return ToolApprovalPolicy.DENY
        }

        return storage.getString("skill_approval_${skill.id.value}")?.let {
            runCatching { ToolApprovalPolicy.valueOf(it) }.getOrNull()
        } ?: defaultApprovalPolicy(
            skill.toolDefinitions.firstOrNull { it.toolId == toolName }?.risk ?: skill.risk
        )
    }

    override fun authorizationGroupForTool(toolName: String): String =
        _skills.value.firstOrNull { toolName in it.tools }?.id?.value ?: toolName

    override suspend fun setApprovalPolicy(id: SkillId, policy: ToolApprovalPolicy) {
        if (get(id) == null) return
        storage.putString("skill_approval_${id.value}", policy.name)
        _skills.value = _skills.value.toList()
    }

    private fun defaultApprovalPolicy(risk: SkillRisk): ToolApprovalPolicy =
        if (risk == SkillRisk.READ_ONLY) ToolApprovalPolicy.ALWAYS_ALLOW else ToolApprovalPolicy.ASK_EVERY_TIME

    companion object {
        fun defaultBuiltInSkills(): List<SkillDescriptor> = listOf(
            SkillDescriptor(id = SkillId("halo.time"), name = "Date & time", description = "Read local time and time zone.",
                category = SkillCategory.INFORMATION, tools = setOf("halo_current_time")),
            SkillDescriptor(id = SkillId("halo.storage"), name = "Storage status", description = "Read available storage.",
                category = SkillCategory.INFORMATION, tools = setOf("halo_storage_status")),
            SkillDescriptor(
                id = SkillId("halo.discovery"), name = "Tool discovery",
                description = "Let the assistant look up additional tool schemas when needed.",
                category = SkillCategory.INFORMATION,
                tools = setOf("halo_discover_tools"), risk = SkillRisk.READ_ONLY, source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.notifications"), name = "Notification digests",
                description = "Build private offline notification digests.", category = SkillCategory.AUTOMATION,
                tools = setOf("halo_notification_digest"), risk = SkillRisk.READ_ONLY, source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.notebook"),
                name = "Notebook",
                description = "Create, search, and manage local notes and workspace items.",
                category = SkillCategory.WORKSPACE,
                tools = setOf("halo_create_note", "halo_search_notebook", "halo_get_workspace_object", "halo_add_to_working_set", "halo_remove_from_working_set"),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.memory"),
                name = "Memory",
                description = "Recall and propose user preferences and facts.",
                category = SkillCategory.WORKSPACE,
                tools = setOf("halo_search_memory", "halo_propose_memory"),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.files"),
                name = "Files",
                description = "Search and read metadata or text from user-imported documents.",
                category = SkillCategory.WORKSPACE,
                tools = setOf("halo_search_files", "halo_get_file_metadata", "halo_read_text_file"),
                risk = SkillRisk.READ_ONLY,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.device"),
                name = "Device controls",
                description = "Control local phone settings like volume and flashlight.",
                category = SkillCategory.DEVICE,
                tools = setOf("halo_volume", "halo_flashlight"),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.phone"),
                name = "Phone",
                description = "Entry point for phone control: load halo.phone.actions for deterministic actions " +
                    "and halo.phone.screen only for UI workflows with no dedicated capability.",
                category = SkillCategory.DEVICE,
                tools = setOf("halo_use_skill"),
                toolDefinitions = listOf(
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_use_skill", SkillRisk.READ_ONLY),
                ),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN,
            ),
            SkillDescriptor(
                id = SkillId("halo.phone.actions"),
                name = "Phone actions",
                description = "Deterministic Android phone actions. Preferred over screen automation whenever a " +
                    "dedicated Android capability exists.",
                category = SkillCategory.DEVICE,
                tools = setOf(
                    "halo_list_apps",
                    "halo_open_app",
                    "halo_set_alarm",
                    "halo_volume",
                    "halo_flashlight",
                    "halo_find_contact",
                    "halo_dial_contact",
                    "halo_open_settings",
                    "halo_navigation",
                    "halo_browser",
                    "halo_search",
                    "halo_share_text",
                    "halo_media_status",
                    "halo_media_control",
                ),
                risk = SkillRisk.EXTERNAL_SIDE_EFFECT,
                source = SkillSource.BUILT_IN,
            ),
            SkillDescriptor(
                id = SkillId("halo.phone.screen"),
                name = "Phone screen control",
                description = "Read and operate supported Android screens using snapshot-scoped semantic controls.",
                category = SkillCategory.DEVICE,
                tools = setOf(
                    "halo_read_screen",
                    "halo_find_on_screen",
                    "halo_ui_click",
                    "halo_ui_scroll",
                    "halo_ui_set_text",
                    "halo_ui_global_action",
                ),
                toolDefinitions = listOf(
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_read_screen", SkillRisk.READ_ONLY),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_find_on_screen", SkillRisk.READ_ONLY),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_click", SkillRisk.EXTERNAL_SIDE_EFFECT),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_scroll", SkillRisk.EXTERNAL_SIDE_EFFECT),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_set_text", SkillRisk.SENSITIVE),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_global_action", SkillRisk.EXTERNAL_SIDE_EFFECT),
                ),
                requiredPermissions = setOf(com.halo.sdk.runtime.skills.HostPermission.ACCESSIBILITY),
                risk = SkillRisk.SENSITIVE,
                source = SkillSource.BUILT_IN,
            ),
            SkillDescriptor(
                id = SkillId("halo.apps"),
                name = "Apps & Navigation",
                description = "Open launchable apps and navigation routes in installed maps.",
                category = SkillCategory.NAVIGATION,
                tools = setOf("halo_open_app", "halo_navigation", "halo_browser", "halo_search", "halo_media", "halo_list_apps"),
                risk = SkillRisk.EXTERNAL_SIDE_EFFECT,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.reminders"),
                name = "Reminders & Settings",
                description = "Set reminders and open device settings.",
                category = SkillCategory.AUTOMATION,
                tools = setOf("halo_create_reminder", "halo_open_settings", "halo_share_text", "halo_prepare_contact", "halo_list_reminders"),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN
            )
        )
    }
}
