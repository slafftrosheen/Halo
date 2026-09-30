package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AiSelectionPersistenceTest {
    private class MemoryStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() = values.clear()
    }

    private class Provider(override val id: String, private val modelId: String) : AiInferenceProvider {
        override val displayName = id
        override val isLocal = id == LITERT
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun listModels() = listOf(
            AiModel(modelId, modelId, id, setOf(AiCapability.TEXT), 4096, false, false),
        )
        override suspend fun generate(request: AiRequest, context: AiContext) =
            AiResponse(text = "ok", providerId = id, modelId = modelId)
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
    }


    @Test fun freshInstallSelectsAndPersistsLocalQwenDefault() {
        val storage = MemoryStorage()
        val first = router(storage, providers())
        assertSelection(first, LITERT, QWEN)

        val reconstructed = router(storage, providers().reversed())
        assertSelection(reconstructed, LITERT, QWEN)
    }

    @Test fun legacyLiteRtSelectionWithoutModelGetsQwenDefaultOnce() {
        val storage = MemoryStorage().apply {
            putString(AiSelectionRepository.PROVIDER_KEY, LITERT)
            putString(AiSelectionRepository.POLICY_KEY, AiRoutingPolicy.EXPLICIT.name)
            putBoolean(AiSelectionRepository.MIGRATION_COMPLETE_KEY, true)
        }
        val first = router(storage, providers())
        assertSelection(first, LITERT, QWEN)
        assertSelection(router(storage, providers().reversed()), LITERT, QWEN)
    }

    /**
     * The marker must not be able to preserve a provider Halo no longer ships. A start that
     * happened before the legacy key was restored (backup restore, earlier process, copied
     * preferences) sets the marker, and the retired id must still be repaired afterwards.
     */
    @Test fun retiredProviderIdIsRepairedEvenAfterTheMigrationMarkerIsSet() {
        val storage = MemoryStorage().apply {
            putString(AiSelectionRepository.PROVIDER_KEY, DefaultAiProviderRouter.LEGACY_LLAMACPP_PROVIDER_ID)
            putString("litert_model", QWEN)
            putString(AiSelectionRepository.POLICY_KEY, AiRoutingPolicy.EXPLICIT.name)
            putBoolean(AiSelectionRepository.MIGRATION_COMPLETE_KEY, true)
        }

        val restored = router(storage, providers())

        assertSelection(restored, LITERT, QWEN)
        assertEquals(LITERT, storage.getString(AiSelectionRepository.PROVIDER_KEY))
        assertSelection(router(storage, providers().reversed()), LITERT, QWEN)
    }

    @Test fun aCompletedMigrationStillAvoidsRewritingAProviderSpecificModelKey() {
        val storage = MemoryStorage().apply {
            putString(AiSelectionRepository.PROVIDER_KEY, LITERT)
            putString(AiSelectionRepository.MODEL_KEY, QWEN)
            putString(AiSelectionRepository.POLICY_KEY, AiRoutingPolicy.EXPLICIT.name)
            putString("litert_model", "some-other-stale-model")
            putBoolean(AiSelectionRepository.MIGRATION_COMPLETE_KEY, true)
        }

        assertSelection(router(storage, providers()), LITERT, QWEN)
        assertEquals(QWEN, storage.getString(AiSelectionRepository.MODEL_KEY))
    }

    @Test fun litertQwenSurvivesProcessReconstruction() = assertRestart(LITERT, QWEN)
    @Test fun openRouterSurvivesProcessReconstruction() = assertRestart(OPENROUTER, "openrouter/free")
    @Test fun ollamaSurvivesProcessReconstruction() = assertRestart(OLLAMA, "llama3.2")
    @Test fun geminiSurvivesProcessReconstruction() = assertRestart(GEMINI, "gemini-2.5-flash")

    @Test fun addingProviderAndChangingOrderDoesNotChangePersistedChoice() {
        val storage = MemoryStorage()
        router(storage, providers()).selectTarget(AiModelRef(LITERT, QWEN))
        val reconstructed = router(storage, listOf(Provider("new-provider", "new-model")) + providers().reversed())
        assertSelection(reconstructed, LITERT, QWEN)
    }

    @Test fun diagnosticsDoesNotMutateChoice() = runTest {
        val storage = MemoryStorage()
        val router = router(storage, providers())
        router.selectTarget(AiModelRef(GEMINI, "gemini-2.5-flash"))
        router.diagnostics()
        assertSelection(router(storage, providers().reversed()), GEMINI, "gemini-2.5-flash")
    }

    @Test fun missingSelectedLocalModelPreservesPreference() {
        val storage = MemoryStorage()
        router(storage, providers()).selectTarget(AiModelRef(LITERT, QWEN))
        val reconstructed = router(storage, listOf(Provider(LITERT, "different-installed-model")) + providers().drop(1))
        assertSelection(reconstructed, LITERT, QWEN)
    }

    private fun assertRestart(providerId: String, modelId: String) {
        val storage = MemoryStorage()
        router(storage, providers()).selectTarget(AiModelRef(providerId, modelId))
        assertSelection(router(storage, providers().reversed()), providerId, modelId)
    }

    private fun assertSelection(router: DefaultAiProviderRouter, providerId: String, modelId: String) {
        assertEquals(providerId, router.selectedProviderId.value)
        assertEquals(modelId, router.selectedModelId.value)
        assertEquals(AiRoutingPolicy.EXPLICIT, router.routingPolicy.value)
    }

    private fun router(storage: Storage, providers: List<AiProvider>) =
        DefaultAiProviderRouter(
            providers,
            storage,
            defaultInferenceProviderId = LITERT,
            defaultInferenceModelId = QWEN,
        )

    private fun providers(): List<AiProvider> = listOf(
        Provider(LITERT, QWEN),
        Provider(OPENROUTER, "openrouter/free"),
        Provider(OLLAMA, "llama3.2"),
        Provider(GEMINI, "gemini-2.5-flash"),
    )

    private companion object {
        const val LITERT = "provider.litert.local"
        const val QWEN = "qwen3-0.6b-dynamic-int4"
        const val OPENROUTER = "openrouter"
        const val OLLAMA = "provider.ollama"
        const val GEMINI = "gemini"
    }
}
