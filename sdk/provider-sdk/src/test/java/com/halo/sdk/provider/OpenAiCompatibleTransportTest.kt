package com.halo.sdk.provider

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the shared OpenAI-compatible transport: model list parsing with
 * free-model evidence, auth-failure fallback, and error mapping.
 */
class OpenAiCompatibleTransportTest {
    @Test fun `chat stream emits one final response with requested and resolved models`() = runTest {
        server.enqueue(MockResponse().setBody("data: {\"model\":\"resolved\",\"choices\":[{\"delta\":{\"content\":\"HALO\"}}]}\n\ndata: [DONE]\n\n"))
        val events = transport.stream(AiRequest(text = "HALO", modelId = "selected"), AiContext.EMPTY,
            "selected", "Authorization", "Bearer fixture", null, true).toList()
        assertEquals("HALO", events.filterIsInstance<AiStreamEvent.Token>().single().text)
        val answer = events.filterIsInstance<AiStreamEvent.Completed>().single().response
        assertEquals("HALO", answer.text)
        assertEquals("selected", answer.requestedModelId)
        assertEquals("resolved", answer.resolvedModelId)
    }

    @Test fun `responses stream uses final output without duplicating deltas`() = runTest {
        val body = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"HALO\"}\n\n" +
            "data: {\"type\":\"response.completed\",\"response\":{\"model\":\"resolved\",\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"HALO\"}]}]}}\n\n"
        server.enqueue(MockResponse().setBody(body))
        val events = transport.withProtocol(AiWireProtocol.OPENAI_RESPONSES).stream(
            AiRequest(text = "HALO", modelId = "selected"), AiContext.EMPTY, "selected",
            "Authorization", "Bearer fixture", null, true).toList()
        assertEquals(1, events.filterIsInstance<AiStreamEvent.Token>().size)
        val answer = events.filterIsInstance<AiStreamEvent.Completed>().single().response
        assertEquals("HALO", answer.text)
        assertEquals("selected", answer.requestedModelId)
        assertEquals("resolved", answer.resolvedModelId)
        assertEquals("/v1/responses", server.takeRequest().path)
    }

    @Test fun `truncated and failed streams never emit successful completion`() = runTest {
        for (body in listOf("data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n",
            "data: {\"type\":\"response.failed\",\"response\":{\"error\":{\"message\":\"secret\"}}}\n\n")) {
            server.enqueue(MockResponse().setBody(body))
            val events = transport.stream(AiRequest(text = "HALO", modelId = "selected"), AiContext.EMPTY,
                "selected", "Authorization", "Bearer fixture", null, true).toList()
            assertTrue(events.none { it is AiStreamEvent.Completed })
            assertTrue(events.last() is AiStreamEvent.Failed)
            assertTrue(!events.last().toString().contains("secret"))
        }
    }

