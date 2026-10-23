package com.halo.app.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ModeProfileRoutingTest {
    @Test fun configuredButUnselectedRemoteProviderCannotBeSubstituted() = runTest {
        val fixture = Fixture()
        fixture.delegate.forced = fixture.other
        try { fixture.router.resolveExecutionTarget(AiRequest(text = "fixture")); fail("No remote substitution") }
        catch (_: AiProviderException) { }
        assertEquals(0, fixture.other.calls)
    }

    @Test fun resolutionStartedBeforeModeRoundTripCannotBorrowTheNewProfileRevision() = runTest {
        val fixture = Fixture()
        val release = CompletableDeferred<Unit>()
        fixture.delegate.gate = release
        val resolving = async { fixture.router.resolveExecutionTarget(AiRequest(text = "fixture")) }
        fixture.delegate.entered.await()
        fixture.store.set(PrivacyMode.PRIVATE)
        fixture.store.edit(PrivacyMode.CLOUD) { it.copy(strings = it.strings + ("active_ai_model" to "incoming-model")) }
        fixture.store.set(PrivacyMode.CLOUD)
        release.complete(Unit)
        val staleTarget = resolving.await()
        try { staleTarget.provider.generate(AiRequest(text = "fixture", modelId = staleTarget.modelId), AiContext.EMPTY); fail("Stale resolution") }
        catch (_: RemoteDispatchBlocked) { }
        assertEquals(0, fixture.primary.calls)
    }

    @Test fun localFallbackRequiresAnExplicitProfileChoice() = runTest {
        val fixture = Fixture()
        fixture.delegate.forced = fixture.local
        try { fixture.router.resolveExecutionTarget(AiRequest(text = "fixture")); fail("Local fallback was not enabled") }
        catch (_: AiProviderException) { }
        fixture.store.edit(PrivacyMode.CLOUD) { it.copy(localFallback = true,
            strings = it.strings + ("ai_routing_policy" to "PREFERRED_WITH_FALLBACK")) }
        assertEquals(fixture.local.id, fixture.router.resolveExecutionTarget(AiRequest(text = "fixture")).providerId)
    }

    @Test fun currentExplicitTargetExecutesExactlyOnceWithItsModel() = runTest {
        val fixture = Fixture()
        val target = fixture.router.resolveExecutionTarget(AiRequest(text = "fixture"))
        val answer = target.provider.generate(AiRequest(text = "fixture", modelId = target.modelId), AiContext.EMPTY)
        assertEquals("cloud-a", answer.providerId)
        assertEquals("selected-model", answer.modelId)
        assertEquals(1, fixture.primary.calls)
        assertEquals(0, fixture.other.calls)
    }

    @Test fun disabledWebFeatureStopsBeforeChallengeOrBudgetReservation() = runTest {
        val fixture = Fixture()
        fixture.store.edit(PrivacyMode.CLOUD) { it.copy(onlineFeatures = it.onlineFeatures - OnlineFeature.WEB) }
        val service = com.halo.app.online.GroundedWebSearchService(fixture.store.storage, fixture.router, { true })
        try { service.prepare("synthetic fixture", "en"); fail() }
        catch (failure: com.halo.app.online.OnlineOperationRejected) {
            assertEquals(com.halo.app.online.OnlineProblem.FEATURE_DISABLED, failure.problem)
        }
    }

    private class Fixture {
        val primary = Provider("cloud-a"); val other = Provider("cloud-b"); val local = Provider("provider.litert.local", true)
        val store = PrivacyModeStore(Memory()).apply {
            remoteAllowed = { true }; configuredProviders = { setOf("cloud-a", "cloud-b") }
            edit(PrivacyMode.CLOUD) { it.copy(strings = it.strings + mapOf("active_ai_provider" to "cloud-a", "active_ai_model" to "selected-model")) }
            acknowledgeSharing(); set(PrivacyMode.CLOUD)
        }
        val delegate = Router(store.storage, listOf(primary, other, local))
        val router = ParentControlledAiRouter(delegate, { store.mode.value == PrivacyMode.CLOUD },
            store.profileChanges.map { store.mode.value == PrivacyMode.CLOUD }, store)
    }
    private class Provider(override val id: String, override val isLocal: Boolean = false) : AiInferenceProvider {
        override val displayName = id
        override val status = MutableStateFlow(ProviderStatus(ProviderStatus.State.READY))
        var calls = 0
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun listModels() = emptyList<AiModel>()
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            calls++; return AiResponse("fixture", providerId = id, modelId = request.modelId)
        }
        override fun stream(request: AiRequest, context: AiContext) = flow { emit(AiStreamEvent.Completed(generate(request, context))) }
    }
    private class Router(private val storage: Storage, val providers: List<Provider>) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow(storage.getString("active_ai_provider"))
        override val selectedModelId = MutableStateFlow(storage.getString("active_ai_model"))
        var forced: Provider? = null
        var gate: CompletableDeferred<Unit>? = null
        val entered = CompletableDeferred<Unit>()
        override fun selectProvider(id: String) { selectedProviderId.value = id }
        override fun selectModel(modelId: String?) { selectedModelId.value = modelId }
        override fun getAllProviders(): List<AiProvider> = providers
        override suspend fun getEligibleProvider(request: AiRequest): AiProvider = resolveExecutionTarget(request).provider
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
            val provider = forced ?: providers.first { it.id == storage.getString("active_ai_provider") }
            val model = storage.getString("active_ai_model") ?: "selected-model"
            val target = target(provider, model)
            entered.complete(Unit); gate?.await()
            return target
        }
        private fun target(provider: Provider, model: String) = AiExecutionTarget(provider,
            AiModel(model, model, provider.id, setOf(AiCapability.TEXT),
                contextLength = 4096, supportsStreaming = true, supportsTools = false,
                executionLocation = if (provider.isLocal) AiExecutionLocation.ON_DEVICE else AiExecutionLocation.REMOTE), setOf(AiCapability.TEXT))
        override suspend fun execute(request: AiRequest, context: AiContext) = execute(request, AiWorkloadSpec(), context)
        override suspend fun execute(request: AiRequest, workload: AiWorkloadSpec, context: AiContext): AiResponse {
            val target = workload.pinnedTarget?.let { ref -> target(providers.first { it.id == ref.providerId }, ref.modelId) }
                ?: resolveExecutionTarget(request, workload)
            return target.provider.generate(request.copy(modelId = target.modelId), context)
        }
        override fun start() = Unit
        override fun stop() = Unit
    }
    private class Memory : Storage {
        private val strings = mutableMapOf<String, String?>(); private val booleans = mutableMapOf<String, Boolean>()
        override fun getString(key: String, defaultValue: String?) = strings[key] ?: defaultValue
        override fun putString(key: String, value: String?) { strings[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = booleans[key] ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { booleans[key] = value }
        override fun getInt(key: String, defaultValue: Int) = defaultValue
        override fun putInt(key: String, value: Int) = Unit
        override fun remove(key: String) { strings.remove(key); booleans.remove(key) }
        override fun clear() { strings.clear(); booleans.clear() }
    }
}
