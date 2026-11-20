package com.halo.providers.openai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatGptSubscriptionProviderTest {
    @Test fun savedAccountIsRoutableAfterRestartWithoutManualConnectionTest() = runBlocking {
        Fixture().use { f ->
            assertEquals(ProviderReadiness.ConfiguredCold, f.provider.getStatus().readiness)
            assertEquals(0, f.server.requestCount)
            f.account.disconnect(revokeRemote = false)
            assertEquals(ProviderStatus.State.UNCONFIGURED, f.provider.getStatus().state)
        }
    }

    @Test fun imageOnlyRequestsAndAccountModalityEvidenceArePreserved() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setBody("""{"models":[{"visibility":"list","slug":"vision","input_modalities":["text","image"]},{"visibility":"list","slug":"unknown"}]}"""))
            val models = f.provider.listModels()
            assertTrue(AiCapability.IMAGE in f.provider.getCapabilities())
            assertEquals(CapabilityEvidence.AUTHORITATIVE_METADATA, models[0].capabilityEvidence[AiCapability.IMAGE])
            assertFalse(AiCapability.IMAGE in models[1].inputCapabilities)
            val body = f.provider.body(AiRequest(image = byteArrayOf(1, 2), mimeTypes = listOf("image/png")), AiContext.EMPTY, "vision")
            val part = body.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0)
            assertEquals("input_image", part.getString("type"))
            assertEquals("data:image/png;base64,AQI=", part.getString("image_url"))
            val continuation = f.provider.body(AiRequest(image = byteArrayOf(1), appendTextAsUserMessage = false), AiContext.EMPTY, "vision")
            assertEquals(0, continuation.getJSONArray("input").length())
        }
    }
    private class Memory : Storage {
        override fun getString(key: String, defaultValue: String?): String? { check(key != "openai_api_key"); return defaultValue }
        override fun putString(key: String, value: String?) = Unit
        override fun getBoolean(key: String, defaultValue: Boolean) = defaultValue
        override fun putBoolean(key: String, value: Boolean) = Unit
        override fun getInt(key: String, defaultValue: Int) = defaultValue
        override fun putInt(key: String, value: Int) = Unit
        override fun remove(key: String) = Unit
        override fun clear() = Unit
    }
    private class Fixture : AutoCloseable {
        val server = MockWebServer().apply { start() }
        val secrets = object : ProviderSecretStore {
            var record: String? = JSONObject().put("schema", 1).put("subject", "fixture-subject").put("clientId", "fixture-client")
                .put("tokens", JSONObject().put("scope", ChatGptAccount.SCOPES).put("accessToken", "fixture-oauth")
                    .put("refreshToken", "fixture-refresh").put("expiresAt", System.currentTimeMillis() + 3_600_000)).toString()
            override suspend fun get(key: String) = record
            override suspend fun put(key: String, value: String?) { record = value }
            override suspend fun remove(key: String) { record = null }
        }
        val client = ChatGptSubscriptionProvider.defaultClient()
        val account = ChatGptAccount(secrets, client)
        val provider = ChatGptSubscriptionProvider(account, Memory(), client, server.url("/").toString().trimEnd('/'))
        override fun close() { server.shutdown(); client.connectionPool.evictAll() }
        fun stream(vararg data: String) = server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
            .setBody(data.joinToString("") { "data: $it\n\n" }))
    }
    @Test fun usesAccountCatalogOrderAndPublicResponsesWithNoApiKeyOrUnsupportedFields() = runBlocking {
        Fixture().use { f ->
            f.server.enqueue(MockResponse().setBody("""{"models":[{"visibility":"list","slug":"z","display_name":"Z"},{"visibility":"hidden","slug":"x"},{"visibility":"list","slug":"a","display_name":"A"}]}"""))
            assertEquals(listOf("z", "a"), f.provider.listModels().map { it.id })
            assertEquals("/models", f.server.takeRequest().path)
            f.stream("""{"type":"response.output_text.delta","delta":"Hello"}""",
                """{"type":"response.completed","response":{"status":"completed","model":"z","output":[{"type":"message","content":[{"type":"output_text","text":"Hello"}]}]}}""")
            val response = f.provider.generate(AiRequest("Hi", modelId = "z", maxOutputTokens = 10, temperatureOverride = 0.2), AiContext.EMPTY)
            assertEquals("Hello", response.text)
            val request = f.server.takeRequest()
            assertEquals("/responses", request.path)
            assertEquals("Bearer fixture-oauth", request.getHeader("Authorization"))
            val body = JSONObject(request.body.readUtf8())
            assertFalse(body.getBoolean("store")); assertTrue(body.getBoolean("stream"))
            assertFalse(body.has("max_output_tokens")); assertFalse(body.has("temperature")); assertFalse(body.has("previous_response_id"))
        }
    }
    @Test fun partialTextAndSubscriptionFailuresAreNeverReportedAsCompleted() = runBlocking {
        Fixture().use { f ->
            for (terminal in listOf(
                """{"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}}""",
                """{"type":"response.incomplete"}""", "[DONE]")) {
                f.stream("""{"type":"response.output_text.delta","delta":"Partial"}""", terminal)
                val events = f.provider.stream(AiRequest("Hi", modelId = "z"), AiContext.EMPTY).toList()
                assertTrue(events.any { it is AiStreamEvent.Token })
                assertTrue(events.last() is AiStreamEvent.Failed)
                assertTrue(events.none { it is AiStreamEvent.Completed })
                if (terminal.contains("subscription_sharing_usage_limit_exceeded"))
                    assertEquals(AiErrorType.RATE_LIMITED, ((events.last() as AiStreamEvent.Failed).error as AiProviderException).type)
            }
        }
    }
    @Test fun acceptsCompletedEventStreamWithoutContentTypeAndRequestsStreamingExplicitly() = runBlocking {
        Fixture().use { f ->
            // Real subscription endpoint returned HTTP 200 without Content-Type on the phone.
            f.server.enqueue(MockResponse().setBody("data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"HALO\"}]}]}}\n\n"))
            assertEquals("HALO", f.provider.generate(AiRequest("Reply exactly HALO", modelId = "z"), AiContext.EMPTY).text)
            assertEquals("text/event-stream", f.server.takeRequest().getHeader("Accept"))
        }
    }
    @Test fun missingContentTypeDoesNotAcceptNonStreamBodiesOrIncompleteStreams() = runBlocking {
        Fixture().use { f ->
            for (body in listOf("{\"status\":\"completed\",\"output_text\":\"HALO\"}", "<html>HALO</html>",
                "data: {\"type\":\"response.output_text.delta\",\"delta\":\"HALO\"}\n\n")) {
                f.server.enqueue(MockResponse().setBody(body))
                val events = f.provider.stream(AiRequest("Reply exactly HALO", modelId = "z"), AiContext.EMPTY).toList()
                assertTrue(events.last() is AiStreamEvent.Failed)
                assertFalse(events.any { it is AiStreamEvent.Completed })
            }
            f.server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"HALO\"}]}]}}\n\n"))
            val events = f.provider.stream(AiRequest("Reply exactly HALO", modelId = "z"), AiContext.EMPTY).toList()
            assertTrue(events.last() is AiStreamEvent.Failed)
            assertFalse(events.any { it is AiStreamEvent.Completed })
        }
    }
    @Test fun namespacedToolsRoundTripThroughTheExistingRuntimeContract() = runBlocking {
        Fixture().use { f ->
            val tool = ToolDefinition("halo_test", "halo_test", "Read a synthetic value", mapOf("type" to "object"))
            f.stream("""{"type":"response.completed","response":{"status":"completed","output":[{"type":"function_call","call_id":"call-1","namespace":"halo","name":"halo_test","arguments":"{\"count\":2}"}]}}""")
            val reply = f.provider.generate(AiRequest("Test", modelId = "z"), AiContext(availableTools = listOf(tool)))
            assertEquals(ToolCall("call-1", "halo_test", mapOf("count" to 2)), reply.toolCalls.single())
            val sent = JSONObject(f.server.takeRequest().body.readUtf8())
            assertEquals("namespace", sent.getJSONArray("tools").getJSONObject(0).getString("type"))
            val next = f.provider.body(AiRequest(modelId = "z", appendTextAsUserMessage = false), AiContext(conversation = listOf(
                AIMessage(Role.SYSTEM, "Instructions"), AIMessage(Role.ASSISTANT, "", reply.toolCalls),
                AIMessage(Role.TOOL, "Result", toolCallId = "call-1"))), "z")
            val history = next.getJSONArray("input")
            assertEquals("developer", history.getJSONObject(0).getString("role"))
            assertEquals("halo", history.getJSONObject(1).getString("namespace"))
            assertEquals("call-1", history.getJSONObject(2).getString("call_id"))
        }
    }
}
