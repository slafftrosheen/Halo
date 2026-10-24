package com.halo.app.di.tools

import com.halo.app.learning.LearningConfigurationOwner
import com.halo.app.ui.screens.settings.LearningSettingsViewModel
import com.halo.runtime.cognition.memory.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearningConfigurationToolsTest {
    private fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("learning", name, args)
    private val scope = ToolExecutionContext("approved-interaction", false)
    @Test fun uiAndToolShareOwnerAndRejectStaleVersions() = runBlocking {
        val storage = MemoryStorage()
        val owner = LearningConfigurationOwner(storage, LearningPreferences())
        val ui = LearningSettingsViewModel(storage, LearningPreferences(), MutableStateFlow(false), {}, owner)
        val tools = LearningConfigurationTools(owner) { true }
        ui.mode(LearningPreference.SUGGEST)
        val before = owner.snapshot()
        ui.option("learning_memories", false)
        val stale = tools.execute(call("halo_set_learning_configuration", mapOf("sourceVersion" to before.sourceVersion, "suggestMemories" to true)), scope)
        assertTrue(stale is ToolExecutionResult.Rejected)
        val current = owner.snapshot()
        val saved = tools.execute(call("halo_set_learning_configuration", mapOf("sourceVersion" to current.sourceVersion, "suggestAutomations" to false)), scope) as ToolExecutionResult.Success
        assertFalse(JSONObject(saved.output).getBoolean("suggestAutomations"))
        assertFalse(ui.preferences.value.suggestAutomations)
        assertFalse(ui.preferences.value.suggestMemories)
        assertEquals(ui.preferences.value, LearningConfigurationOwner(storage, LearningPreferences()).snapshot().preferences)
    }
    @Test fun writesRequireScopedApprovalAndCannotChangeMemorySharing() = runBlocking {
        val owner = LearningConfigurationOwner(MemoryStorage(), LearningPreferences())
        val tools = LearningConfigurationTools(owner) { true }
        val args = mapOf("sourceVersion" to owner.snapshot().sourceVersion, "mode" to "SUGGEST")
        assertEquals(ToolRejectionKind.PERMISSION_REQUIRED, (tools.execute(call("halo_set_learning_configuration", args)) as ToolExecutionResult.Rejected).kind)
        assertTrue(tools.execute(call("halo_set_learning_configuration", args + ("allowCloudMemories" to true)), scope) is ToolExecutionResult.Rejected)
        assertEquals(LearningPreference.EXPLICIT_ONLY, owner.preferences.value.mode)
        assertTrue(LearningConfigurationTools(owner).execute(call("halo_get_learning_configuration")) is ToolExecutionResult.Success)
    }
    @Test fun partialStorageFailureIsUncertainAndConsumesVersion() = runBlocking {
        val storage = MemoryStorage()
        val owner = LearningConfigurationOwner(storage, LearningPreferences(mode = LearningPreference.SUGGEST))
        owner.mode(LearningPreference.SUGGEST)
        val before = owner.snapshot()
        storage.failNextProcedureWrite = true
        val result = LearningConfigurationTools(owner) { true }.execute(call("halo_set_learning_configuration",
            mapOf("sourceVersion" to before.sourceVersion, "suggestMemories" to false)), scope) as ToolExecutionResult.Failed
        assertTrue(result.completionUnknown)
        assertNotEquals(before.sourceVersion, owner.snapshot().sourceVersion)
        assertFalse(owner.snapshot().preferences.suggestMemories)
    }
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        var failNextProcedureWrite = false
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) {
            if (key == "learning_procedures" && failNextProcedureWrite) { failNextProcedureWrite = false; error("Disk failure") }
            values[key] = value
        }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
