package com.halo.runtime.cognition.skills

import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.skills.SkillCategory
import com.halo.sdk.runtime.skills.SkillDescriptor
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillRisk
import com.halo.sdk.runtime.skills.SkillSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SkillRegistry {
    val skills: StateFlow<List<SkillDescriptor>>
    fun get(id: SkillId): SkillDescriptor?
    fun enabledTools(allTools: List<ToolDefinition>): List<ToolDefinition>
    suspend fun setEnabled(id: SkillId, enabled: Boolean)
}

class DefaultSkillRegistry(
    private val storage: Storage,
    initialBuiltIns: List<SkillDescriptor> = defaultBuiltInSkills()
) : SkillRegistry {

    private val _skills = MutableStateFlow<List<SkillDescriptor>>(emptyList())
    override val skills: StateFlow<List<SkillDescriptor>> = _skills.asStateFlow()

    init {
        val list = initialBuiltIns.map { skill ->
            val savedEnabled = storage.getBoolean("skill_enabled_${skill.id.value}", skill.enabled)
            skill.copy(enabled = savedEnabled)
        }
        _skills.value = list
    }

    override fun get(id: SkillId): SkillDescriptor? {
        return _skills.value.firstOrNull { it.id == id }
    }

    override fun enabledTools(allTools: List<ToolDefinition>): List<ToolDefinition> {
        val enabledToolNames = _skills.value
            .filter { it.enabled }
            .flatMap { it.tools }
            .toSet()

        return allTools.filter { tool ->
            tool.name in enabledToolNames || tool.id in enabledToolNames
        }
    }

    override suspend fun setEnabled(id: SkillId, enabled: Boolean) {
        storage.putBoolean("skill_enabled_${id.value}", enabled)
        _skills.value = _skills.value.map { skill ->
            if (skill.id == id) skill.copy(enabled = enabled) else skill
        }
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
