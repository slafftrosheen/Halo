package com.halo.runtime.cognition.skills

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.skills.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SkillToolOwnershipTest {
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private fun custom(tool: String) = SkillDescriptor(SkillId("custom.helper"), "AAA helper", "Custom guidance",
        SkillCategory.WORKSPACE, tools = setOf(tool), source = SkillSource.CUSTOM)

    @Test fun `Workspace name retains legacy skill settings and tool ownership after reload`() = runTest {
        val storage = MemoryStorage()
        val original = DefaultSkillRegistry(storage)
        val id = SkillId("halo.notebook")
        original.setEnabled(id, false)
        val restored = DefaultSkillRegistry(storage)
        assertEquals(id, restored.skillIdForTool("halo_get_workspace_object"))
        assertTrue(restored.enabledTools(listOf(ToolDefinition("halo_get_workspace_object",
            "halo_get_workspace_object", "Read selected object", emptyMap()))).isEmpty())
        assertEquals("Workspace", DefaultSkillRegistry.defaultBuiltInSkills().first { it.id == id }.name)
    }
    @Test fun `custom ordering cannot replace native enablement or approval authority`() = runTest {
        val native = DefaultSkillRegistry.defaultBuiltInSkills().first { it.id.value == "halo.apps" }
        val custom = custom("halo_open_app")
        val registry = DefaultSkillRegistry(MemoryStorage(), initialBuiltIns = listOf(custom, native))
        registry.setApprovalPolicy(native.id, ToolApprovalPolicy.DENY)
        registry.setApprovalPolicy(custom.id, ToolApprovalPolicy.ALWAYS_ALLOW)
        registry.replaceCustomSkills(listOf(custom))
        assertEquals(native.id, registry.skillIdForTool("halo_open_app"))
        assertEquals(ToolApprovalPolicy.DENY, registry.approvalPolicyForTool("halo_open_app"))
        registry.setEnabled(native.id, false)
        assertTrue(registry.enabledTools(listOf(ToolDefinition("halo_open_app", "halo_open_app", "Open app", emptyMap()))).isEmpty())
        val result = UseSkillToolExecutor(SkillInstructionSource { SkillInstruction(it, "Use only available tools") }, registry)
            .execute(ToolCall("load", "halo_use_skill", mapOf("skillId" to custom.id.value))) as ToolExecutionResult.Success
        assertFalse(result.output.contains("Available tools for this skill"))
    }
    @Test fun `custom skill retains ownership of its own extension tools`() = runTest {
        val custom = custom("extension_lookup")
        val registry = DefaultSkillRegistry(MemoryStorage(), initialBuiltIns = listOf(custom))
        assertEquals(custom.id, registry.skillIdForTool("extension_lookup"))
        val result = UseSkillToolExecutor(SkillInstructionSource { SkillInstruction(it, "Read extension data") }, registry)
            .execute(ToolCall("load", "halo_use_skill", mapOf("skillId" to custom.id.value))) as ToolExecutionResult.Success
        assertTrue(result.output.contains("Available tools for this skill: extension_lookup"))
    }
    @Test fun `loading shared guidance cannot expose permission blocked native actions`() = runTest {
        val native = DefaultSkillRegistry.defaultBuiltInSkills().first { it.id.value == "halo.contacts" }.copy(
            requiredPermissions = setOf(HostPermission.CONTACTS),
            toolDefinitions = listOf(SkillToolDefinition("halo_find_contact", SkillRisk.READ_ONLY, setOf(HostPermission.CONTACTS)),
                SkillToolDefinition("halo_dial_contact", SkillRisk.EXTERNAL_SIDE_EFFECT)))
        val custom = custom("halo_find_contact")
        val registry = DefaultSkillRegistry(MemoryStorage(), listOf(custom, native), SkillAvailabilityResolver(permissionGranted = { false }))
        val result = UseSkillToolExecutor(SkillInstructionSource { SkillInstruction(it, "Find the chosen contact") }, registry)
            .execute(ToolCall("load", "halo_use_skill", mapOf("skillId" to custom.id.value))) as ToolExecutionResult.Success
        assertFalse(result.output.contains("Available tools for this skill"))
    }
}
