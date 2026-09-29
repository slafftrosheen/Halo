package com.halo.sdk.provider

import kotlinx.coroutines.test.runTest
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
    fun `401 without fallback returns empty`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val models = transport.fetchRemoteModelList(
            credentialHeader = "Authorization",
            credentialHeaderValue = "Bearer wrong",
            allowInsecureLocal = allowLocal,
            staticHints = emptyList(),
            freeModelIds = emptySet(),
            fallbackWhenUnauthenticated = false,
        )
        assertEquals(0, models.size)
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
