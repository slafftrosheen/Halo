package com.halo.app.di.tools

import com.halo.app.ai.*
import com.halo.app.di.tools.host.OpenRouterSpeechTools
import com.halo.providers.openrouter.OpenRouterSpeechProvider
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class OpenRouterSpeechToolsTest {
    @Test fun registeredOperationsHaveClosedContractsVersionGuardsAndNoReplay() {
        val definitions = OpenRouterSpeechTools.definitionsList()
        val descriptors = definitions.map { requireNotNull(HaloToolDescriptors.assistant(it)) }
        val registry = ToolRegistry(emptyList())
        val executor = OpenRouterSpeechTools({ error("Review must not initialize providers") }, { false })
        registry.registerDynamic(executor, descriptors)
        assertEquals(emptyList<ToolOperabilityProblem>(), ToolOperabilityReview.inspect(
            listOf(ToolFeatureOperability("openrouter-speech", OpenRouterSpeechTools.NAMES)), registry.descriptors()))
        val save = descriptors.single { it.name == OpenRouterSpeechTools.SAVE }
        assertEquals(ConfirmationPolicy.ALWAYS, save.security.confirmation)
        assertEquals(ReplaySafety.NEVER_REPLAY, save.security.replaySafety)
        assertEquals(setOf("revision", "sourceVersion"), save.operability!!.sourceVersionArguments)
    }
    @Test fun sharedOwnerSavesOnlyObservedPowerVoiceAndRejectsStaleOrLocalRequests() = runBlocking {
        val server = MockWebServer(); server.start()
        val backing = Memory().apply { putString(OpenRouterSpeechProvider.KEY, "fixture-key") }
        val modes = PrivacyModeStore(backing)
        val provider = OpenRouterSpeechProvider(modes.storage, { error("Configuration must not synthesize speech") }, baseUrl = server.url("/").toString())
        val control = OpenRouterSpeechControl(provider, modes, { true }) { mode, profile, expected ->
            modes.edit(mode) { check(modes.revision == expected); profile }
        }
        val tools = OpenRouterSpeechTools({ control }, { true })
        fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("test", name, args)
        try {
            assertTrue(tools.execute(call(OpenRouterSpeechTools.REFRESH)) is ToolExecutionResult.Failed)
            assertEquals(0, server.requestCount)
            modes.acknowledgeSharing(); modes.set(PrivacyMode.CLOUD)
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"vendor/speech","name":"Speech","supported_voices":["supported-voice"],"architecture":{"output_modalities":["speech"]}}]}"""))
            assertTrue(tools.execute(call(OpenRouterSpeechTools.REFRESH)) is ToolExecutionResult.Success)
            val read = tools.execute(call(OpenRouterSpeechTools.READ, mapOf("offset" to 0))) as ToolExecutionResult.Success
            assertFalse(read.output.contains("fixture-key"))
            val state = JSONObject(read.output)
            assertEquals("supported-voice", state.getJSONArray("models").getJSONObject(0).getJSONArray("voices").getString(0))
            val args = mapOf("modelId" to "vendor/speech", "voiceId" to "supported-voice",
                "revision" to state.getLong("revision"), "sourceVersion" to state.getString("sourceVersion"))
            assertTrue(tools.execute(call(OpenRouterSpeechTools.SAVE, args + ("voiceId" to "invented"))) is ToolExecutionResult.Rejected)
            val saved = tools.execute(call(OpenRouterSpeechTools.SAVE, args)) as ToolExecutionResult.Success
            assertFalse(JSONObject(saved.output).getBoolean("speechPlayed"))
            assertEquals(OpenRouterSpeechProvider.ID, modes.storage.getString("speech_tts_provider"))
            assertTrue(tools.execute(call(OpenRouterSpeechTools.SAVE, args)) is ToolExecutionResult.Failed)
            modes.set(PrivacyMode.PRIVATE)
            assertNotEquals(OpenRouterSpeechProvider.ID, modes.storage.getString("speech_tts_provider"))
            assertEquals(1, server.requestCount)
        } finally { provider.close(); server.shutdown() }
    }
    @Test fun permissionAndUnknownArgumentsStopBeforeOwnerInitialization() = runBlocking {
        val denied = OpenRouterSpeechTools({ error("Must not access settings") }, { false })
        assertTrue(denied.execute(ToolCall("no", OpenRouterSpeechTools.READ, mapOf("offset" to 0))) is ToolExecutionResult.Rejected)
        val guarded = OpenRouterSpeechTools({ error("Must reject malformed arguments") }, { true })
        assertTrue(guarded.execute(ToolCall("no", OpenRouterSpeechTools.SAVE, mapOf("apiKey" to "no"))) is ToolExecutionResult.Rejected)
    }
    private class Memory : Storage {
        private val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
}
