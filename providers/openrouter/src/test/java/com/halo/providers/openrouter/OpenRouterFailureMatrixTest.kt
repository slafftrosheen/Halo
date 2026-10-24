package com.halo.providers.openrouter

import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.Recoverability
import com.halo.sdk.provider.TargetRejectionReason
import com.halo.sdk.runtime.Storage
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Deterministic provider failure matrix.
 *
 * Every case asserts the normalized contract (`AiErrorType`, recoverability, `retryAfterMs`,
 * `httpStatus`, `providerCode`) rather than a human string, so the semantics survive refactoring.
 * No real network and no real rate-limit abuse: MockWebServer serves each failure.
 */
@RunWith(RobolectricTestRunner::class)
class OpenRouterFailureMatrixTest {

    private lateinit var server: MockWebServer

    private val fixedNow = Instant.parse("2026-09-24T12:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    /**
     * Serves the same failure for every request. Needed for cases OkHttp itself retries
     * (408 is retried internally), where a single queued response would leave the queue dry
     * and turn the assertion into an unrelated client-side timeout.
     */
    private fun respondAlways(build: () -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = build()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private class FakeStorage(private val apiKey: String? = "test-key") : Storage {
        private val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?): String? =
            if (key == "openrouter_api_key") apiKey else data[key] as? String ?: defaultValue

        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }

    /** Routes production OpenRouter URLs at the mock server without touching production code. */
    private fun provider(
        apiKey: String? = "test-key",
        now: () -> Long = { fixedNow },
        readTimeoutMs: Long = 5_000L,
    ): OpenRouterProvider {
        val client = OkHttpClient.Builder()
            .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                chain.proceed(
                    original.newBuilder()
                        .url(
                            original.url.newBuilder()
                                .scheme(server.url("/").scheme)
                                .host(server.hostName)
                                .port(server.port)
                                .build()
                        )
                        .build()
                )
            }
            .build()
        return OpenRouterProvider(FakeStorage(apiKey), client, now)
    }

    private fun request() = AiRequest(text = "hello", modelId = "test/model")

    private fun generate(provider: OpenRouterProvider): AiProviderException = runBlocking {
        try {
            provider.generate(request(), AiContext.EMPTY)
            throw AssertionError("Expected the provider to fail")
        } catch (e: AiProviderException) {
            e
        }
    }

    private fun generateSuccessfully(provider: OpenRouterProvider) =
        runBlocking { provider.generate(request(), AiContext.EMPTY) }

    private fun enqueueError(code: Int, body: String, headers: Map<String, String> = emptyMap()) {
        val response = MockResponse().setResponseCode(code).setBody(body)
        headers.forEach { (name, value) -> response.addHeader(name, value) }
        server.enqueue(response)
    }

