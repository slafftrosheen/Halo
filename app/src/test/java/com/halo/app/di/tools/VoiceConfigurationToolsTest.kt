package com.halo.app.di.tools

import android.app.Application
import com.halo.app.audio.VoiceExperienceSettings
import com.halo.app.di.tools.host.*
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.sdk.provider.ToolCall
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class VoiceConfigurationToolsTest {
    @Test fun ignoredOrUncertainOwnerWriteNeverReportsConfirmed() = runBlocking {
        val state = VoiceConfigurationState("en", VoiceExperienceSettings(), true, listOf("en", "lv"))
        val tools = VoiceConfigurationTools({ state }, { _, _ -> }, { true }, { true })
        val read = JSONObject((tools.execute(ToolCall("read", "halo_get_voice_configuration", emptyMap())) as ToolExecutionResult.Success).output)
        val result = tools.execute(ToolCall("ignored", "halo_set_voice_configuration", mapOf("revision" to read.getLong("revision"),
            "snapshot" to read.getString("snapshot"), "locale" to "lv"))) as ToolExecutionResult.Failed
        assertTrue(result.completionUnknown)
    }
    @Test fun exactConfigurationChangesUseOwnerWithoutStartingCapturePlaybackOrTests() = runBlocking {
        var state = VoiceConfigurationState("en", VoiceExperienceSettings(), true, listOf("en", "lv"))
        var writes = 0
        val tools = VoiceConfigurationTools({ state }, { before, change ->
            assertEquals(state, before); writes++
            state = state.copy(locale = change.locale ?: state.locale,
                experience = state.experience.copy(wearablePhoneFallback = change.wearablePhoneFallback ?: state.experience.wearablePhoneFallback),
                synthesisFallback = change.synthesisFallback ?: state.synthesisFallback)
        }, { true }, { true })
        val read = JSONObject((tools.execute(ToolCall("read", "halo_get_voice_configuration", emptyMap())) as ToolExecutionResult.Success).output)
        val args = mapOf("revision" to read.getLong("revision"), "snapshot" to read.getString("snapshot"),
            "locale" to "lv", "wearablePhoneFallback" to true, "synthesisFallback" to false)
        assertEquals(ActionTruth.CONFIRMED, (tools.execute(ToolCall("write", "halo_set_voice_configuration", args)) as ToolExecutionResult.Success).actionTruth)
        assertEquals("lv", state.locale); assertEquals(1, writes)
        assertTrue(tools.execute(ToolCall("stale", "halo_set_voice_configuration", args)) is ToolExecutionResult.Rejected)
        assertEquals(1, writes)
    }

    @Test fun malformedUnavailableBackgroundAndKidsWritesNeverReachCanonicalOwner() = runBlocking {
        val state = VoiceConfigurationState("en", VoiceExperienceSettings(), true, listOf("en"))
        var foreground = true; var adult = true
        val tools = VoiceConfigurationTools({ state }, { _, _ -> fail("Invalid request reached owner") }, { foreground }, { adult })
        val read = JSONObject((tools.execute(ToolCall("read", "halo_get_voice_configuration", emptyMap())) as ToolExecutionResult.Success).output)
        val base = mapOf("revision" to read.getLong("revision"), "snapshot" to read.getString("snapshot"))
        listOf(mapOf("locale" to "xx"), mapOf("synthesisFallback" to "yes"), mapOf("playTest" to true),
            mapOf("kokoroVoiceId" to 0), mapOf("readingSpeed" to 9.0), mapOf("scopedControlAction" to "LaunchApp")).forEach {
            assertTrue(tools.execute(ToolCall("invalid", "halo_set_voice_configuration", base + it)) is ToolExecutionResult.Rejected)
        }
        foreground = false
        assertTrue(tools.execute(ToolCall("hidden", "halo_set_voice_configuration", base + ("locale" to "en"))) is ToolExecutionResult.Rejected)
        foreground = true; adult = false
        assertTrue(tools.execute(ToolCall("kids", "halo_set_voice_configuration", base + ("locale" to "en"))) is ToolExecutionResult.Rejected)
    }
}
