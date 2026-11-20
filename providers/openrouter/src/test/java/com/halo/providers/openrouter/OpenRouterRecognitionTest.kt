package com.halo.providers.openrouter

import com.halo.sdk.host.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class OpenRouterRecognitionTest {
    private class Memory : Storage {
        val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }
    private class Policy : RemoteDispatchPolicy {
        override var revision = 1L
        var allowed = true
        override fun acquire(request: RemoteDispatchRequest, expectedRevision: Long, recordActivity: Boolean, cancel: () -> Unit): RemoteDispatchLease {
            if (!allowed || revision != expectedRevision) throw RemoteDispatchBlocked()
            return object : RemoteDispatchLease {
                override fun requireCurrent() { if (!allowed || revision != expectedRevision) throw RemoteDispatchBlocked() }
                override fun finish(outcome: RemoteDispatchOutcome, providerReportedCostUsd: String?) {}
            }
        }
    }
    private val sample = AudioBuffer(listOf(AudioFrame(shortArrayOf(0, 1234, -1234), 16000, 1, 0)), "fixture")
    private fun disk() = Memory().apply {
        putString(OpenRouterSpeechProvider.KEY, "fixture-key")
        putString(OpenRouterRecognitionProvider.MODEL, "vendor/transcription")
    }
    private fun provider(disk: Memory, policy: Policy, server: MockWebServer) = OpenRouterRecognitionProvider(disk, { "en-US" }, policy,
        OpenRouterSpeechProvider.defaultClient().withRemoteDispatchPolicy(policy) { request ->
            RemoteDispatchRequest(OpenRouterRecognitionProvider.ID, RemoteOperation.HOSTED_SPEECH,
                setOf(if (request.method == "GET") RemoteDataCategory.ACCOUNT_METADATA else RemoteDataCategory.AUDIO))
        }, server.url("/").toString())
    @Test fun sharedKeyDedicatedCatalogAndMultipartTranscriptionPreservePcm() = runBlocking {
        val server = MockWebServer(); server.start()
        val provider = provider(disk(), Policy(), server)
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"data":[{"id":"vendor/transcription","name":"Transcription","architecture":{"output_modalities":["transcription"]}},{"id":"vendor/chat","architecture":{"output_modalities":["text"]}}]}"""))
            assertEquals(listOf("vendor/transcription"), provider.refreshModels().map { it.id })
            assertEquals("/models?output_modalities=transcription", server.takeRequest().path)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"text":"Hello Halo"}"""))
            val result = withContext(provider.captureContext().context) { provider.recognize(sample) }
            assertEquals("Hello Halo", result.text); assertNull(result.confidence)
            val request = server.takeRequest()
            assertEquals("/audio/transcriptions", request.path)
            assertEquals("Bearer fixture-key", request.getHeader("Authorization"))
            val body = request.body.readByteArray()
            assertTrue(Regex("name=\"model\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\nvendor/transcription\\r\\n").containsMatchIn(body.decodeToString()))
            assertTrue(Regex("name=\"language\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\nen\\r\\n").containsMatchIn(body.decodeToString()))
            val wav = OpenRouterRecognitionProvider.wav(sample)
            val start = body.asList().windowed(wav.size).indexOf(wav.asList())
            assertTrue(start >= 0)
            val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(16000, header.getInt(24)); assertEquals(1, header.getShort(22).toInt())
            assertEquals(6, header.getInt(40)); assertEquals(1234, header.getShort(46).toInt())
        } finally { provider.close(); server.shutdown() }
    }
    @Test fun localCaptureMissingAuthorityAndChangedRevisionNeverUpload() = runBlocking {
        val server = MockWebServer(); server.start(); val policy = Policy()
        val provider = provider(disk(), policy, server)
        try {
            try { provider.recognize(sample); fail("Missing capture authority") } catch (_: RemoteDispatchBlocked) {}
            val captured = provider.captureContext()
            policy.revision++
            try { withContext(captured.context) { provider.recognize(sample) }; fail("Stale capture") } catch (_: RemoteDispatchBlocked) {}
            policy.allowed = false
            try { withContext(provider.captureContext().context) { provider.recognize(sample) }; fail("Local policy") } catch (_: java.io.IOException) {}
            assertEquals(0, server.requestCount)
        } finally { provider.close(); server.shutdown() }
    }
    @Test fun cancellationStopsActualUploadAndNoLateTranscriptReturns() = runBlocking {
        val server = MockWebServer(); server.start(); val provider = provider(disk(), Policy(), server)
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val context = provider.captureContext().context
            val job = async(Dispatchers.IO + context) { provider.recognize(sample) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)); provider.stopCurrent()
            try { withTimeout(5000) { job.await() }; fail("Must cancel") } catch (_: CancellationException) {}
        } finally { provider.close(); server.shutdown() }
    }
    @Test fun errorsAreBoundedAndNeverExposeServerText() = runBlocking {
        val server = MockWebServer(); server.start(); val provider = provider(disk(), Policy(), server)
        try {
            for (status in listOf(401, 402, 429, 500)) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("private server detail"))
                try { withContext(provider.captureContext().context) { provider.recognize(sample) }; fail("Must fail") }
                catch (error: Exception) { assertFalse(error.message.orEmpty().contains("private server detail")) }
            }
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"text":42}"""))
            try { withContext(provider.captureContext().context) { provider.recognize(sample) }; fail("Invalid text") }
            catch (_: IllegalArgumentException) {}
        } finally { provider.close(); server.shutdown() }
    }
    @Test fun inconsistentOrOversizedAudioFailsBeforeNetworking() {
        for (audio in listOf(AudioBuffer(emptyList()), sample.copy(frames = sample.frames + AudioFrame(shortArrayOf(1), 24000, 1, 1)),
            AudioBuffer(listOf(AudioFrame(ShortArray(16000 * 121), 16000, 1, 0))))) {
            assertTrue(runCatching { OpenRouterRecognitionProvider.wav(audio) }.isFailure)
        }
    }
}
