package com.halo.app.ai

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.halo.engines.ai.DefaultAiProviderRouter
import com.halo.engines.ai.catalog.AiModelCatalogRepository
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProviderControlSafetyTest {
    private class MemoryStorage : Storage {
        val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
    private class FixtureProvider(val storage: Storage) : AiInferenceProvider, CredentialProvider, ProviderConfigurationReporting {
        override val id = "fixture"
        override val displayName = "Fixture"
        override val isLocal = false
        override val status = MutableStateFlow(ProviderStatus.READY)
        var unreadable = false
        var failWrite = false
        var generations = 0
        var auth: suspend () -> Boolean = { true }
        var lastRequest: AiRequest? = null
        override val configurationDescriptor = ProviderConfigurationDescriptor(id, displayName, AiExecutionLocation.REMOTE,
            listOf(ProviderConfigurationField("credential", "Key", ProviderFieldKind.SECRET),
                ProviderConfigurationField("fixture_endpoint", "Endpoint", ProviderFieldKind.ENDPOINT)), "FIXTURE", "FIXTURE")
        override fun hasCredential(): Boolean { check(!unreadable) { "fixture-secret-read-error" }; return credential() != null }
        override fun credential() = storage.getString("fixture_key")?.let { Credential(id, it) }
        override fun setCredential(credential: Credential) { check(!failWrite) { "fixture-secret-write-error" }; storage.putString("fixture_key", credential.value) }
        override fun clearCredential() { storage.remove("fixture_key") }
        override suspend fun listModels(): List<AiModel> = emptyList()
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun testConnection() = auth()
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            generations++; lastRequest = request
            assertEquals(AiContext.EMPTY, context)
            return AiResponse("HALO.", providerId = id, modelId = "text")
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
    }
    private suspend fun fixture(catalogueRefresh: suspend () -> Unit = {},
        block: suspend (ProviderControlRepository, FixtureProvider, MemoryStorage) -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val store = ViewModelStore()
        try {
            val storage = MemoryStorage().apply { putString("fixture_key", "fixture-old"); putString("fixture_endpoint", "https://fixture.test/v1") }
            val provider = FixtureProvider(storage)
            val model = AiModel("text", "Text", provider.id, setOf(AiCapability.TEXT), 1024, false, false)
            val catalogue = object : AiModelCatalogRepository {
                override val state = MutableStateFlow(AiModelCatalogState(models = listOf(
                    AiModelCatalogEntry(model, AiModelRuntimeMetadata(AiModelRef(provider.id, model.id), AiExecutionLocation.REMOTE,
                        true, false, false, false), source = ModelCatalogueSource.REMOTE_LIVE))))
                override suspend fun refresh() = catalogueRefresh()
            }
            val router = DefaultAiProviderRouter(listOf(provider), storage, defaultInferenceProviderId = provider.id, defaultInferenceModelId = model.id)
            val control = ProviderControlRepository(router, catalogue, storage)
            store.put("control", control)
            withContext(Dispatchers.Default) { withTimeout(5000) { control.rows.first { it.isNotEmpty() } } }
            block(control, provider, storage)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
    @Test fun providerConfigurationIsVisibleWhileLocalInventoryRefreshIsPending() = runTest {
        fixture(catalogueRefresh = { awaitCancellation() }) { control, provider, _ ->
            val row = control.rows.value.single()
            assertEquals(provider.id, row.descriptor.providerId)
            assertTrue(row.configured)
            assertTrue(row.selected)
            assertTrue(control.useDefault(provider.id))
        }
    }
    @Test fun saveDoesNotAuthenticateOrLoseTheKeyWhileOffline() = runTest {
        fixture { control, provider, storage ->
            provider.auth = { error("Connection must not run during save") }
            val done = CompletableDeferred<Boolean>()
            control.save(provider.id, emptyMap(), "fixture-new") { done.complete(it) }
            assertTrue(withContext(Dispatchers.Default) { withTimeout(5000) { done.await() } })
            assertEquals("fixture-new", storage.getString("fixture_key"))
            assertEquals(ProviderTestOutcome.SAVED_UNTESTED, control.lastDiagnostic(provider.id)?.outcome)
            assertEquals(provider.id, control.router.selectedProviderId.value)
        }
    }
    @Test fun rotatedKeyCannotInheritAnOlderConnectionResult() = runTest {
        fixture { control, provider, storage ->
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            provider.auth = {
                entered.complete(Unit); release.await()
                provider.status.value = ProviderStatus(ProviderStatus.State.ERROR, "stale auth response",
                    rejectionReason = TargetRejectionReason.CONFIGURATION_MISSING)
                true
            }
            val check = control.testConnection(provider.id)
            withContext(Dispatchers.Default) { withTimeout(5000) { entered.await() } }
            val done = CompletableDeferred<Boolean>()
            control.save(provider.id, emptyMap(), "fixture-rotated") { done.complete(it) }
            withContext(Dispatchers.Default) { withTimeout(5000) { done.await() } }
            release.complete(Unit)
            withContext(Dispatchers.Default) { check.join() }
            assertEquals("fixture-rotated", storage.getString("fixture_key"))
            assertEquals(ProviderTestOutcome.SAVED_UNTESTED, control.lastDiagnostic(provider.id)?.outcome)
            assertEquals(ProviderConnectionState.CONFIGURED, control.rows.value.single().connection)
        }
    }
    @Test fun billableGenerationNeedsConsentAndMarkerPunctuationIsSeparateFromProof() = runTest {
        fixture { control, provider, _ ->
            withContext(Dispatchers.Default) { control.checkGeneration(provider.id).join() }
            assertEquals(0, provider.generations)
            assertEquals(ProviderTestOutcome.CONSENT_REQUIRED, control.lastDiagnostic(provider.id)?.outcome)
            withContext(Dispatchers.Default) { control.checkGeneration(provider.id, allowBillable = true).join() }
            assertEquals(GenerationProof.PROVEN, control.rows.value.single().generation)
            assertEquals(false, control.rows.value.single().generationEvidence?.sentinelExactness)
            assertEquals(256, provider.lastRequest?.maxOutputTokens)
            assertEquals(ToolUseMode.NONE, provider.lastRequest?.toolUseMode)
        }
    }
    @Test fun unreadableCredentialsProduceAnIsolatedProtectedStoreFailure() = runTest {
        fixture { control, provider, _ ->
            provider.unreadable = true
            withContext(Dispatchers.Default) { control.testConnection(provider.id).join() }
            assertEquals(ProviderTestOutcome.SECRET_STORE_ERROR, control.rows.value.single().diagnostic?.outcome)
            assertFalse(control.rows.value.single().configured)
            assertFalse(control.rows.value.single().toString().contains("fixture-secret-read-error"))
        }
    }
    @Test fun generationProofIsClearedWhenTheSelectedModelChangesAndDoesNotFallback() = runTest {
        fixture { control, provider, _ ->
            withContext(Dispatchers.Default) { control.checkGeneration(provider.id, true).join() }
            assertEquals(GenerationProof.PROVEN, control.rows.value.single().generation)
            control.router.selectModel("missing-model")
            withContext(Dispatchers.Default) { withTimeout(5000) {
                control.rows.first { it.single().lastSelectedModelId == "missing-model" && it.single().generation != GenerationProof.PROVEN }
            } }
            withContext(Dispatchers.Default) { control.checkGeneration(provider.id, true).join() }
            assertEquals(1, provider.generations)
            assertEquals(ProviderTestOutcome.MODEL_REQUIRED, control.lastDiagnostic(provider.id)?.outcome)
        }
    }

    @Test fun protectedWriteErrorsRemainSafeAndKeepThePreviousKey() = runTest {
        fixture { control, provider, storage ->
            provider.failWrite = true
            val done = CompletableDeferred<Boolean>()
            control.save(provider.id, emptyMap(), "fixture-new") { done.complete(it) }
            assertFalse(withContext(Dispatchers.Default) { withTimeout(5000) { done.await() } })
            assertEquals("fixture-old", storage.getString("fixture_key"))
            assertEquals(ProviderTestOutcome.SECRET_STORE_ERROR, control.lastDiagnostic(provider.id)?.outcome)
            assertFalse(control.rows.value.single().toString().contains("fixture-secret-write-error"))
        }
    }
}
