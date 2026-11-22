package com.halo.app.online

import com.halo.app.ai.*
import com.halo.app.ai.WorkspaceImageChoice
import com.halo.app.ai.WorkspaceImageService
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

/** Hermetic admission cases using the real mode profile owner and a non-network fixture provider. */
@RunWith(RobolectricTestRunner::class)
class OnlineAdmissionTest {
    @Test fun assistantSearchUsesCanonicalOwnerReturnsCitationsAndCannotReplay() = runTest {
        val f = Fixture()
        val tools = com.halo.app.di.tools.host.GroundedWebSearchTools { f.search }
        val definitions = tools.definitions()
        val descriptors = definitions.map { com.halo.app.di.tools.HaloToolDescriptors.webInsight(it)!! }
        assertTrue(com.halo.runtime.cognition.tools.ToolOperabilityReview.inspect(listOf(
            com.halo.runtime.cognition.tools.ToolFeatureOperability("web", definitions.map { it.name }.toSet())), descriptors).isEmpty())
        assertEquals(com.halo.runtime.cognition.tools.ConfirmationPolicy.ALWAYS, descriptors.last().security.confirmation)
        assertEquals(com.halo.runtime.cognition.tools.ReplaySafety.NEVER_REPLAY, descriptors.last().security.replaySafety)
        val args = mapOf<String, Any>("query" to "synthetic question", "language" to "pl")
        val prepared = tools.execute(ToolCall("prepare", "halo_web_search_prepare", args)) as com.halo.runtime.cognition.tools.ToolExecutionResult.Success
        assertEquals(0, f.calls)
        val version = org.json.JSONObject(prepared.output).getString("sourceVersion")
        val call = ToolCall("search", "halo_web_search", args + ("sourceVersion" to version))
        val result = tools.execute(call) as com.halo.runtime.cognition.tools.ToolExecutionResult.Success
        val body = org.json.JSONObject(result.output)
        assertEquals("https://example.org/report", body.getJSONArray("sources").getJSONObject(0).getString("url"))
        val receipt = OnlineOperationLedger(f.storage).snapshot().records.single()
        assertEquals(receipt.id, body.getString("searchId")); assertEquals("COMPLETED", receipt.state)
        assertTrue(tools.execute(call) is com.halo.runtime.cognition.tools.ToolExecutionResult.Rejected)
        assertEquals(1, f.calls)
        f.modes.set(PrivacyMode.PRIVATE)
        assertTrue(tools.execute(ToolCall("local", "halo_web_search_prepare", args)) is com.halo.runtime.cognition.tools.ToolExecutionResult.Rejected)
        assertEquals(1, f.calls)
    }

