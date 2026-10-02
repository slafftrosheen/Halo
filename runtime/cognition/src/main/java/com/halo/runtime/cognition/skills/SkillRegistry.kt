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
            val legacyEnabled = if (skill.id.value in NATIVE_COMPATIBILITY_IDS) storage.getBoolean("skill_enabled_halo.phone.actions", skill.enabled) else skill.enabled
            val savedEnabled = storage.getBoolean("skill_enabled_${skill.id.value}", legacyEnabled)
            skill.copy(enabled = savedEnabled)
        }
        _skills.value = list.map { it.copy(availability = availabilityResolver.resolve(it)) }
    }

    override fun get(id: SkillId): SkillDescriptor? {
        return _skills.value.firstOrNull { it.id == id }
    }

    override fun enabledTools(allTools: List<ToolDefinition>): List<ToolDefinition> {
        refreshAvailability()
        return allTools.filter { tool ->
            val owner = ownerForTool(tool.name) ?: ownerForTool(tool.id)
            owner != null && owner.enabled && when (val state = owner.availability) {
                SkillAvailability.Available -> true
                is SkillAvailability.PartiallyAvailable -> tool.name !in state.unavailableTools
                else -> false
            }
        }
    }

    override fun skillIdForTool(toolName: String): SkillId? {
        return ownerForTool(toolName)?.id
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
        return savedApproval(id)?.let {
            runCatching { ToolApprovalPolicy.valueOf(it) }.getOrNull()
        } ?: defaultApprovalPolicy(skill.risk)
    }

    override fun approvalPolicyForTool(toolName: String): ToolApprovalPolicy {
        refreshAvailability()
        val skill = ownerForTool(toolName)
            ?: return ToolApprovalPolicy.DENY
        if (!skill.enabled) return ToolApprovalPolicy.DENY
        when (val availability = skill.availability) {
            SkillAvailability.Available -> Unit
            is SkillAvailability.PartiallyAvailable -> if (toolName in availability.unavailableTools) return ToolApprovalPolicy.DENY
            else -> return ToolApprovalPolicy.DENY
        }

        return savedApproval(skill.id)?.let {
            runCatching { ToolApprovalPolicy.valueOf(it) }.getOrNull()
        } ?: defaultApprovalPolicy(
            skill.toolDefinitions.firstOrNull { it.toolId == toolName }?.risk ?: skill.risk
        )
    }

    override fun authorizationGroupForTool(toolName: String): String =
        ownerForTool(toolName)?.id?.value ?: toolName

    private fun ownerForTool(toolName: String) = _skills.value.filter { toolName in it.tools }
        .minByOrNull {
            val preferred = when (toolName) {
                "halo_find_contact", "halo_dial_contact" -> "halo.contacts"
                "halo_media_status", "halo_media_control" -> "halo.media"
                "halo_share_text" -> "halo.sharing"
                else -> null
            }
            when { it.id.value == preferred -> 0; it.id.value in setOf("halo.phone.actions", "halo.phone") -> 2; else -> 1 }
        }

    override suspend fun setApprovalPolicy(id: SkillId, policy: ToolApprovalPolicy) {
        if (get(id) == null) return
        storage.putString("skill_approval_${id.value}", policy.name)
        _skills.value = _skills.value.toList()
    }

    private fun savedApproval(id: SkillId): String? = storage.getString("skill_approval_${id.value}")
        ?: if (id.value in NATIVE_COMPATIBILITY_IDS) storage.getString("skill_approval_halo.phone.actions") else null

    private fun defaultApprovalPolicy(risk: SkillRisk): ToolApprovalPolicy =
        if (risk == SkillRisk.READ_ONLY) ToolApprovalPolicy.ALWAYS_ALLOW else ToolApprovalPolicy.ASK_EVERY_TIME

    companion object {
        private val NATIVE_COMPATIBILITY_IDS = setOf("halo.contacts", "halo.media", "halo.sharing", "halo.alarms", "halo.settings")
        fun defaultBuiltInSkills(): List<SkillDescriptor> = listOf(
            SkillDescriptor(id = SkillId("halo.messaging"), name = "Messaging", description = "Prepare messages in a platform composer.", category = SkillCategory.INFORMATION,
                tools = setOf("halo_compose_sms", "halo_compose_email", "halo_compose_message"), risk = SkillRisk.EXTERNAL_SIDE_EFFECT),
            SkillDescriptor(id = SkillId("halo.current_app"), name = "Current app", description = "Bounded semantic actions in the current app.", category = SkillCategory.INFORMATION,
                tools = setOf("halo_current_app_capabilities", "halo_current_app_action"), risk = SkillRisk.SENSITIVE),
            SkillDescriptor(id = SkillId("halo.system.panels"), name = "System panels", description = "Open Android controls that require user action.", category = SkillCategory.INFORMATION,
                tools = setOf("halo_open_settings"), risk = SkillRisk.REVERSIBLE),
            SkillDescriptor(id = SkillId("halo.study"), name = "Study", description = "Study local workspace material and inspect sessions.", category = SkillCategory.WORKSPACE, tools = setOf("halo_study_with_halo", "halo_get_study_session"), toolDefinitions = listOf(com.halo.sdk.runtime.skills.SkillToolDefinition("halo_study_with_halo", SkillRisk.REVERSIBLE), com.halo.sdk.runtime.skills.SkillToolDefinition("halo_get_study_session", SkillRisk.READ_ONLY)), risk = SkillRisk.REVERSIBLE),
            SkillDescriptor(id = SkillId("halo.research"), name = "Research & delegation", description = "Run bounded reasoning tasks through the configured agentic lane.", category = SkillCategory.INFORMATION, tools = setOf("halo_research", "halo_delegate_task"), toolDefinitions = listOf(com.halo.sdk.runtime.skills.SkillToolDefinition("halo_research", SkillRisk.READ_ONLY), com.halo.sdk.runtime.skills.SkillToolDefinition("halo_delegate_task", SkillRisk.READ_ONLY)), risk = SkillRisk.READ_ONLY),
            SkillDescriptor(id = SkillId("halo.context"), name = "Conversation & payload reading", description = "Read bounded local conversation history and tool payloads under context policy.", category = SkillCategory.WORKSPACE, tools = setOf("halo_read_payload", "halo_search_conversations"), toolDefinitions = listOf(com.halo.sdk.runtime.skills.SkillToolDefinition("halo_read_payload", SkillRisk.READ_ONLY), com.halo.sdk.runtime.skills.SkillToolDefinition("halo_search_conversations", SkillRisk.READ_ONLY)), risk = SkillRisk.READ_ONLY),
            SkillDescriptor(id = SkillId("halo.automation"), name = "Routines & procedures", description = "Propose procedures and approve saving a procedure or scheduled routine.", category = SkillCategory.AUTOMATION, tools = setOf("halo_create_routine", "halo_propose_procedure", "halo_save_procedure", "halo_use_procedure"), toolDefinitions = listOf(com.halo.sdk.runtime.skills.SkillToolDefinition("halo_create_routine", SkillRisk.SENSITIVE), com.halo.sdk.runtime.skills.SkillToolDefinition("halo_propose_procedure", SkillRisk.READ_ONLY), com.halo.sdk.runtime.skills.SkillToolDefinition("halo_save_procedure", SkillRisk.SENSITIVE), com.halo.sdk.runtime.skills.SkillToolDefinition("halo_use_procedure", SkillRisk.READ_ONLY)), risk = SkillRisk.SENSITIVE),
            SkillDescriptor(id = SkillId("halo.alarms"), name = "Alarms & timers", description = "Set an alarm or timer using Android.", category = SkillCategory.DEVICE, tools = setOf("halo_set_alarm", "halo_timer"), risk = SkillRisk.EXTERNAL_SIDE_EFFECT),
            SkillDescriptor(id = SkillId("halo.settings"), name = "Settings", description = "Open a system settings page.", category = SkillCategory.DEVICE, tools = setOf("halo_open_settings"), risk = SkillRisk.REVERSIBLE),
            SkillDescriptor(id = SkillId("halo.contacts"), name = "Contacts", description = "Find contacts and open the dialer.",
                category = SkillCategory.INFORMATION, tools = setOf("halo_find_contact", "halo_dial_contact"), risk = SkillRisk.EXTERNAL_SIDE_EFFECT),
            SkillDescriptor(id = SkillId("halo.media"), name = "Media", description = "Read and control active playback.",
                category = SkillCategory.DEVICE, tools = setOf("halo_media_status", "halo_media_control"), risk = SkillRisk.REVERSIBLE),
            SkillDescriptor(id = SkillId("halo.sharing"), name = "Sharing", description = "Open the system share chooser.",
                category = SkillCategory.DEVICE, tools = setOf("halo_share_text"), risk = SkillRisk.EXTERNAL_SIDE_EFFECT),
            SkillDescriptor(id = SkillId("halo.calendar"), name = "Calendar", description = "Read upcoming events and create an approved event.",
                category = SkillCategory.INFORMATION, tools = setOf("halo_calendar_upcoming", "halo_calendar_create"), risk = SkillRisk.EXTERNAL_SIDE_EFFECT),
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
                id = SkillId("halo.presentation"),
                name = "Presentation",
                description = "Present bounded structured information to the user.",
                category = SkillCategory.INFORMATION,
                tools = setOf("halo_present"),
                risk = SkillRisk.READ_ONLY,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.notebook"),
                name = "Notebook",
                description = "Create, search, and manage local notes and workspace items.",
                category = SkillCategory.WORKSPACE,
                tools = setOf("halo_save_screen_to_notebook", "halo_create_empty_note", "halo_create_note", "halo_search_notebook", "halo_get_workspace_object", "halo_add_to_working_set", "halo_remove_from_working_set"),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.memory"),
                name = "Memory",
                description = "Recall and propose user preferences and facts.",
                category = SkillCategory.WORKSPACE,
                tools = setOf("halo_search_memory", "halo_read_memory", "halo_propose_memory", "halo_remember", "halo_update_memory", "halo_forget_memory", "halo_set_working_memory", "halo_read_working_memory"),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.files"),
                name = "Files",
                description = "Search and read metadata or text from user-imported documents, and checksum imported files or " +
                    "user-provided text.",
                category = SkillCategory.WORKSPACE,
                tools = setOf("halo_search_files", "halo_get_file_metadata", "halo_read_text_file", "halo_checksum"),
                risk = SkillRisk.READ_ONLY,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.device"),
                name = "Device controls",
                description = "Control local phone state like media volume, flashlight, sound mode and screen brightness.",
                category = SkillCategory.DEVICE,
                tools = setOf("halo_volume", "halo_flashlight", "halo_ringer", "halo_brightness"),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.device.info"),
                name = "Device insight",
                description = "Describe the phone, battery state and active network. Observation only.",
                category = SkillCategory.INFORMATION,
                tools = setOf("halo_device_info", "halo_battery_status", "halo_network_status"),
                risk = SkillRisk.READ_ONLY,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.clipboard"),
                name = "Clipboard",
                description = "Read the text the user last copied, or copy text for the user to paste elsewhere.",
                category = SkillCategory.INFORMATION,
                tools = setOf("halo_clipboard_read", "halo_clipboard_write"),
                risk = SkillRisk.SENSITIVE,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.web"),
                name = "Web reading",
                description = "Read the readable text or links of one public web page so Halo can summarize it.",
                category = SkillCategory.INFORMATION,
                tools = setOf("halo_fetch_url"),
                risk = SkillRisk.SENSITIVE,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.assistant"), name = "Halo assistant controls",
                description = "Halo pages, app-only appearance/font/start page, and read-aloud playback.",
                category = SkillCategory.DEVICE,
                tools = setOf("halo_list_screens", "halo_open_screen", "halo_get_preferences", "halo_set_preferences", "halo_read_aloud", "halo_stop_reading", "halo_list_app_actions", "halo_perform_app_action"),
                risk = SkillRisk.SENSITIVE,
                toolDefinitions = listOf(
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_list_screens", SkillRisk.READ_ONLY),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_open_screen", SkillRisk.EXTERNAL_SIDE_EFFECT),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_get_preferences", SkillRisk.READ_ONLY),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_set_preferences", SkillRisk.REVERSIBLE),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_read_aloud", SkillRisk.EXTERNAL_SIDE_EFFECT),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_stop_reading", SkillRisk.REVERSIBLE),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_list_app_actions", SkillRisk.READ_ONLY),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_perform_app_action", SkillRisk.SENSITIVE)
                ), source = SkillSource.BUILT_IN
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
                    "halo_ringer",
                    "halo_brightness",
                    "halo_timer",
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
                    "halo_ui_set_checked",
                    "halo_ui_wait",
                    "halo_ui_search",
                    "halo_ui_global_action",
                ),
                toolDefinitions = listOf(
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_read_screen", SkillRisk.READ_ONLY),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_find_on_screen", SkillRisk.READ_ONLY),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_click", SkillRisk.EXTERNAL_SIDE_EFFECT),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_scroll", SkillRisk.EXTERNAL_SIDE_EFFECT),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_set_text", SkillRisk.SENSITIVE),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_set_checked", SkillRisk.REVERSIBLE),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_wait", SkillRisk.READ_ONLY),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_ui_search", SkillRisk.EXTERNAL_SIDE_EFFECT),
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
                tools = setOf("halo_open_app", "halo_navigation", "halo_browser", "halo_search", "halo_media_status", "halo_media_control", "halo_list_apps"),
                risk = SkillRisk.EXTERNAL_SIDE_EFFECT,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.reminders"),
                name = "Alarms, timers & settings",
                description = "Set clock alarms and countdown timers, open specific Android settings screens, and share text.",
                category = SkillCategory.AUTOMATION,
                // RUNTIME-056: this skill previously declared five tools, three of which
                // (halo_create_reminder, halo_prepare_contact, halo_list_reminders) are not
                // registered anywhere, so the Skills screen reported the skill as
                // permanently partly-unavailable. It now names the registered actions.
                tools = setOf("halo_set_alarm", "halo_timer", "halo_open_settings", "halo_share_text"),
                toolDefinitions = listOf(
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_set_alarm", SkillRisk.EXTERNAL_SIDE_EFFECT),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_timer", SkillRisk.EXTERNAL_SIDE_EFFECT),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_open_settings", SkillRisk.REVERSIBLE),
                    com.halo.sdk.runtime.skills.SkillToolDefinition("halo_share_text", SkillRisk.EXTERNAL_SIDE_EFFECT),
                ),
                risk = SkillRisk.EXTERNAL_SIDE_EFFECT,
                source = SkillSource.BUILT_IN
            )
        )
    }
}
