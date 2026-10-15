package com.halo.app.ai

import android.app.Application
import com.halo.providers.openai.OpenAiProvider
import com.halo.providers.anthropic.AnthropicProvider
import com.halo.providers.gemini.GeminiProvider
import com.halo.providers.openrouter.OpenRouterProvider
import com.halo.providers.ollama.OllamaProvider
import com.halo.providers.opencode.zen.OpenCodeZenProvider
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Loopback-only adapter contracts. Dummy keys never leave MockWebServer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DirectProviderContractTest {
    private class MemoryStorage : Storage {
        val values = mutableMapOf<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private class Fixture(kind: String, code: Int, body: String) : AutoCloseable {
        val server = MockWebServer()
        val storage = MemoryStorage()
        val provider: AiInferenceProvider
        init {
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(code)
                    .setHeader("Content-Type", "application/json").setHeader("Retry-After", "2").setBody(body)
            }
            server.start()
            listOf("openai", "anthropic", "gemini", "openrouter", "opencode_zen").forEach { storage.putString(it + "_api_key", "fixture-key") }
            storage.putString("ollama_endpoint", server.url("/").toString().trimEnd('/'))
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(2, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request()
                    chain.proceed(request.newBuilder().url(request.url.newBuilder().scheme("http")
                        .host(server.hostName).port(server.port).build()).build())
                }.build()
            provider = when (kind) {
                "openai" -> OpenAiProvider(storage, client)
                "anthropic" -> AnthropicProvider(storage, client)
                "gemini" -> GeminiProvider(storage, client)
                "openrouter" -> OpenRouterProvider(storage, client)
                "ollama" -> OllamaProvider(storage)
                else -> OpenCodeZenProvider(storage, OpenAiCompatibleTransport(client,
                    "https://opencode.ai/inference/v1", AiWireProtocol.OPENAI_CHAT_COMPLETIONS))
            }
        }
        override fun close() { server.shutdown() }
    }
    private val kinds = listOf("openai", "anthropic", "gemini", "openrouter", "ollama", "zen")
    private fun authBody(kind: String) = when (kind) {
        "gemini" -> """{"models":[]}"""
        "ollama" -> """{"models":[]}"""
        "openrouter" -> """{"data":{}}"""
        else -> """{"data":[]}"""
    }
    private fun generationBody(kind: String) = when (kind) {
        "anthropic" -> """{"content":[{"type":"text","text":"HALO."}],"model":"text","stop_reason":"end_turn"}"""
        "gemini" -> """{"candidates":[{"content":{"parts":[{"text":"HALO."}]},"finishReason":"STOP"}],"modelVersion":"text"}"""
        "ollama" -> """{"message":{"role":"assistant","content":"HALO."},"done":true,"model":"text","created_at":"2026-10-15T06:00:00Z"}"""
        else -> """{"choices":[{"message":{"content":"HALO."},"finish_reason":"stop"}],"model":"text"}"""
    }
    private suspend fun authContract(kind: String) {
        Fixture(kind, 200, authBody(kind)).use { f ->
            assertTrue("$kind should connect before model selection", f.provider.testConnection())
            val sent = f.server.takeRequest(2, TimeUnit.SECONDS)!!
            when (kind) {
                "anthropic" -> { assertEquals("fixture-key", sent.getHeader("x-api-key")); assertNotNull(sent.getHeader("anthropic-version")) }
                "gemini" -> assertEquals("fixture-key", sent.getHeader("x-goog-api-key"))
                "ollama" -> assertEquals("/api/tags", sent.path)
                else -> assertEquals("Bearer fixture-key", sent.getHeader("Authorization"))
            }
        }
        for (code in listOf(401, 402, 403, 404, 429, 500)) Fixture(kind, code,
            """{"error":{"code":"fixture-secret-code","message":"fixture-secret-body"}}""").use { f ->
            val failure = runCatching { f.provider.testConnection() }.exceptionOrNull()
            assertTrue("$kind HTTP $code lost its typed failure: $failure", failure is AiProviderException)
            assertEquals(code, (failure as AiProviderException).details.httpStatus)
            val diagnostic = providerFailure(ProviderTestStage.AUTHENTICATE, failure)
            assertFalse(diagnostic.toString().contains("fixture-secret"))
            if (code == 429) assertEquals(2000L, diagnostic.retryAfterMs)
            if (kind != "ollama") assertTrue((f.provider as CredentialProvider).hasCredential())
        }
    }
    @Test fun openAiAuthenticationContract() = runBlocking { authContract("openai") }
    @Test fun anthropicAuthenticationContract() = runBlocking { authContract("anthropic") }
    @Test fun geminiAuthenticationContract() = runBlocking { authContract("gemini") }
    @Test fun openRouterAuthenticationContract() = runBlocking { authContract("openrouter") }
    @Test fun ollamaConnectionDoesNotRequireAnAlreadySelectedModel() = runBlocking { authContract("ollama") }
    @Test fun zenAuthenticationContract() = runBlocking { authContract("zen") }
    @Test fun expiredOAuthPreservesTheStoredApiKeyWithoutChangingTheChosenAuthContract() = runBlocking {
        Fixture("gemini", 401, """{"error":{"status":"UNAUTHENTICATED"}}""").use { f ->
            val provider = f.provider as GeminiProvider
            provider.acceptOAuthAccessToken("fixture-oauth", "fixture-project")
            val failure = runCatching { provider.testConnection() }.exceptionOrNull() as AiProviderException
            assertEquals(401, failure.details.httpStatus)
            val request = f.server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("Bearer fixture-oauth", request.getHeader("Authorization"))
            assertNull(request.getHeader("x-goog-api-key"))
            assertEquals("fixture-key", f.storage.getString("gemini_api_key"))
            assertEquals(com.halo.providers.gemini.GeminiAuthenticationMode.NONE, provider.authenticationMode)
            assertEquals(com.halo.providers.gemini.GeminiAuthenticationMode.NONE, GeminiProvider(f.storage).authenticationMode)
        }
    }

    @Test fun malformedModelListsAreProtocolFailuresRatherThanEmptySuccessfulCatalogs() = runBlocking {
        kinds.forEach { kind -> Fixture(kind, 200, """{"unexpected":true}""").use { f ->
            val failure = runCatching { f.provider.listModels() }.exceptionOrNull()
            assertTrue("$kind accepted a malformed catalog", failure != null)
            assertEquals("$kind", ProviderTestOutcome.PROTOCOL_ERROR,
                providerFailure(ProviderTestStage.FETCH_MY_MODELS, failure!!).outcome)
        } }
    }
    @Test fun missingGenerationModelIsDistinctFromAnAuthenticationEndpointFailure() = runBlocking {
        kinds.forEach { kind -> Fixture(kind, 404, """{"error":{"message":"fixture-body"}}""").use { f ->
            val failure = runCatching { f.provider.generate(AiRequest(text = "HALO", modelId = "missing-model",
                maxOutputTokens = 256, toolUseMode = ToolUseMode.NONE), AiContext.EMPTY) }.exceptionOrNull()
            assertNotNull("$kind", failure)
            val diagnostic = providerFailure(ProviderTestStage.TEST_GENERATION, failure!!)
            assertEquals("$kind", 404, diagnostic.httpStatus)
            assertEquals("$kind", ProviderTestOutcome.MODEL_NOT_FOUND, diagnostic.outcome)
        } }
    }

    @Test fun zenStreamingKeepsTheConfiguredProviderOwner() = runBlocking {
        val events = "data: {\"choices\":[{\"delta\":{\"content\":\"HALO\"}}]}\n\ndata: [DONE]\n\n"
        Fixture("zen", 200, events).use { f ->
            val completed = f.provider.stream(AiRequest(text = "Reply HALO", modelId = "text", maxOutputTokens = 256,
                toolUseMode = ToolUseMode.NONE), AiContext.EMPTY).toList().filterIsInstance<AiStreamEvent.Completed>().single()
            assertEquals(f.provider.id, completed.response.providerId)
            assertEquals("HALO", completed.response.text)
        }
    }

    @Test fun eachAdapterUsesTheExplicitModelWithABoundedEmptyContextRequest() = runBlocking {
        kinds.forEach { kind -> Fixture(kind, 200, generationBody(kind)).use { f ->
            val answer = f.provider.generate(AiRequest(text = "Reply exactly HALO", modelId = "text",
                maxOutputTokens = 256, toolUseMode = ToolUseMode.NONE, reasoningMode = AiReasoningMode.DISABLED), AiContext.EMPTY)
            assertEquals("$kind", f.provider.id, answer.providerId)
            assertTrue("$kind", generationEvidence(answer).proven)
            assertFalse("$kind", generationEvidence(answer).sentinelExactness)
            val sent = f.server.takeRequest(2, TimeUnit.SECONDS)!!
            val body = org.json.JSONObject(sent.body.readUtf8())
            val budget = when (kind) {
                "openai" -> body.getInt("max_completion_tokens")
                "gemini" -> body.getJSONObject("generationConfig").getInt("maxOutputTokens")
                "ollama" -> body.getJSONObject("options").getInt("num_predict")
                else -> body.getInt("max_tokens")
            }
            assertEquals("$kind", 256, budget)
        } }
    }
}