    @Test fun assistantSearchRejectsFabricatedVersionsAndReportsUncertainMissingSources() = runTest {
        val f = Fixture("""{"choices":[{"finish_reason":"stop","message":{"content":"uncited"}}]}""")
        val tools = com.halo.app.di.tools.host.GroundedWebSearchTools { f.search }
        val args = mapOf<String, Any>("query" to "synthetic", "language" to "en")
        assertTrue(tools.execute(ToolCall("fake", "halo_web_search", args + ("sourceVersion" to java.util.UUID.randomUUID().toString()))) is com.halo.runtime.cognition.tools.ToolExecutionResult.Rejected)
        assertEquals(0, f.calls)
        val prepared = tools.execute(ToolCall("prepare", "halo_web_search_prepare", args)) as com.halo.runtime.cognition.tools.ToolExecutionResult.Success
        val version = org.json.JSONObject(prepared.output).getString("sourceVersion")
        val result = tools.execute(ToolCall("search", "halo_web_search", args + ("sourceVersion" to version))) as com.halo.runtime.cognition.tools.ToolExecutionResult.Failed
        assertTrue(result.completionUnknown)
        assertEquals("UNCERTAIN", OnlineOperationLedger(f.storage).snapshot().records.single().state)
        assertEquals(1, f.calls)
    }
    @Test fun referenceRequiredModelCannotPrepareTextOnlyGeneration() = runTest {
        val f = Fixture(); val service = WorkspaceImageService(f.router, f.storage, privacyModes=f.modes.mode)
        val choice = f.choice.copy(model = f.choice.model.copy(requiresReference = true))
        try { service.prepareSpend(choice, "draw", null, "image/png"); fail("Reference required") }
        catch (_: com.halo.app.ai.WorkspaceImageUnavailable) { }
        assertEquals(0, f.provider.imageCalls)
    }
    @Test fun savedImageReferenceMustAllowCloudBeforeApproval() = runTest {
        val f = Fixture(); f.storage.putBoolean(com.halo.app.ai.KEY_CLOUD_IMAGES, true)
        val service = WorkspaceImageService(f.router, f.storage, allowReference = { false },privacyModes=f.modes.mode)
        try { service.prepareSpend(f.choice, "edit", byteArrayOf(1), "image/png", "local-only-note"); fail("Source permission required") }
        catch (_: com.halo.app.ai.WorkspaceImageUnavailable) { }
        assertEquals(0, f.provider.imageCalls)
    }
    @Test fun revokedReferenceAccessStopsApprovedImageBeforeDispatch() = runTest {
        val f = Fixture(); f.storage.putBoolean(com.halo.app.ai.KEY_CLOUD_IMAGES, true)
        var allowed = true
        val service = WorkspaceImageService(f.router, f.storage, allowReference = { allowed },privacyModes=f.modes.mode)
        val bytes = byteArrayOf(1)
        val approval = service.prepareSpend(f.choice, "edit", bytes, "image/png", "source-note")
        f.disk.afterWrite = { if (it == OnlineOperationLedger.RECORDS_KEY) allowed = false }
        try { service.generate(f.choice, "edit", bytes, "image/png", approval, BigDecimal("0.01"), true, "source-note"); fail("Revoked source permission required") }
        catch (_: com.halo.app.ai.WorkspaceImageUnavailable) { }
        assertEquals(0, f.provider.imageCalls)
    }

