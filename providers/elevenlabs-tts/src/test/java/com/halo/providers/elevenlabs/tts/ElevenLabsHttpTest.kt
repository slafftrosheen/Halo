package com.halo.providers.elevenlabs.tts

import com.halo.sdk.provider.Credential
import com.halo.sdk.provider.SpeechRequest
import com.halo.sdk.provider.SpeechProviderException
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ElevenLabsHttpTest {
    private class MemoryStorage : Storage {
        private val values = ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private val models = """[{"model_id":"tts-test","name":"Test TTS","can_do_text_to_speech":true},{"model_id":"asr-test","can_do_text_to_speech":false}]"""

    @Test fun discoveryAndPcmSynthesisFollowActualHttpContract() = runBlocking {
        val server = MockWebServer()
        server.start()
        val provider = ElevenLabsTtsProvider(MemoryStorage(), server.url("/").toString())
        try {
            provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "test-placeholder"))
            server.enqueue(MockResponse().setBody(models))
            server.enqueue(MockResponse().setBody("""{"voices":[{"voice_id":"voice-test","name":"Test"}],"has_more":false}"""))
            val catalogue = provider.refreshCatalogue()
            assertEquals(listOf("tts-test"), catalogue.models.map { it.id })
            assertEquals("/v1/models", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
            val voiceRequest = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertTrue(voiceRequest.path!!.startsWith("/v2/voices?"))
            assertEquals("test-placeholder", voiceRequest.getHeader("xi-api-key"))
            provider.selectVoice("voice-test")
            provider.selectModel("tts-test")
            server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1, 0, 2, 0))))
            val pcm = provider.synthesizeToPcm(SpeechRequest(text = "test"))!!
            assertEquals(24000, pcm.sampleRateHz)
            assertEquals(4, pcm.chunks.sumOf { it.size })
            assertTrue(provider.state.value.ready)
            assertEquals("/v1/text-to-speech/voice-test?output_format=pcm_24000", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
            provider.saveVoiceStyle(0.4, 0.7, 1.1)
            assertFalse(provider.state.value.ready)
            assertTrue(provider.state.value.authenticated)
            provider.selectVoice("voice-test")
            provider.selectModel("tts-test")
            assertTrue(provider.state.value.authenticated)
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun keyChangeCancelsWholeUtteranceWithoutUsingNewKeyForLaterChunks() = runBlocking {
        val server = MockWebServer()
        server.start()
        val provider = ElevenLabsTtsProvider(MemoryStorage(), server.url("/").toString())
        try {
            provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "old-placeholder"))
            server.enqueue(MockResponse().setBody(models))
            server.enqueue(MockResponse().setBody("""{"voices":[{"voice_id":"voice-test","name":"Test"}],"has_more":false}"""))
            provider.refreshCatalogue()
            server.takeRequest(2, TimeUnit.SECONDS)
            server.takeRequest(2, TimeUnit.SECONDS)
            provider.selectVoice("voice-test")
            provider.selectModel("tts-test")
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val synthesis = async(Dispatchers.IO) { provider.synthesizeToPcm(SpeechRequest("a".repeat(4500))) }
            assertEquals("old-placeholder", server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("xi-api-key"))
            provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "new-placeholder"))
            try { withTimeout(5000) { synthesis.await() }; fail("Stale synthesis must not return PCM") }
            catch (_: CancellationException) { assertTrue(synthesis.isCancelled) }
            assertEquals(3, server.requestCount)
            assertEquals("new-placeholder", provider.credential()?.value)
            assertNull(provider.state.value.error)
            assertFalse(provider.state.value.ready)
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun duplicateModelsAreCollapsedAndMalformedRefreshKeepsCachedChoices() = runBlocking {
        val server = MockWebServer()
        server.start()
        val provider = ElevenLabsTtsProvider(MemoryStorage(), server.url("/").toString())
        try {
            provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "test-placeholder"))
            server.enqueue(MockResponse().setBody("""[{"model_id":"tts-test","can_do_text_to_speech":true},{"model_id":"tts-test","can_do_text_to_speech":true}]"""))
            server.enqueue(MockResponse().setBody("""{"voices":[],"has_more":false}"""))
            val cached = provider.refreshCatalogue()
            assertEquals(1, cached.models.size)
            server.enqueue(MockResponse().setBody("private malformed response"))
            try { provider.refreshCatalogue(); fail("Expected a safe discovery failure") }
            catch (expected: SpeechProviderException) {
                assertFalse(expected.message!!.contains("private malformed response"))
                assertEquals(expected.message, provider.state.value.error)
            }
            assertEquals(cached, provider.cachedCatalogue())
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun stoppingRequestDoesNotBecomeNetworkFailureOrChangeProviderReadiness() = runBlocking {
        val server = MockWebServer()
        server.start()
        val provider = ElevenLabsTtsProvider(MemoryStorage(), server.url("/").toString())
        try {
            provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "test-placeholder"))
            server.enqueue(MockResponse().setBody(models))
            server.enqueue(MockResponse().setBody("""{"voices":[{"voice_id":"voice-test","name":"Test"}],"has_more":false}"""))
            provider.refreshCatalogue()
            server.takeRequest(2, TimeUnit.SECONDS)
            server.takeRequest(2, TimeUnit.SECONDS)
            provider.selectVoice("voice-test")
            provider.selectModel("tts-test")
            server.enqueue(MockResponse().setBody(okio.Buffer().write(byteArrayOf(1, 0))))
            provider.synthesizeToPcm(SpeechRequest("first"))
            server.takeRequest(2, TimeUnit.SECONDS)
            assertTrue(provider.state.value.ready)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val stopped = async(Dispatchers.IO) { provider.synthesizeToPcm(SpeechRequest("second")) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            provider.stopCurrent()
            try { withTimeout(5000) { stopped.await() }; fail("Stopped request must be cancelled") }
            catch (_: CancellationException) { assertTrue(stopped.isCancelled) }
            assertNull(provider.state.value.error)
            assertNull(provider.state.value.errorCode)
            assertTrue(provider.state.value.ready)
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun missingKeyPermissionsAreActionableAndDoNotExposeServerMessage() = runBlocking {
        val server = MockWebServer()
        server.start()
        val provider = ElevenLabsTtsProvider(MemoryStorage(), server.url("/").toString())
        try {
            provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "test-placeholder"))
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":{"status":"missing_permissions","message":"private server text"}}"""))
            assertFalse(provider.health())
            val error = provider.state.value.error!!
            assertTrue(error.contains("Models (HTTP 401)"))
            assertTrue(error.contains("Models Read"))
            assertFalse(error.contains("private server text"))
            assertFalse(error.contains("test-placeholder"))
        } finally { provider.close(); server.shutdown() }
    }

    @Test fun voiceFailureStillExposesSuccessfullyFetchedModels() = runBlocking {
        val server = MockWebServer()
        server.start()
        val provider = ElevenLabsTtsProvider(MemoryStorage(), server.url("/").toString())
        try {
            provider.setCredential(Credential(ElevenLabsTtsProvider.ID, "test-placeholder"))
            server.enqueue(MockResponse().setBody(models))
            server.enqueue(MockResponse().setResponseCode(403))
            try { provider.refreshCatalogue(); fail("Expected voice access failure") }
            catch (expected: SpeechProviderException) { assertTrue(expected.message!!.contains("Voices (HTTP 403)")) }
            assertEquals("tts-test", provider.state.value.catalogue!!.models.single().id)
            assertTrue(provider.state.value.catalogue!!.stale)
            assertFalse(provider.state.value.ready)
        } finally { provider.close(); server.shutdown() }
    }
}
