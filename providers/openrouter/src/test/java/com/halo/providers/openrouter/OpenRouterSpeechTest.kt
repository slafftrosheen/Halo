package com.halo.providers.openrouter

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OpenRouterSpeechTest {
    private class Memory : Storage {
        val values = ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private val catalogue = """{"data":[{"id":"vendor/speech","name":"Speech","architecture":{"output_modalities":["speech"]}},{"id":"vendor/chat","architecture":{"output_modalities":["text"]}}]}"""
    private val pcm = PcmSpeech(24000, 1, PcmEncoding.SIGNED_16_LE, listOf(byteArrayOf(1, 0)))

    @Test fun pcmOnlyModelsPreserveTheirDocumentedSampleRate() = runBlocking {
        val server = MockWebServer(); server.start()
        val disk = Memory().apply { putString(OpenRouterSpeechProvider.KEY, "fixture-key") }
        val provider = OpenRouterSpeechProvider(disk, { error("PCM must not reach MP3 decoder") }, baseUrl = server.url("/").toString())
        try {
            for ((model, rate) in listOf(
                "google/gemini-3.8-flash-lite-tts" to 24_000,
                "google/gemini-3.8-flash-tts" to 24_000,
                "google/gemini-3.1-flash-tts-preview" to 24_000,
                "google/gemini-2.5-flash-tts" to 24_000,
                "google/gemini-2.5-pro-tts" to 24_000,
                "heygen/heygen-voice-1" to 44_100,
                "heygen/heygen-voice-1-turbo" to 44_100,
                "heygen/heygen-voice-1-turbo:free" to 44_100,
            )) {
                val bytes = byteArrayOf(0, 0, -1, 127, 0, -128)
                server.enqueue(MockResponse().setHeader("Content-Type", "audio/pcm").setBody(okio.Buffer().write(bytes)))
                val voice = if (model.startsWith("heygen/")) "nadine" else "Kore"
                val result = provider.synthesizeToPcm(SpeechRequest("Hello", modelId = model, voiceId = voice))
                val body = JSONObject(server.takeRequest().body.readUtf8())
                assertEquals("pcm", body.getString("response_format"))
                assertEquals(model, body.getString("model"))
                assertEquals(voice, body.getString("voice"))
                assertEquals(rate, result.sampleRateHz); assertEquals(1, result.channelCount)
                assertEquals(PcmEncoding.SIGNED_16_LE, result.encoding); assertArrayEquals(bytes, result.chunks.single())
            }
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun streamsShortParagraphsTogetherWithoutIndividualSentenceRequests() = runBlocking {
        val server = MockWebServer().apply { start() }
        val disk = Memory().apply { putString(OpenRouterSpeechProvider.KEY, "fixture-key") }
        val provider = OpenRouterSpeechProvider(disk, { pcm }, baseUrl = server.url("/").toString())
        try {
            val input = "First sentence. Still the first paragraph.\n\nThis is the second paragraph."
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("fixture"))
            assertEquals(1, provider.streamPcm(SpeechRequest(input, modelId = "vendor/speech", voiceId = "A")).toList().size)
            assertEquals(1, server.requestCount)
            assertEquals(input, JSONObject(server.takeRequest().body.readUtf8()).getString("input"))
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun malformedOrUnexpectedPcmFailsClosed() = runBlocking {
        val server = MockWebServer(); server.start()
        val disk = Memory().apply { putString(OpenRouterSpeechProvider.KEY, "fixture-key") }
        val provider = OpenRouterSpeechProvider(disk, { error("Must not decode invalid audio") }, baseUrl = server.url("/").toString())
        try {
            for ((model, type, bytes) in listOf(
                Triple("google/gemini-3.8-flash-lite-tts", "audio/pcm", byteArrayOf(1)),
                Triple("google/gemini-3.8-flash-lite-tts", "audio/pcm", "RIFFdata".toByteArray()),
                Triple("google/gemini-3.8-flash-lite-tts", "application/json", "{}".toByteArray()),
                Triple("vendor/unknown", "audio/pcm", byteArrayOf(0, 0)))) {
                server.enqueue(MockResponse().setHeader("Content-Type", type).setBody(okio.Buffer().write(bytes)))
                try { provider.synthesizeToPcm(SpeechRequest("Hello", modelId = model, voiceId = "voice")); fail("Invalid response must fail") }
                catch (error: SpeechProviderException) { assertEquals(SpeechErrorCode.INVALID_AUDIO_RESPONSE, error.code) }
            }
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun catalogVoicesAreDiscoverableAndUnsupportedSelectionsCannotBeSaved() = runBlocking {
        val server = MockWebServer(); server.start()
        val disk = Memory().apply { putString(OpenRouterSpeechProvider.KEY, "fixture-key") }
        val provider = OpenRouterSpeechProvider(disk, { pcm }, baseUrl = server.url("/").toString())
        try {
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"vendor/speech","name":"Speech","supported_voices":["Kore","Zephyr","Kore"],"architecture":{"output_modalities":["speech"]}}]}"""))
            assertEquals(listOf("Kore", "Zephyr"), provider.refreshModels().single().voices)
            try { provider.configure("vendor/speech", "invented"); fail("Unsupported voice must fail") }
            catch (_: IllegalArgumentException) { }
            assertNull(disk.getString(OpenRouterSpeechProvider.VOICE))
            provider.configure("vendor/speech", "Kore")
            assertEquals("Kore", disk.getString(OpenRouterSpeechProvider.VOICE))
            assertEquals(1, server.requestCount)
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun usesExistingOpenRouterKeyAndSpeechEndpointWithExplicitModelAndVoice() = runBlocking {
        val server = MockWebServer(); server.start()
        val disk = Memory().apply { putString("openrouter_api_key", "fixture-key") }
        var decoded = 0
        val provider = OpenRouterSpeechProvider(disk, { bytes -> assertEquals("fixture-mp3", bytes.decodeToString()); decoded++; pcm }, baseUrl = server.url("/").toString())
        try {
            server.enqueue(MockResponse().setBody(catalogue))
            assertEquals(listOf("vendor/speech"), provider.refreshModels().map { it.id })
            assertEquals("/models?output_modalities=speech", server.takeRequest().path)
            provider.configure("vendor/speech", "documented-voice")
            val pinned = provider.pinSpeechRequest(SpeechRequest("Hello"))
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("fixture-mp3"))
            assertEquals(pcm, provider.synthesizeToPcm(pinned))
            val request = server.takeRequest()
            assertEquals("/audio/speech", request.path)
            assertEquals("Bearer fixture-key", request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals(setOf("model", "input", "voice", "response_format"), body.keys().asSequence().toSet())
            assertEquals("mp3", body.getString("response_format"))
            assertEquals("documented-voice", body.getString("voice"))
            assertEquals(1, decoded)
            assertNull(disk.getString("elevenlabs_api_key"))
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun jsonAndHttpErrorsAreNeverDecodedOrExposedAsAudio() = runBlocking {
        val server = MockWebServer(); server.start()
        val disk = Memory().apply { putString("openrouter_api_key", "fixture-key") }
        val provider = OpenRouterSpeechProvider(disk, { error("Must not decode") }, baseUrl = server.url("/").toString())
        try {
            server.enqueue(MockResponse().setBody(catalogue)); provider.refreshModels(); server.takeRequest()
            provider.configure("vendor/speech", "voice")
            for (status in listOf(200, 401, 402, 429)) {
                server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody("private server body"))
                try { provider.synthesizeToPcm(provider.pinSpeechRequest(SpeechRequest("Hi"))); fail("Expected failure") }
                catch (error: SpeechProviderException) { assertFalse(error.message.orEmpty().contains("private server body")) }
            }
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun stopCancelsTheActualNetworkCall() = runBlocking {
        val server = MockWebServer(); server.start()
        val disk = Memory().apply { putString("openrouter_api_key", "fixture-key") }
        val provider = OpenRouterSpeechProvider(disk, { pcm }, baseUrl = server.url("/").toString())
        try {
            server.enqueue(MockResponse().setBody(catalogue)); provider.refreshModels(); server.takeRequest()
            provider.configure("vendor/speech", "voice")
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = async(Dispatchers.IO) { provider.synthesizeToPcm(provider.pinSpeechRequest(SpeechRequest("Hi"))) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)); provider.stopCurrent()
            try { withTimeout(5000) { job.await() }; fail("Must cancel") } catch (_: CancellationException) { }
        } finally { provider.close(); server.shutdown() }
    }
}