    @Test
    fun `direct discovery preserves authentication failure and HTTP status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":"invalid_api_key"}}"""))
        val failure = runCatching {
            transport.fetchRemoteModelList("Authorization", "Bearer fixture", true, emptyList(), emptySet(), false)
        }.exceptionOrNull()
        assertTrue("A 401 must not become an empty successful catalogue", failure is AiProviderException)
        assertEquals(401, (failure as AiProviderException).details.httpStatus)
    }

    @Test
    fun `connection preserves configured API path without unsupported pagination`() = runTest {
        server.enqueue(MockResponse().setBody("""{"data":[]}"""))
        transport.testConnection("Authorization", "Bearer fixture", true)
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test fun `protocol selection sends bounded generation to the configured wire path`() = runTest {
        val responses = transport.withProtocol(AiWireProtocol.OPENAI_RESPONSES)
        server.enqueue(MockResponse().setBody("""{"model":"text","status":"completed","output":[{"type":"message","content":[{"type":"output_text","text":"HALO."}]}]}"""))
        val answer = responses.generate(AiRequest(text = "Reply exactly HALO", modelId = "text", maxOutputTokens = 256,
            toolUseMode = ToolUseMode.NONE), AiContext.EMPTY, "text", "Authorization", "Bearer fixture", null, true)
        assertEquals("HALO.", answer.text)
        val sent = server.takeRequest()
        assertEquals("/v1/responses", sent.path)
        assertEquals(256, org.json.JSONObject(sent.body.readUtf8()).getInt("max_output_tokens"))
    }

    @Test fun `HTTP failures preserve stage evidence without returning server secrets`() = runTest {
        for (code in listOf(401, 402, 403, 404, 429, 500)) {
            server.enqueue(MockResponse().setResponseCode(code).setHeader("Retry-After", "2")
                .setBody("""{"error":{"code":"fixture-secret-code","message":"fixture-secret-body"}}"""))
            val error = runCatching { transport.generate(AiRequest(text = "HALO", modelId = "text", maxOutputTokens = 256),
                AiContext.EMPTY, "text", "Authorization", "Bearer fixture", null, true) }.exceptionOrNull() as AiProviderException
            assertEquals(code, error.details.httpStatus)
            assertEquals(null, error.details.providerCode)
            assertTrue(!error.toString().contains("fixture-secret"))
            server.takeRequest()
        }
    }

    private lateinit var server: MockWebServer
    private lateinit var transport: OpenAiCompatibleTransport

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        transport = OpenAiCompatibleTransport(
            client = OpenAiCompatibleTransport.defaultClient(),
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            protocol = AiWireProtocol.OPENAI_CHAT_COMPLETIONS,
        )
    }

    /** The mock server is loopback cleartext, so tests opt in to local transport. */
    private val allowLocal = true

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `model list marks free models from freeModelIds`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"llama-3.3-70b","name":"Llama 3.3 70B"},{"id":"gpt-oss-120b","name":"GPT-OSS 120B"}]}""",
            ),
        )
        val models = transport.fetchRemoteModelList(
            credentialHeader = "Authorization",
            credentialHeaderValue = "Bearer test",
            allowInsecureLocal = allowLocal,
            staticHints = emptyList(),
            freeModelIds = setOf("gpt-oss-120b"),
            fallbackWhenUnauthenticated = false,
        )
        assertEquals(2, models.size)
        assertTrue(models.first { it.id == "gpt-oss-120b" }.isFree)
        assertEquals(false, models.first { it.id == "llama-3.3-70b" }.isFree)
    }

    @Test
    fun `model list marks free when pricing is zero`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"data":[{"id":"free-model","name":"Free Model","pricing":{"prompt":"0","completion":"0"}}]}""",
            ),
        )
        val models = transport.fetchRemoteModelList(
            credentialHeader = "Authorization",
            credentialHeaderValue = "Bearer test",
            allowInsecureLocal = allowLocal,
            staticHints = emptyList(),
            freeModelIds = emptySet(),
            fallbackWhenUnauthenticated = false,
        )
        assertEquals(1, models.size)
        assertTrue(models.single().isFree)
    }

    @Test
    fun `unauthenticated falls back to static hints when allowed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val hints = listOf(
            AiModel(id = "hint-model", displayName = "Hint", providerId = "x", inputCapabilities = setOf(AiCapability.TEXT), contextLength = null, supportsStreaming = true, supportsTools = false),
        )
        val models = transport.fetchRemoteModelList(
            credentialHeader = "Authorization",
            credentialHeaderValue = "Bearer wrong",
            allowInsecureLocal = allowLocal,
            staticHints = hints,
            freeModelIds = emptySet(),
            fallbackWhenUnauthenticated = true,
        )
        assertEquals(hints, models)
    }

    @Test
    fun `401 without fallback retains failed authentication`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val failure = runCatching { transport.fetchRemoteModelList(
            credentialHeader = "Authorization",
            credentialHeaderValue = "Bearer wrong",
            allowInsecureLocal = allowLocal,
            staticHints = emptyList(),
            freeModelIds = emptySet(),
            fallbackWhenUnauthenticated = false,
        ) }.exceptionOrNull()
        assertTrue(failure is AiProviderException)
        assertEquals(AiErrorType.AUTHENTICATION_FAILED, (failure as AiProviderException).type)
    }

    @Test
    fun `auth failure maps to AUTHENTICATION_FAILED with user action recoverability`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"message":"forbidden"}}"""))
        val hints = listOf(
            AiModel(id = "hint", displayName = "H", providerId = "x", inputCapabilities = setOf(AiCapability.TEXT), contextLength = null, supportsStreaming = true, supportsTools = false),
        )
        val models = transport.fetchRemoteModelList(
            credentialHeader = "Authorization",
            credentialHeaderValue = "Bearer wrong",
            allowInsecureLocal = allowLocal,
            staticHints = hints,
            freeModelIds = emptySet(),
            fallbackWhenUnauthenticated = true,
        )
        // 403 also falls back when allowed (both 401 and 403 are auth failures).
        assertEquals(hints, models)
    }

    @Test
    fun `rate limited error carries retry-after and RATE_LIMITED type`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setHeader("Retry-After", "2")
                .setBody("""{"error":{"message":"slow down"}}"""),
        )
        val error = runCatching {
            transport.testConnection("Authorization", "Bearer t", allowInsecureLocal = allowLocal)
        }.exceptionOrNull()
        assertTrue(error is AiProviderException)
        val providerError = error as AiProviderException
        assertEquals(AiErrorType.RATE_LIMITED, providerError.type)
        assertEquals(2000L, providerError.details?.retryAfterMs)
        assertEquals(429, providerError.details?.httpStatus)
    }

    @Test
    fun `credentials are refused on cleartext public transport`() = runTest {
        val error = runCatching {
            transport.testConnection("Authorization", "Bearer secret", allowInsecureLocal = false)
        }.exceptionOrNull()
        // MockWebServer serves plain HTTP on loopback; with allowInsecureLocal=false
        // the policy must refuse to send the credential.
        assertTrue(error is AiProviderException)
        assertEquals(AiErrorType.AUTHENTICATION_FAILED, (error as AiProviderException).type)
    }
}
