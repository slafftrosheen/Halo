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
    fun refreshAvailability() {}
    fun approvalPolicy(id: SkillId): ToolApprovalPolicy = ToolApprovalPolicy.ASK_EVERY_TIME
    fun approvalPolicyForTool(toolName: String): ToolApprovalPolicy = ToolApprovalPolicy.ASK_EVERY_TIME
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
            .filter { it.enabled && it.availability == SkillAvailability.Available }
            .flatMap { it.tools }
            .toSet()

        return allTools.filter { tool ->
            tool.name in enabledToolNames || tool.id in enabledToolNames
        }
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

    override fun approvalPolicy(id: SkillId): ToolApprovalPolicy {
        val skill = get(id) ?: return ToolApprovalPolicy.DENY
        val default = if (skill.risk == SkillRisk.READ_ONLY) ToolApprovalPolicy.ALWAYS_ALLOW
            else ToolApprovalPolicy.ASK_EVERY_TIME
        return storage.getString("skill_approval_${id.value}")?.let {
            runCatching { ToolApprovalPolicy.valueOf(it) }.getOrNull()
        } ?: default
    }

    override fun approvalPolicyForTool(toolName: String): ToolApprovalPolicy {
        val skill = _skills.value.firstOrNull { toolName in it.tools }
            ?: return ToolApprovalPolicy.DENY
        if (!skill.enabled || skill.availability != SkillAvailability.Available) return ToolApprovalPolicy.DENY

        val toolRisk = skill.toolDefinitions.firstOrNull { it.toolId == toolName }?.risk ?: skill.risk
        val default = if (toolRisk == SkillRisk.READ_ONLY) ToolApprovalPolicy.ALWAYS_ALLOW
            else ToolApprovalPolicy.ASK_EVERY_TIME

        return storage.getString("skill_approval_${skill.id.value}")?.let {
            runCatching { ToolApprovalPolicy.valueOf(it) }.getOrNull()
        } ?: default
    }

    override suspend fun setApprovalPolicy(id: SkillId, policy: ToolApprovalPolicy) {
        if (get(id) == null) return
        storage.putString("skill_approval_${id.value}", policy.name)
        _skills.value = _skills.value.toList()
    }

    companion object {
        fun defaultBuiltInSkills(): List<SkillDescriptor> = listOf(
            SkillDescriptor(
                id = SkillId("halo.notebook"),
                name = "Notebook",
                description = "Create, search, and manage local notes and workspace items.",
                category = SkillCategory.WORKSPACE,
                tools = setOf("halo_create_note", "halo_search_notebook", "halo_get_workspace_object", "halo_add_to_working_set", "halo_remove_from_working_set"),
                risk = SkillRisk.READ_ONLY,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.memory"),
                name = "Memory",
                description = "Recall and propose user preferences and facts.",
                category = SkillCategory.WORKSPACE,
                tools = setOf("halo_search_memory", "halo_propose_memory", "halo_remember_text"),
                risk = SkillRisk.READ_ONLY,
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
                id = SkillId("halo.apps"),
                name = "Apps & Navigation",
                description = "Open launchable apps and navigation routes in installed maps.",
                category = SkillCategory.NAVIGATION,
                tools = setOf("halo_open_app", "halo_navigation", "halo_browser", "halo_search", "halo_media"),
                risk = SkillRisk.EXTERNAL_SIDE_EFFECT,
                source = SkillSource.BUILT_IN
            ),
            SkillDescriptor(
                id = SkillId("halo.reminders"),
                name = "Reminders & Settings",
                description = "Set reminders and open device settings.",
                category = SkillCategory.AUTOMATION,
                tools = setOf("halo_create_reminder", "halo_open_settings", "halo_share_text", "halo_prepare_contact"),
                risk = SkillRisk.REVERSIBLE,
                source = SkillSource.BUILT_IN
            )
        )
    }
}