    private fun enqueueSuccess(text: String) {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"$text"},"finish_reason":"stop"}],"model":"test/model"}""")
        )
    }

    @Test
    fun `429 with Retry-After seconds preserves the duration`() {
        enqueueError(429, """{"error":{"message":"limited","code":"rate_limit_exceeded"}}""", mapOf("Retry-After" to "42"))

        val error = generate(provider())

        assertEquals(AiErrorType.RATE_LIMITED, error.type)
        assertEquals(Recoverability.RETRY_AFTER, error.details.recoverability)
        assertEquals(42_000L, error.details.retryAfterMs)
        assertEquals(429, error.details.httpStatus)
        assertEquals("rate_limit_exceeded", error.details.providerCode)
    }

    @Test
    fun `429 with an HTTP-date Retry-After is normalized to a future duration`() {
        enqueueError(
            429,
            """{"error":{"message":"limited"}}""",
            mapOf("Retry-After" to "Thu, 24 Sep 2026 12:01:01 GMT"),
        )

        val error = generate(provider())

        assertEquals(AiErrorType.RATE_LIMITED, error.type)
        assertEquals(Recoverability.RETRY_AFTER, error.details.recoverability)
        assertEquals(61_000L, error.details.retryAfterMs)
    }

    @Test
    fun `429 with an HTTP-date in the past does not produce a negative cooldown`() {
        enqueueError(
            429,
            """{"error":{"message":"limited"}}""",
            mapOf("Retry-After" to "Thu, 24 Sep 2026 11:59:00 GMT"),
        )

        val error = generate(provider())

        assertEquals(AiErrorType.RATE_LIMITED, error.type)
        assertEquals(0L, error.details.retryAfterMs)
    }

    @Test
    fun `429 body retry value is used when the header is absent`() {
        enqueueError(429, """{"error":{"message":"limited","retry_after_ms":7000}}""")

        val error = generate(provider())

        assertEquals(7_000L, error.details.retryAfterMs)
        assertEquals(Recoverability.RETRY_AFTER, error.details.recoverability)
    }

    @Test
    fun `429 header wins over a stale body retry value`() {
        enqueueError(
            429,
            """{"error":{"message":"limited","retry_after_ms":900000}}""",
            mapOf("Retry-After" to "5"),
        )

        val error = generate(provider())

        assertEquals(5_000L, error.details.retryAfterMs)
    }

    @Test
    fun `429 without any retry information reports unknown recoverability`() {
        enqueueError(429, """{"error":{"message":"limited"}}""")

        val error = generate(provider())

        assertEquals(AiErrorType.RATE_LIMITED, error.type)
        assertNull(error.details.retryAfterMs)
        assertEquals(Recoverability.UNKNOWN, error.details.recoverability)
    }

    @Test
    fun `401 and 403 require user action`() {
        listOf(401, 403).forEach { code ->
            enqueueError(code, """{"error":{"message":"no auth","code":"invalid_api_key"}}""")

            val error = generate(provider())

            assertEquals("status $code", AiErrorType.AUTHENTICATION_FAILED, error.type)
            assertEquals("status $code", Recoverability.USER_ACTION_REQUIRED, error.details.recoverability)
            assertEquals("status $code", code, error.details.httpStatus)
            assertEquals("status $code", "invalid_api_key", error.details.providerCode)
        }
    }

    @Test
    fun `408 is a retryable timeout`() {
        respondAlways { MockResponse().setResponseCode(408).setBody("""{"error":{"message":"timeout"}}""") }

        val error = generate(provider())

        assertEquals(AiErrorType.TIMEOUT, error.type)
        assertEquals(Recoverability.RETRY_IMMEDIATELY, error.details.recoverability)
        assertEquals(408, error.details.httpStatus)
    }

    @Test
    fun `409 and 5xx are retryable provider outages`() {
        listOf(409, 500, 503).forEach { code ->
            respondAlways { MockResponse().setResponseCode(code).setBody("""{"error":{"message":"upstream"}}""") }

            val error = generate(provider())

            assertEquals("status $code", AiErrorType.PROVIDER_UNAVAILABLE, error.type)
            assertEquals("status $code", Recoverability.RETRY_IMMEDIATELY, error.details.recoverability)
        }
    }

    @Test
    fun `a 400 context overflow is classified as context too large, not a malformed request`() {
        enqueueError(
            400,
            """{"error":{"message":"This model's maximum context length is 4096 tokens","code":"context_length_exceeded"}}""",
        )

        val error = generate(provider())

        assertEquals(AiErrorType.CONTEXT_TOO_LARGE, error.type)
        assertEquals(Recoverability.PERMANENT, error.details.recoverability)
        assertEquals("context_length_exceeded", error.details.providerCode)
        assertEquals(400, error.details.httpStatus)
    }

    @Test
    fun `context overflow is recognized from provider prose when no code is sent`() {
        enqueueError(400, """{"error":{"message":"Input validation error: prompt is too long"}}""")

        val error = generate(provider())

        assertEquals(AiErrorType.CONTEXT_TOO_LARGE, error.type)
        assertEquals(Recoverability.PERMANENT, error.details.recoverability)
    }

    @Test
    fun `an unrelated 400 that merely mentions tokens stays a malformed request`() {
        enqueueError(400, """{"error":{"message":"unknown field: tokens_per_second"}}""")

        val error = generate(provider())

        assertEquals(AiErrorType.INVALID_REQUEST, error.type)
    }

    @Test
    fun `400 is permanent and does not invite a blind retry`() {
        enqueueError(400, """{"error":{"message":"bad payload"}}""")

        val error = generate(provider())

        assertEquals(AiErrorType.INVALID_REQUEST, error.type)
        assertEquals(Recoverability.PERMANENT, error.details.recoverability)
    }

    @Test
    fun `malformed JSON on a 200 is an invalid response, not a network failure`() {
        enqueueError(200, "{not json at all")

        val error = generate(provider())

        assertEquals(AiErrorType.INVALID_RESPONSE, error.type)
    }

    @Test
    fun `empty body on a 200 is an invalid response`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(""))

        val error = generate(provider())

        assertEquals(AiErrorType.INVALID_RESPONSE, error.type)
    }

    @Test
    fun `a body that never arrives is a timeout, not a generic failure`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"hi"}}]}""")
                .setBodyDelay(3, TimeUnit.SECONDS)
        )

        val error = generate(provider(readTimeoutMs = 250L))

        assertEquals(AiErrorType.TIMEOUT, error.type)
    }

    @Test
    fun `a connection dropped mid-stream surfaces on the stream as a failure event`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"par\"}}]}\n")
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val events = runBlocking { provider().stream(request(), AiContext.EMPTY).toList() }

        val failure = events.filterIsInstance<AiStreamEvent.Failed>().singleOrNull()
        assertNotNull("stream must report a failure rather than completing silently", failure)
        assertEquals(AiErrorType.NETWORK_ERROR, (failure!!.error as AiProviderException).type)
    }

    @Test
    fun `nullable SSE fields do not corrupt tool identity arguments or visible text`() = runBlocking {
        val first = """{"choices":[{"delta":{"content":null,"tool_calls":[{"index":0,"id":"call_qa","function":{"name":"halo_discover_tools","arguments":"{\"query\":"}}]}}]}"""
        val second = """{"choices":[{"delta":{"content":null,"tool_calls":[{"index":0,"id":null,"function":{"name":null,"arguments":"\"notebook\"}"}}]}}]}"""
        val finish = """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}"""
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "text/event-stream")
            .setBody("data: $first\n\ndata: $second\n\ndata: $finish\n\ndata: [DONE]\n\n"))
        val events = provider().stream(request(), AiContext.EMPTY).toList()
        assertTrue(events.filterIsInstance<AiStreamEvent.Failed>().isEmpty())
        assertTrue("JSON null is absence, never spoken output", events.filterIsInstance<AiStreamEvent.Token>().isEmpty())
        val completed = events.filterIsInstance<AiStreamEvent.Completed>().single().response
        val call = completed.toolCalls.single()
        assertEquals("call_qa", call.id)
        assertEquals("halo_discover_tools", call.functionName)
        assertEquals("notebook", call.arguments["query"])
        val deltas = events.filterIsInstance<AiStreamEvent.ToolCallDelta>()
        assertNull(deltas[1].name)
        assertNull(deltas[1].id)
    }

    @Test
    fun `a rate limit opens an ephemeral cooldown that suppresses further calls`() {
        enqueueError(429, """{"error":{"message":"limited"}}""", mapOf("Retry-After" to "60"))

        var now = fixedNow
        val provider = provider(now = { now })

        assertEquals(AiErrorType.RATE_LIMITED, generate(provider).type)
        assertEquals(1, server.requestCount)

        // Repeated attempts during the cooldown must not hammer the endpoint.
        repeat(3) {
            val suppressed = generate(provider)
            assertEquals(AiErrorType.RATE_LIMITED, suppressed.type)
            assertEquals(Recoverability.RETRY_AFTER, suppressed.details.recoverability)
            assertTrue((suppressed.details.retryAfterMs ?: 0L) > 0L)
        }
        assertEquals("cooldown must not issue new HTTP calls", 1, server.requestCount)

        // Once the window elapses the provider is willing to try again without a restart.
        now += 61_000L
        enqueueSuccess("recovered")
        assertEquals("recovered", generateSuccessfully(provider).text)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an active cooldown is visible as standby with the remaining wait`() {
        enqueueError(429, """{"error":{"message":"limited"}}""", mapOf("Retry-After" to "60"))

        var now = fixedNow
        val provider = provider(now = { now })
        generate(provider)
        now += 20_000L

        val status = runBlocking { provider.getStatus() }

        assertEquals(ProviderStatus.State.STANDBY, status.state)
        assertEquals(TargetRejectionReason.PROVIDER_COOLDOWN, status.rejectionReason)
        assertEquals(40_000L, status.cooldown?.remainingMs(now))
        assertTrue(status.message.orEmpty().contains("40 seconds"))
    }

    @Test
    fun `cooldown never persists into a new provider instance`() {
        enqueueError(429, """{"error":{"message":"limited"}}""", mapOf("Retry-After" to "600"))
        generate(provider())

        enqueueSuccess("ok")

        assertEquals("ok", generateSuccessfully(provider()).text)
    }

    @Test
    fun `missing api key fails locally without any network call`() {
        val error = generate(provider(apiKey = null))

        assertEquals(AiErrorType.AUTHENTICATION_FAILED, error.type)
        assertEquals(Recoverability.USER_ACTION_REQUIRED, error.details.recoverability)
        assertEquals(0, server.requestCount)
    }
}
