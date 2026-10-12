package com.halo.app.ai

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.halo.app.ui.screens.settings.ModelBrowserViewModel
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
@Config(application = Application::class)
class RoleBrowserCapabilityTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun browserUsesExactTruthAndPickingRolePreservesConversationDefault() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val values = mutableMapOf<String, Any?>()
            val storage = object : Storage {
                override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
                override fun putString(key: String, value: String?) { values[key] = value }
                override fun getInt(key: String, defaultValue: Int) = defaultValue
                override fun putInt(key: String, value: Int) = Unit
                override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
                override fun putBoolean(key: String, value: Boolean) { values[key] = value }
                override fun remove(key: String) { values.remove(key) }
                override fun clear() { values.clear() }
            }
            var lists = 0
            var generations = 0
            val provider = object : AiInferenceProvider, ModelInputCapabilityReporting, ToolProtocolReporting {
                override val id = "provider.litert.local"
                override val displayName = "Local"
                override val isLocal = true
                override val status = MutableStateFlow(ProviderStatus.READY)
                val models = listOf("default", "static-native", "prompt").map { model ->
                    AiModel(model, model, id, setOf(AiCapability.TEXT), 4096, false,
                        supportsTools = model == "static-native", executionLocation = AiExecutionLocation.ON_DEVICE)
                }
                override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
                override suspend fun capabilitiesForModel(modelId: String) =
                    if (modelId == "prompt") setOf(AiCapability.TEXT, AiCapability.TOOLS) else setOf(AiCapability.TEXT)
                override suspend fun toolCallProtocol(modelId: String) =
                    if (modelId == "prompt") ToolCallProtocol.HALO_PROMPT else ToolCallProtocol.NONE
                override suspend fun listModels(): List<AiModel> { lists++; return models }
                override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
                    generations++; return AiResponse("HALO", providerId = id, modelId = "resolved-model")
                }
                override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
            }
            val catalogue = object : AiModelCatalogRepository {
                override val state = MutableStateFlow(AiModelCatalogState(models = provider.models.map {
                    AiModelCatalogEntry(it, AiModelRuntimeMetadata(AiModelRef(provider.id, it.id), AiExecutionLocation.ON_DEVICE,
                        true, false, false, false), source = ModelCatalogueSource.LOCAL_INSTALLED)
                }))
                override suspend fun refresh() = Unit
            }
            val router = DefaultAiProviderRouter(listOf(provider), storage,
                defaultInferenceProviderId = provider.id, defaultInferenceModelId = "default")
            val control = ProviderControlRepository(router, catalogue, storage)
            val browser = ModelBrowserViewModel(control)
            store.put("control", control); store.put("browser", browser)
            browser.enter(null, AiModelRole.AGENTIC)
            browser.filter.value = browser.filter.value.copy(query = "prompt")
            browser.enter(null, AiModelRole.AGENTIC)
            assertEquals("prompt", browser.filter.value.query)
            val state = withContext(Dispatchers.Default) { withTimeout(10_000) {
                browser.state.first { it.entries.isNotEmpty() }
            } }
            assertEquals(listOf("prompt"), state.entries.map { it.model.id })
            assertEquals(ToolCallProtocol.HALO_PROMPT, state.candidates.getValue(state.entries.single().runtime.ref).toolProtocol)
            assertEquals(0, lists); assertEquals(0, generations)
            val picked = CompletableDeferred<Unit>()
            browser.pick(state.entries.single()) { picked.complete(Unit) }
            withContext(Dispatchers.Default) { withTimeout(10_000) { picked.await() } }
            assertEquals("prompt", router.roleBinding(AiModelRole.AGENTIC).modelId)
            assertEquals(AiRoleSelection.EXPLICIT, router.roleBinding(AiModelRole.AGENTIC).selection)
            assertEquals("default", router.selectedModelId.value)
            assertEquals(0, generations)
            withContext(Dispatchers.Default) { control.checkGeneration(provider.id).join() }
            val proved = control.rows.value.single { it.descriptor.providerId == provider.id }
            assertEquals(GenerationProof.PROVEN, proved.generation)
            assertEquals("resolved-model", proved.generationModelId)
            assertNotEquals(proved.lastSelectedModelId, proved.generationModelId)
            assertEquals(1, generations)
            withContext(Dispatchers.Default) { control.removeCredential(provider.id).join() }
            assertNull(control.rows.value.single { it.descriptor.providerId == provider.id }.generationModelId)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
