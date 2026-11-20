package com.halo.runtime.cognition.skills

import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.skills.SkillId
import com.halo.sdk.runtime.skills.SkillRisk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class InterfaceConfigurationDiscoveryTest {
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

    @Test fun newDomainsHaveUniqueNativeOwnershipWithoutCollidingWithPhoneScreenControl() {
        val registry = DefaultSkillRegistry(MemoryStorage())
        val expected = mapOf("halo.assistant" to PersonalConfigurationToolAccess.preferences,
            "halo.saved_devices" to PersonalConfigurationToolAccess.savedDevices,
            "halo.notification_settings" to PersonalConfigurationToolAccess.notificationSettings,
            "halo.voice.configuration" to PersonalConfigurationToolAccess.voice,
            "halo.speech.inventory" to PersonalConfigurationToolAccess.speechInventory,
            "halo.learning.configuration" to PersonalConfigurationToolAccess.learning,
            "halo.provider.configuration" to PersonalConfigurationToolAccess.providers,
            "halo.automation" to PersonalConfigurationToolAccess.routines,
            "halo.study" to PersonalConfigurationToolAccess.activeStudy,
            "halo.notebook" to (setOf("halo_canvas_current") + WorkspaceToolAccess.canvasWorkflowTools))
        expected.forEach { (owner, names) -> names.forEach { assertEquals(it, SkillId(owner), registry.skillIdForTool(it)) } }
        assertNotEquals(SkillId("halo.screen.layout"), registry.skillIdForTool("halo_read_screen"))
        assertEquals(SkillRisk.READ_ONLY, registry.get(SkillId("halo.assistant"))!!.toolDefinitions.single { it.toolId == "halo_get_mode_profile" }.risk)
        assertEquals(SkillRisk.SENSITIVE, registry.get(SkillId("halo.assistant"))!!.toolDefinitions.single { it.toolId == "halo_edit_mode_profile" }.risk)
        assertEquals(SkillRisk.READ_ONLY, registry.get(SkillId("halo.notebook"))!!.toolDefinitions.single { it.toolId == "halo_canvas_current" }.risk)
    }

    @Test fun retiredAmbientPageCannotBeDiscoveredAsAnAction() = runTest {
        val registry = DefaultSkillRegistry(MemoryStorage())
        assertNull(registry.get(SkillId("halo.screen.layout")))
        assertNotNull(registry.skillIdForTool("halo_read_screen"))
        assertNotNull(registry.get(SkillId("halo.everyday")))
        val executor = UseSkillToolExecutor(BundledSkillInstructionSource(), registry)
        assertTrue(executor.execute(ToolCall("retired", "halo_use_skill", mapOf("skillId" to "halo.screen.layout"))) !is ToolExecutionResult.Success)
    }
    @Test fun allConfigurationDomainsLoadDedicatedBoundedGuidance() = runTest {
        val source = BundledSkillInstructionSource()
        listOf("halo.assistant", "halo.saved_devices", "halo.notification_settings", "halo.notebook",
            "halo.voice.configuration", "halo.speech.inventory", "halo.learning.configuration", "halo.provider.configuration", "halo.automation", "halo.study").forEach {
            assertNotNull(it, source.load(SkillId(it)))
        }
        val assistant = source.load(SkillId("halo.assistant"))!!.body
        assertTrue(assistant.contains("0.45..2.0"))
        assertTrue(assistant.contains("halo_get_mode_profile"))
        assertTrue(source.load(SkillId("halo.notebook"))!!.body.contains("halo_canvas_current"))
    }
}
