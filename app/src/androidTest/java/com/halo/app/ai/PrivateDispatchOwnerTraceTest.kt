package com.halo.app.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.halo.app.di.tools.host.WebInsightTools
import com.halo.app.mcp.HttpMcpTransport
import com.halo.app.nativeactions.GoogleMailReader
import com.halo.app.online.OpenRouterOnlineClient
import com.halo.providers.anthropic.AnthropicProvider
import com.halo.providers.cerebras.CerebrasProvider
import com.halo.providers.custom.openaicompatible.CustomOpenAiCompatibleCredentialProvider
import com.halo.providers.custom.openaicompatible.CustomOpenAiCompatibleProvider
import com.halo.providers.elevenlabs.tts.ElevenLabsTtsProvider
import com.halo.providers.gemini.GeminiProvider
import com.halo.providers.groq.GroqProvider
import com.halo.providers.ollama.OllamaProvider
import com.halo.providers.opencode.companion.OpenCodeCompanionProvider
import com.halo.providers.opencode.zen.OpenCodeZenProvider
import com.halo.providers.openai.OpenAiProvider
import com.halo.providers.openrouter.OpenRouterProvider
import com.halo.providers.xai.XaiProvider
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.mockwebserver.Dispatcher
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real Android sockets, production owner implementations, synthetic credentials and
 * a loopback-only TLS server. Does not read or change the user's profiles/accounts.
 * These traces qualify the dispatch boundary, not live provider or hardware quality.
 */
