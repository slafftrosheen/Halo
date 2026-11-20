package com.halo.app.di.tools

import com.halo.app.ai.*
import com.halo.app.di.tools.host.OpenRouterRecognitionTools
import com.halo.providers.openrouter.OpenRouterRecognitionProvider
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
class OpenRouterRecognitionToolsTest {
    @Test fun registeredOperationsHaveClosedContractsVersionGuardsAndNoReplay() {
        val definitions = OpenRouterRecognitionTools.definitionsList()
        val descriptors = definitions.map { requireNotNull(HaloToolDescriptors.assistant(it)) }
        val registry = ToolRegistry(emptyList())
        val executor = OpenRouterRecognitionTools({ error("Review must not initialize providers") }, { false })
        registry.registerDynamic(executor, descriptors)
        assertEquals(emptyList<ToolOperabilityProblem>(), ToolOperabilityReview.inspect(
            listOf(ToolFeatureOperability("openrouter-transcription", OpenRouterRecognitionTools.NAMES)), registry.descriptors()))
        val save = descriptors.single { it.name == OpenRouterRecognitionTools.SAVE }
        assertEquals(ConfirmationPolicy.ALWAYS, save.security.confirmation)
        assertEquals(ReplaySafety.NEVER_REPLAY, save.security.replaySafety)
        assertEquals(setOf("revision", "sourceVersion"), save.operability!!.sourceVersionArguments)
    }
    @Test fun sharedOwnerSavesOnlyObservedPowerTranscriberAndRejectsStaleOrLocalRequests() = runBlocking {
        val server = MockWebServer(); server.start()
        val backing = Memory().apply { putString(com.halo.providers.openrouter.OpenRouterSpeechProvider.KEY, "fixture-key") }
        val modes = PrivacyModeStore(backing)
        val provider = OpenRouterRecognitionProvider(modes.storage, { "en" }, modes, baseUrl = server.url("/").toString())
        val control = OpenRouterRecognitionControl(provider, modes, { true }) { mode, profile, expected ->
            modes.edit(mode) { check(modes.revision == expected); profile }
        }
        val tools = OpenRouterRecognitionTools({ control }, { true })
        fun call(name: String, args: Map<String, Any> = emptyMap()) = ToolCall("test", name, args)
        try {
            assertTrue(tools.execute(call(OpenRouterRecognitionTools.REFRESH)) is ToolExecutionResult.Failed)
            assertEquals(0, server.requestCount)
            modes.acknowledgeSharing(); modes.set(PrivacyMode.CLOUD)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"data":[{"id":"vendor/transcription","name":"Speech","architecture":{"output_modalities":["transcription"]}}]}"""))
            assertTrue(tools.execute(call(OpenRouterRecognitionTools.REFRESH)) is ToolExecutionResult.Success)
            val read = tools.execute(call(OpenRouterRecognitionTools.READ, mapOf("offset" to 0))) as ToolExecutionResult.Success
            assertFalse(read.output.contains("fixture-key"))
            val state = JSONObject(read.output)
            val args = mapOf("modelId" to "vendor/transcription",
                "revision" to state.getLong("revision"), "sourceVersion" to state.getString("sourceVersion"))
            assertTrue(tools.execute(call(OpenRouterRecognitionTools.SAVE, args + ("modelId" to "invented"))) is ToolExecutionResult.Rejected)
            val saved = tools.execute(call(OpenRouterRecognitionTools.SAVE, args)) as ToolExecutionResult.Success
            assertFalse(JSONObject(saved.output).getBoolean("audioSent"))
            assertEquals(OpenRouterRecognitionProvider.ID, modes.storage.getString("speech_stt_provider"))
            assertTrue(tools.execute(call(OpenRouterRecognitionTools.SAVE, args)) is ToolExecutionResult.Failed)
            modes.set(PrivacyMode.PRIVATE)
            assertNotEquals(OpenRouterRecognitionProvider.ID, modes.storage.getString("speech_stt_provider"))
            assertEquals(1, server.requestCount)
        } finally { provider.close(); server.shutdown() }
    }
    @Test fun permissionAndUnknownArgumentsStopBeforeOwnerInitialization() = runBlocking {
        val denied = OpenRouterRecognitionTools({ error("Must not access settings") }, { false })
        assertTrue(denied.execute(ToolCall("no", OpenRouterRecognitionTools.READ, mapOf("offset" to 0))) is ToolExecutionResult.Rejected)
        val guarded = OpenRouterRecognitionTools({ error("Must reject malformed arguments") }, { true })
        assertTrue(guarded.execute(ToolCall("no", OpenRouterRecognitionTools.SAVE, mapOf("apiKey" to "no"))) is ToolExecutionResult.Rejected)
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