    @Test fun searchPreflightAndDismissMakeNoRequest() = runTest {
        val f = Fixture()
        val challenge = f.search.prepare("question", "en")
        f.search.discard(challenge)
        assertEquals(0, f.calls)
        assertEquals(OnlineProblem.CONSENT_REQUIRED,
            rejected { f.search.search("question", "en", challenge, BigDecimal("0.01"), true) }.problem)
        assertEquals(0, f.calls)
    }
    @Test fun falseAcknowledgementCannotSpend() = runTest {
        val f = Fixture(); val c = f.search.prepare("question", "en")
        assertEquals(OnlineProblem.CONSENT_REQUIRED,
            rejected { f.search.search("question", "en", c, BigDecimal("0.01"), false) }.problem)
        assertEquals(0, f.calls)
    }
    @Test fun changedQueryOrCredentialInvalidatesConsent() = runTest {
        val f = Fixture(); val c = f.search.prepare("question", "en")
        f.storage.putString("openrouter_api_key", "different-key")
        assertEquals(OnlineProblem.CONSENT_EXPIRED,
            rejected { f.search.search("question", "en", c, BigDecimal("0.01"), true) }.problem)
        val d = f.search.prepare("question", "en")
        assertEquals(OnlineProblem.CONSENT_EXPIRED,
            rejected { f.search.search("other question", "en", d, BigDecimal("0.01"), true) }.problem)
        assertEquals(0, f.calls)
    }
    @Test fun revokedKidsOrLocalOnlyAccessBlocksBeforeRequest() = runTest {
        val f = Fixture(); val c = f.search.prepare("question", "en")
        f.allowed = false
        assertEquals(OnlineProblem.CHILD_RESTRICTED,
            rejected { f.search.search("question", "en", c, BigDecimal("0.01"), true) }.problem)
        f.allowed = true; f.modes.set(PrivacyMode.PRIVATE)
        assertEquals(OnlineProblem.LOCAL_ONLY,
            rejected { f.search.search("question", "en", c, BigDecimal("0.01"), true) }.problem)
        assertEquals(0, f.calls)
    }
    @Test fun budgetRejectionMakesNoRequest() = runTest {
        val f = Fixture(); OnlineOperationLedger(f.storage).configure(BigDecimal.ZERO, null)
        val c = f.search.prepare("question", "en")
        assertEquals(OnlineProblem.BUDGET_REACHED,
            rejected { f.search.search("question", "en", c, BigDecimal("0.01"), true) }.problem)
        assertEquals(0, f.calls)
    }
    @Test fun approvedSearchIsSingleUseAndReportsCostWithoutPrivateContent() = runTest {
        val f = Fixture(); val c = f.search.prepare("private query", "lv")
        val answer = f.search.search("private query", "lv", c, BigDecimal("0.01"), true)
        assertEquals("https://example.org/report", answer.sources.single().url)
        assertEquals(BigDecimal("0.007"), OnlineOperationLedger(f.storage).snapshot().reportedUsd)
        assertEquals(OnlineProblem.CONSENT_REQUIRED,
            rejected { f.search.search("private query", "lv", c, BigDecimal("0.01"), true) }.problem)
        assertEquals(1, f.calls)
        assertFalse(f.storage.getString(OnlineOperationLedger.RECORDS_KEY).orEmpty().contains("private query"))
        assertFalse(f.storage.getString(OnlineOperationLedger.RECORDS_KEY).orEmpty().contains("test-key"))
    }
    @Test fun missingSourcesFailButDoNotEraseReportedCharge() = runTest {
        val f = Fixture(response = """{"choices":[{"finish_reason":"stop","message":{"content":"Unsupported answer"}}],"usage":{"cost":0.007}}""")
        val c = f.search.prepare("question", "en")
        assertEquals(OnlineProblem.NO_SOURCES,
            rejected { f.search.search("question", "en", c, BigDecimal("0.01"), true) }.problem)
        assertEquals(BigDecimal("0.007"), OnlineOperationLedger(f.storage).snapshot().reportedUsd)
        assertEquals("UNCERTAIN", OnlineOperationLedger(f.storage).snapshot().records.single().state)
    }
    @Test fun imageWithoutApprovalCannotCallProvider() = runTest {
        val f = Fixture()
        val service = WorkspaceImageService(f.router, f.storage, privacyModes=f.modes.mode)
        assertEquals(OnlineProblem.CONSENT_REQUIRED,
            rejected { service.generate(f.choice, "draw", null, "image/png") }.problem)
        assertEquals(0, f.provider.imageCalls)
    }
    @Test fun imageApprovalIsConsumedEvenOnProviderFailure() = runTest {
        val f = Fixture(); val service = WorkspaceImageService(f.router, f.storage, privacyModes=f.modes.mode)
        val consent = service.prepareSpend(f.choice, "draw", null, "image/png")
        try { service.generate(f.choice, "draw", null, "image/png", consent, BigDecimal("0.10"), true); fail("Expected provider timeout") }
        catch (_: AiProviderException) { }
        assertEquals(OnlineProblem.CONSENT_REQUIRED,
            rejected { service.generate(f.choice, "draw", null, "image/png", consent, BigDecimal("0.10"), true) }.problem)
        assertEquals(1, f.provider.imageCalls)
        val row = OnlineOperationLedger(f.storage).snapshot().records.single()
        assertNull(row.reportedUsd)
        assertEquals(BigDecimal("0.10"), row.reservationUsd)
    }
    @Test fun revokedAccessDuringReservationStopsSearchBeforeTransport() = runTest {
        val f = Fixture(); val c = f.search.prepare("question", "en")
        f.disk.afterWrite = { if (it == OnlineOperationLedger.RECORDS_KEY) f.allowed = false }
        assertEquals(OnlineProblem.CHILD_RESTRICTED,
            rejected { f.search.search("question", "en", c, BigDecimal("0.01"), true) }.problem)
        assertEquals(0, f.calls)
    }
    @Test fun keyChangedDuringReservationStopsSearchBeforeTransport() = runTest {
        val f = Fixture(); val c = f.search.prepare("question", "en")
        f.disk.afterWrite = { if (it == OnlineOperationLedger.RECORDS_KEY) f.storage.putString("openrouter_api_key", "replacement") }
        assertEquals(OnlineProblem.CONSENT_EXPIRED,
            rejected { f.search.search("question", "en", c, BigDecimal("0.01"), true) }.problem)
        assertEquals(0, f.calls)
    }
    @Test fun reviewedSearchUsesSelectedOpenRouterModelAndItsAutomaticSearchEngine() = runTest {
        val f = Fixture()
        f.router.selectModel("openai/gpt-5.2")
        val review = f.search.prepareReview("question", "en")
        assertEquals("openai/gpt-5.2", review.model)
        assertEquals("native_or_exa", review.searchMode)
        f.search.search("question", "en", review.challenge, BigDecimal("0.01"), true)
        assertEquals("openai/gpt-5.2", f.request.getString("model"))
        assertFalse(f.request.getJSONArray("plugins").getJSONObject(0).has("engine"))
        assertEquals(review.model, OnlineOperationLedger(f.storage).snapshot().records.single().model)
    }
    @Test fun changingTheSearchModelRequiresFreshApproval() = runTest {
        val f = Fixture()
        val approval = f.search.prepare("question", "en")
        f.router.selectModel("openai/gpt-5.2")
        assertEquals(OnlineProblem.CONSENT_EXPIRED,
            rejected { f.search.search("question", "en", approval, BigDecimal("0.01"), true) }.problem)
        assertEquals(0, f.calls)
        assertTrue(OnlineOperationLedger(f.storage).snapshot().records.isEmpty())
    }
    @Test fun supportedOpenAiModelUsesResponsesSearchWithItsOwnKey() = runTest {
        val f = Fixture(providerId = "openai", response = """{"status":"completed","output":[
            {"type":"web_search_call","status":"completed"},
            {"type":"message","role":"assistant","content":[{"type":"output_text","text":"Fact (source)","annotations":[
                {"type":"url_citation","start_index":5,"end_index":13,"url":"https://example.org/report","title":"Report"}
            ]}]}
        ]}""")
        f.router.selectModel("gpt-5.4-mini")
        val approval = f.search.prepareReview("public question", "en")
        assertEquals("openai", approval.providerId)
        val answer = f.search.search("public question", "en", approval.challenge, BigDecimal("0.01"), true)
        assertEquals("openai", answer.providerId)
        assertEquals("api.openai.com", f.requestHost)
        assertEquals("web_search", f.request.getJSONArray("tools").getJSONObject(0).getString("type"))
        assertFalse(f.request.getBoolean("store"))
        assertNull(OnlineOperationLedger(f.storage).snapshot().records.single().reportedUsd)
    }
    @Test fun revokedAccessDuringReservationStopsImageBeforeProvider() = runTest {
        val f = Fixture(); val service = WorkspaceImageService(f.router, f.storage, { f.allowed },privacyModes=f.modes.mode)
        val c = service.prepareSpend(f.choice, "draw", null, "image/png")
        f.disk.afterWrite = { if (it == OnlineOperationLedger.RECORDS_KEY) f.allowed = false }
        try { service.generate(f.choice, "draw", null, "image/png", c, BigDecimal("0.1"), true); fail("Expected revoked access") }
        catch (_: com.halo.app.ai.WorkspaceImageUnavailable) { }
        assertEquals(0, f.provider.imageCalls)
    }
    @Test fun discoveryPreflightDoesNotCreateSpendOrTransportAndTracksAccessChanges() {
        val f = Fixture()
        repeat(3) { assertNull(f.search.availability()) }
        assertEquals(0, f.calls)
        assertTrue(OnlineOperationLedger(f.storage).snapshot().records.isEmpty())
        f.allowed = false
        assertEquals(OnlineProblem.CHILD_RESTRICTED, f.search.availability())
        f.allowed = true
        f.storage.putString("openrouter_api_key", null)
        assertEquals(OnlineProblem.KEY_REQUIRED, f.search.availability())
        f.modes.set(PrivacyMode.PRIVATE)
        assertEquals(OnlineProblem.LOCAL_ONLY, f.search.availability())
        assertEquals(0, f.calls)
    }
    private suspend fun rejected(block: suspend () -> Unit): OnlineOperationRejected {
        try { block() } catch (error: OnlineOperationRejected) { return error }
        throw AssertionError("Expected rejection")
    }
    private class Fixture(response: String = """{"model":"free-resolved","choices":[{"finish_reason":"stop","message":{"content":"Answer","annotations":[{"type":"url_citation","url_citation":{"url":"https://example.org/report","title":"Report"}}]}}],"usage":{"cost":0.007}}""",
        providerId: String = "openrouter") {
        val disk=MemoryStorage().apply {putString("${providerId}_api_key","test-key")}
        val modes=PrivacyModeStore(disk).apply {remoteAllowed={true};configuredProviders={setOf(providerId)};acknowledgeSharing();set(PrivacyMode.CLOUD)}
        val storage=modes.storage.apply {putBoolean(KEY_CLOUD_WORKSPACE,true)}
        val provider = ImageProvider(providerId)
        val router = Router(provider)
        val choice = WorkspaceImageChoice(provider.id, provider.displayName, AiImageModel("image-model"))
        var allowed = true
        var calls = 0
        lateinit var request: org.json.JSONObject
        var requestHost = ""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            requestHost = chain.request().url.host
            chain.request().body?.let { body ->
                val bytes = okio.Buffer()
                body.writeTo(bytes)
                request = org.json.JSONObject(bytes.readUtf8())
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val search = GroundedWebSearchService(storage, router, { allowed }, OpenRouterOnlineClient(http), modes.mode,
            OpenAiWebSearchClient(http))
    }
    private class ImageProvider(override val id: String = "openrouter") : AiImageProvider {
        override val displayName = "Fixture"
        override val isLocal = false
        override val status = MutableStateFlow(ProviderStatus.READY)
        var imageCalls = 0
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.IMAGE)
        override suspend fun listModels() = emptyList<AiModel>()
        override suspend fun listImageModels() = listOf(AiImageModel("image-model"))
        override suspend fun generateImage(request: AiImageRequest): AiImageResult {
            imageCalls++; throw AiProviderException(AiErrorType.TIMEOUT, "simulated timeout")
        }
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = error("Unused")
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
    }
    private class Router(private val provider: AiProvider) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(provider.id)
        override val selectedModelId = MutableStateFlow<String?>("openrouter/free")
        override val routingPolicy = MutableStateFlow(AiRoutingPolicy.EXPLICIT)
        override fun selectProvider(id: String) { selectedProviderId.value = id }
        override fun selectModel(modelId: String?) { selectedModelId.value = modelId }
        override fun getAllProviders() = listOf(provider)
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget = error("Unused")
        override suspend fun getEligibleProvider(request: AiRequest) = provider
        override suspend fun execute(request: AiRequest, context: AiContext): AiResponse = error("Unused")
        override fun start() = Unit
        override fun stop() = Unit
    }
    private class MemoryStorage : Storage {
        var afterWrite: (String) -> Unit = {}
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value; afterWrite(key) }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() = values.clear()
    }
}