@RunWith(AndroidJUnit4::class)
class PrivateDispatchOwnerTraceTest {
    @Test fun everyHttpOwnerBlocksPrivateAndDispatchesOnlyCurrentPowerIntent() = runBlocking {
        val fixtureHosts = setOf("api.openai.com", "api.anthropic.com", "generativelanguage.googleapis.com",
            "openrouter.ai", "api.groq.com", "api.cerebras.ai", "api.x.ai", "opencode.ai",
            "api.elevenlabs.io", "gmail.googleapis.com", "ollama.fixture", "custom.fixture", "companion.fixture", "web.fixture", "mcp.fixture")
        val certificate = HeldCertificate.Builder().commonName("Halo issue 47 loopback fixture").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val dns = AtomicInteger(); val connects = AtomicInteger()
        val traces = JSONArray()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val host = request.getHeader("Host").orEmpty().substringBefore(':')
                    val body = when {
                        host == "api.elevenlabs.io" -> "[]"
                        host == "openrouter.ai" && request.path.orEmpty().contains("auth/key") -> "{\"data\":{}}"
                        host == "web.fixture" -> "<html><title>Fixture</title><body><p>" + "Synthetic reference text. ".repeat(40) + "</p></body></html>"
                        host == "mcp.fixture" -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"
                        else -> "{\"data\":[],\"models\":[],\"messages\":[],\"version\":\"fixture\"}"
                    }
                    return MockResponse().setHeader("Content-Type", if (host == "web.fixture") "text/html" else "application/json").setBody(body)
                }
            }
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val raw = FixtureStorage()
            for (key in listOf("openai_api_key", "anthropic_api_key", "gemini_api_key", "openrouter_api_key", "groq_api_key",
                "cerebras_api_key", "xai_api_key", "opencode_zen_api_key", "custom_openai_compatible_credential", "elevenlabs_api_key"))
                raw.putString(key, "fixture-not-a-real-credential")
            raw.putString("custom_openai_compatible_auth_mode", "API_KEY")
            raw.putString("custom_openai_compatible_endpoint", "https://custom.fixture/v1")
            raw.putString("opencode_companion_endpoint", "https://companion.fixture")
            raw.putString("ollama_endpoint", "https://ollama.fixture")
            val policy = PrivacyModeStore(raw).apply {
                remoteAllowed = { true }
                configuredProviders = { setOf("openai", "anthropic", "gemini", "openrouter", "groq", "cerebras", "xai",
                    "opencode.zen", "companion.opencode", "custom.openaicompatible", "provider.ollama", "provider.elevenlabs.tts",
                    "web.public", "mcp.configured", "google.mail") }
            }
            val storage = policy.storage
            val base = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).protocols(listOf(Protocol.HTTP_1_1))
                .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
                .hostnameVerifier { _, _ -> true } // Fixture certificate only; DNS below cannot leave loopback.
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname !in fixtureHosts) throw IOException("Undeclared fixture destination")
                        dns.incrementAndGet()
                        return listOf(InetAddress.getByName("127.0.0.1"))
                    }
                }).eventListener(object : EventListener() {
                    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) { connects.incrementAndGet() }
                }).addInterceptor { chain ->
                    val url = chain.request().url.newBuilder().scheme("https").port(server.port).build()
                    chain.proceed(chain.request().newBuilder().url(url).build())
                }.connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build()
            fun client(id: String) = base.withProviderDispatchPolicy(policy, id)
            fun operationClient(id: String, operation: RemoteOperation, categories: Set<RemoteDataCategory>) =
                base.withRemoteDispatchPolicy(policy) { RemoteDispatchRequest(id, operation, categories) }
            fun transport(id: String, endpoint: String) = OpenAiCompatibleTransport(client(id), endpoint, AiWireProtocol.OPENAI_CHAT_COMPLETIONS)
            val secretStore = object : ProviderSecretStore {
                override suspend fun get(key: String): String? = null
                override suspend fun put(key: String, value: String?) = Unit
                override suspend fun remove(key: String) = Unit
            }
            val companion = OpenCodeCompanionProvider(storage, secretStore, client("companion.opencode"))
            val inference = listOf<AiInferenceProvider>(
                OpenAiProvider(storage, client("openai")), AnthropicProvider(storage, client("anthropic")),
                GeminiProvider(storage, client("gemini")), OpenRouterProvider(storage, client("openrouter")),
                GroqProvider(storage, transport("groq", "https://api.groq.com/v1")),
                CerebrasProvider(storage, transport("cerebras", "https://api.cerebras.ai/v1")),
                XaiProvider(storage, transport("xai", "https://api.x.ai/v1")),
                OpenCodeZenProvider(storage, transport("opencode.zen", "https://opencode.ai/inference/v1")),
                CustomOpenAiCompatibleProvider(storage, transport("custom.openaicompatible", "https://custom.fixture/v1"),
                    CustomOpenAiCompatibleCredentialProvider(storage)),
                OllamaProvider(storage, policy, base),
            )
            val speech = ElevenLabsTtsProvider(storage, client = operationClient("provider.elevenlabs.tts", RemoteOperation.HOSTED_SPEECH,
                setOf(RemoteDataCategory.ACCOUNT_METADATA)))
            withTimeout(5000) { speech.state.first { !it.loading } }
            val online = OpenRouterOnlineClient(operationClient("openrouter", RemoteOperation.WEB, setOf(RemoteDataCategory.TEXT)))
            val web = WebInsightTools(operationClient("web.public", RemoteOperation.WEB, setOf(RemoteDataCategory.TEXT)),
                hostResolver = { listOf("93.184.216.34") }) // Controlled public-DNS answer, transported only to the fixture.
            val mcp = HttpMcpTransport(operationClient("mcp.configured", RemoteOperation.CONNECTED_SERVICE,
                setOf(RemoteDataCategory.TEXT, RemoteDataCategory.WORKSPACE)))
            val mail = GoogleMailReader(operationClient("google.mail", RemoteOperation.CONNECTED_SERVICE,
                setOf(RemoteDataCategory.TEXT, RemoteDataCategory.ACCOUNT_METADATA)))
            val owners: List<Pair<String, suspend () -> Unit>> = inference.map { provider -> provider.id to suspend {
                provider.testConnection(); Unit
            } } + listOf(
                "companion.opencode" to suspend { companion.health(); Unit },
                "provider.elevenlabs.tts" to suspend { speech.health(); Unit },
                "openrouter.web" to suspend { online.search("fixture-not-a-real-credential", "synthetic fixture", "en"); Unit },
                "web.public" to suspend { web.execute(ToolCall("fixture", "halo_fetch_url", mapOf("url" to "https://web.fixture/fixture-page"))); Unit },
                "mcp.configured" to suspend { mcp.post("https://mcp.fixture/", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}", null, null, null); Unit },
                "google.mail" to suspend { mail.read("fixture-not-a-real-credential", "fixture", null) { true }; Unit },
            )
            for ((id, invoke) in owners) {
                val beforeDns = dns.get(); val beforeConnect = connects.get(); val beforeBytes = server.requestCount
                val beforeBlocked = policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED }
                runCatching { invoke() }
                assertEquals("Private DNS: $id", beforeDns, dns.get())
                assertEquals("Private connect: $id", beforeConnect, connects.get())
                assertEquals("Private request: $id", beforeBytes, server.requestCount)
                assertTrue("Owner never reached its guard: $id", policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED } > beforeBlocked)
                traces.put(JSONObject().put("owner", id).put("mode", "PRIVATE").put("dns", 0).put("connects", 0).put("requests", 0))
            }
            policy.acknowledgeSharing(); policy.set(PrivacyMode.CLOUD)
            for ((id, invoke) in owners) {
                val before = server.requestCount
                runCatching { invoke() }
                assertTrue("Current Power owner made no fixture request: $id", server.requestCount > before)
                traces.put(JSONObject().put("owner", id).put("mode", "CLOUD").put("requests", server.requestCount - before))
            }
            policy.edit(PrivacyMode.CLOUD) { it.copy(booleans = it.booleans + (KEY_CLOUD_WORKSPACE to false)) }
            val beforeDerived = server.requestCount
            val beforeDerivedBlocked = policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED }
            kotlinx.coroutines.withContext(RemoteDispatchContext.provenance.asContextElement(
                RemoteDispatchProvenance(setOf(RemoteDataCategory.WORKSPACE), policy.revision))) {
                runCatching { web.execute(ToolCall("derived-fixture", "halo_fetch_url",
                    mapOf("url" to "https://web.fixture/fixture-page?derived=synthetic"))) }
            }
            assertEquals("Locally derived Workspace text leaked through web", beforeDerived, server.requestCount)
            assertTrue(policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED } > beforeDerivedBlocked)
            traces.put(JSONObject().put("owner", "web.public").put("case", "LOCAL_WORKSPACE_DERIVED_TOOL_BLOCKED").put("requests", 0))
            storage.putBatch(strings = mapOf(ElevenLabsTtsProvider.VOICE to "fixture-voice", ElevenLabsTtsProvider.MODEL to "fixture-model",
                "elevenlabs_tts_catalogue" to """{"voices":[{"id":"fixture-voice","name":"Fixture"}],"models":[{"id":"fixture-model","name":"Fixture"}],"at":1}"""))
            speech.reloadConfigurationNow()
            val beforeSpeech = server.requestCount
            val blockedSpeech = policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED }
            runCatching { speech.synthesizeToPcm(SpeechRequest("synthetic derived answer", remoteProvenance =
                RemoteDispatchProvenance(setOf(RemoteDataCategory.WORKSPACE), policy.revision))) }
            assertEquals("Locally derived Workspace answer leaked through speech", beforeSpeech, server.requestCount)
            assertTrue(policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED } > blockedSpeech)
            traces.put(JSONObject().put("owner", ElevenLabsTtsProvider.ID).put("case", "LOCAL_WORKSPACE_DERIVED_SPEECH_BLOCKED").put("requests", 0))
            val oldRevision = policy.revision
            policy.set(PrivacyMode.PRIVATE); policy.set(PrivacyMode.CLOUD)
            val beforeStale = server.requestCount
            val beforeStaleBlocked = policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED }
            kotlinx.coroutines.withContext(RemoteDispatchContext.provenance.asContextElement(
                RemoteDispatchProvenance(emptySet(), oldRevision))) {
                runCatching { web.execute(ToolCall("stale-fixture", "halo_fetch_url", mapOf("url" to "https://web.fixture/fixture-page"))) }
            }
            assertEquals(beforeStale, server.requestCount)
            assertTrue(policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED } > beforeStaleBlocked)
            traces.put(JSONObject().put("owner", "web.public").put("case", "STALE_LOCAL_TOOL_BLOCKED").put("requests", 0))
            policy.edit(PrivacyMode.CLOUD) { it.copy(booleans = it.booleans + (KEY_CLOUD_WORKSPACE to true)) }
            policy.edit(PrivacyMode.CLOUD) { it.copy(booleans = it.booleans + (KEY_CONVERSATION to false)) }
            for (provider in inference) {
                val before = server.requestCount
                val blockedBefore = policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED }
                runCatching { provider.generate(AiRequest(text = "synthetic current request", modelId = "fixture-model"),
                    AiContext(conversation = listOf(AIMessage(Role.USER, "synthetic prior turn")))) }
                assertEquals("Conversation category leaked: ${provider.id}", before, server.requestCount)
                assertTrue("Generation did not reach category guard: ${provider.id}",
                    policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED } > blockedBefore)
                traces.put(JSONObject().put("owner", provider.id).put("case", "CONVERSATION_DISABLED").put("requests", 0))
            }
            storage.putBatch(strings = mapOf("active_ai_provider" to "provider.ollama", "active_ai_model" to "fixture-model"))
            val legacy = inference.filterIsInstance<OllamaProvider>().single()
            val beforeLegacy = server.requestCount
            val beforeLegacyBlocked = policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED }
            runCatching { legacy.complete(AIRequestLegacy(AIConversation("fixture", mutableListOf(AIMessage(Role.USER, "synthetic prior turn"))))) }
            assertEquals(beforeLegacy, server.requestCount)
            assertTrue(policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED } > beforeLegacyBlocked)
            policy.edit(PrivacyMode.CLOUD) { it.copy(onlineFeatures = it.onlineFeatures - OnlineFeature.IMAGES) }
            for (provider in inference.filterIsInstance<AiImageProvider>()) {
                val before = server.requestCount
                val blockedBefore = policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED }
                val model = if (provider.id == "openai") "gpt-image-1" else "fixture-image"
                runCatching { provider.generateImage(AiImageRequest(model, "synthetic fixture", byteArrayOf(1, 2, 3))) }
                assertEquals("Disabled image feature leaked: ${provider.id}", before, server.requestCount)
                assertTrue("Image call missed its guard: ${provider.id}",
                    policy.activity.receipts.value.count { it.outcome == RemoteDispatchOutcome.BLOCKED } > blockedBefore)
                traces.put(JSONObject().put("owner", provider.id).put("case", "IMAGE_FEATURE_DISABLED").put("requests", 0))
            }
            policy.set(PrivacyMode.PRIVATE)
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val evidenceDirectory = java.io.File(context.cacheDir, "issue47-fixture")
            check(evidenceDirectory.isDirectory || evidenceDirectory.mkdirs())
            java.io.File(evidenceDirectory, "owner-network-trace.json").writeText(JSONObject().put("schema", 1)
                .put("evidence", "ANDROID_LOOPBACK_TLS_PRODUCTION_OWNERS").put("rows", traces).toString(2))
            println("HALO_PRIVACY_OWNER_TRACE owners=${owners.size} privateRequests=0 evidence=ANDROID_LOOPBACK_TLS")
            speech.close()
            base.dispatcher.executorService.shutdownNow(); base.connectionPool.evictAll()
        }
    }

    @Test fun renderedWebReadingBlocksBeforeWebViewAndCancelsOnPrivate() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type", "text/html")
                    .setBody("<html><body>" + "Synthetic page material. ".repeat(80) + "</body></html>")
            }
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val url = server.url("/rendered").toString()
            val policy = PrivacyModeStore(FixtureStorage()).apply {
                remoteAllowed = { true }; configuredProviders = { setOf("web.public") }
            }
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val extractor = com.halo.app.web.AndroidDynamicPageExtractor(context, enabled = { true }, dispatchPolicy = policy,
                urlAllowed = { it == url }, resolutionAllowed = { it == url })
            assertTrue(extractor.extract(url, 8000) is com.halo.app.web.DynamicExtractionOutcome.Unavailable)
            assertEquals(0, server.requestCount)
            policy.acknowledgeSharing(); policy.set(PrivacyMode.CLOUD)
            val outcome = extractor.extract(url, 8000)
            assertTrue("Power fixture extraction failed: ${outcome::class.java.simpleName}", outcome is com.halo.app.web.DynamicExtractionOutcome.Extracted)
            assertTrue(server.requestCount > 0)
            // Delay the next body so revocation happens while WebView owns live work.
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type", "text/html")
                    .setBodyDelay(5, TimeUnit.SECONDS).setBody("<html><body>fixture</body></html>")
            }
            while (server.takeRequest(50, TimeUnit.MILLISECONDS) != null) { /* only synthetic fixture requests */ }
            val job = async(Dispatchers.Default) { extractor.extract(url, 8000) }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            policy.set(PrivacyMode.PRIVATE)
            try { job.await(); fail("Private must cancel active rendered web work") }
            catch (_: kotlinx.coroutines.CancellationException) { }
            assertTrue(policy.activity.receipts.value.any { it.outcome == RemoteDispatchOutcome.CANCELLED })
            println("HALO_PRIVACY_RENDERED_WEB_TRACE privateInitialRequests=0 powerFixtureObserved=true privateCancellationObserved=true")
        }
    }

    private class FixtureStorage : Storage {
        private val strings = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val booleans = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
        override fun getString(key: String, defaultValue: String?) = strings[key] ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) strings.remove(key) else strings[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = booleans[key] ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
        override fun getInt(key: String, defaultValue: Int) = defaultValue
        override fun putInt(key: String, value: Int) = error("No integer fixture state")
        override fun remove(key: String) { strings.remove(key); booleans.remove(key) }
        override fun clear() { strings.clear(); booleans.clear() }
    }
}
