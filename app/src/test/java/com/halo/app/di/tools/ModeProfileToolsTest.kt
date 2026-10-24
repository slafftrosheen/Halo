package com.halo.app.di.tools

import android.app.Application
import com.halo.app.ai.*
import com.halo.app.di.tools.host.ModeProfileTools
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ModeProfileToolsTest {
    @Test fun expandingRemoteSharingNeverUsesProfileApprovalAsDisclosureConsent() = runBlocking {
        val modes = PrivacyModeStore(MemoryStorage())
        modes.edit(PrivacyMode.CLOUD) { it.copy(onlineFeatures = emptySet()) }
        val tools = ModeProfileTools(modes, { _, _, _ -> fail("Expanded sharing must not be saved without disclosure review") })
        val state = JSONObject((tools.execute(ToolCall("read", "halo_get_mode_profile", mapOf("mode" to "CLOUD"))) as ToolExecutionResult.Success).output)
        val response = tools.execute(ToolCall("expand", "halo_edit_mode_profile", mapOf("mode" to "CLOUD",
            "revision" to state.getLong("revision"), "snapshot" to state.getString("snapshot"), "onlineFeatures" to listOf("INFERENCE"))))
        assertEquals(ToolRejectionKind.PERMISSION_REQUIRED, (response as ToolExecutionResult.Rejected).kind)
        assertEquals(PrivacyMode.PRIVATE, modes.mode.value)
        assertFalse(modes.sharingAcknowledged)
        assertTrue(modes.profile(PrivacyMode.CLOUD).onlineFeatures.isEmpty())
    }

    @Test fun exactProfileEditsPersistWithoutActivatingModeAndStaleRequestsStop() = runBlocking {
        val backing = MemoryStorage()
        val modes = PrivacyModeStore(backing)
        val tools = ModeProfileTools(modes, { mode, profile, expected ->
            modes.edit(mode) { check(modes.revision == expected); profile }
        })
        val read = tools.execute(ToolCall("read", "halo_get_mode_profile", mapOf("mode" to "PRIVATE"))) as ToolExecutionResult.Success
        val state = JSONObject(read.output)
        val args = mapOf("mode" to "PRIVATE", "revision" to state.getLong("revision"), "snapshot" to state.getString("snapshot"), "routing" to "EXPLICIT")
        val result = tools.execute(ToolCall("write", "halo_edit_mode_profile", args)) as ToolExecutionResult.Success
        assertEquals(ActionTruth.CONFIRMED, result.actionTruth)
        assertEquals("EXPLICIT", modes.profile(PrivacyMode.PRIVATE).strings["ai_routing_policy"])
        assertEquals(PrivacyMode.PRIVATE, modes.mode.value)
        assertFalse(modes.sharingAcknowledged)
        assertEquals("EXPLICIT", PrivacyModeStore(backing).profile(PrivacyMode.PRIVATE).strings["ai_routing_policy"])
        assertTrue(tools.execute(ToolCall("stale", "halo_edit_mode_profile", args)) is ToolExecutionResult.Rejected)
    }

    @Test fun unknownFieldsSecretsAndModeActivationCannotBeSmuggledIntoProfileWrites() = runBlocking {
        val modes = PrivacyModeStore(MemoryStorage())
        val tools = ModeProfileTools(modes, { _, _, _ -> fail("Malformed write must not reach owner") })
        val state = JSONObject((tools.execute(ToolCall("read", "halo_get_mode_profile", mapOf("mode" to "PRIVATE"))) as ToolExecutionResult.Success).output)
        val base = mapOf("mode" to "PRIVATE", "revision" to state.getLong("revision"), "snapshot" to state.getString("snapshot"))
        listOf(mapOf("apiKey" to "secret"), mapOf("activate" to true), mapOf("endpoint" to "https://example.com"),
            mapOf("booleans" to mapOf("unknown" to true)), mapOf("onlineFeatures" to listOf("INFERENCE"))).forEach {
            assertTrue(tools.execute(ToolCall("invalid", "halo_edit_mode_profile", base + it)) is ToolExecutionResult.Rejected)
        }
    }

    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
