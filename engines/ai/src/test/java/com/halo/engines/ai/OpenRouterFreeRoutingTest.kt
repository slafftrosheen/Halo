package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/** Source regression cases for #40. Execution is deliberately deferred to owner QA. */
class OpenRouterFreeRoutingTest {
    @Test fun automaticVisionCanChooseAConcreteFreeImageModelWithoutChangingChat() = runTest {
        val imageModel = "example/vision:free"
        val storage = selection(FREE).apply { putBoolean(PaidMediaPolicy.CONSENT_KEY, true) }
        val provider = Provider(OPENROUTER, listOf(PAID, FREE, imageModel), visionModels = setOf(PAID, imageModel))
        val router = router(storage, provider)
        val target = router.resolveExecutionTarget(AiRequest(image = byteArrayOf(1)),
            AiRoleRequirements.workload(AiModelRole.VISION))
        assertEquals(imageModel, target.modelId)
        assertEquals(FREE, router.selectedModelId.value)
        assertTrue(provider.generatedModels.isEmpty())
    }

    @Test fun automaticVisionNeverUsesAPaidImageModelToReplaceAFreeDefault() = runTest {
        val storage = selection(FREE).apply { putBoolean(PaidMediaPolicy.CONSENT_KEY, true) }
        val provider = Provider(OPENROUTER, listOf(PAID, FREE), visionModels = setOf(PAID))
        val router = router(storage, provider)
        assertTrue(runCatching { router.resolveExecutionTarget(AiRequest(image = byteArrayOf(1)),
            AiRoleRequirements.workload(AiModelRole.VISION)) }.isFailure)
        assertTrue(provider.generatedModels.isEmpty())
    }

    @Test fun providerOnlySelectionUsesFreeEvenWhenPaidModelSortsFirst() = runTest {
        val provider = Provider(OPENROUTER, listOf(PAID, FREE))
        val storage = selection(null)
        val router = router(storage, provider)
        assertEquals(FREE, router.resolveExecutionTarget(AiRequest()).modelId)
        assertNull(storage.getString(AiSelectionRepository.MODEL_KEY))
        assertEquals(FREE, router(storage, provider).resolveExecutionTarget(AiRequest()).modelId)
    }

    @Test fun blankSelectionAndLegacyFreeAliasResolveToCanonicalRoute() = runTest {
        for (model in listOf("", "  ", "free", " openrouter/free ")) {
            val router = router(selection(model), Provider(OPENROUTER, listOf(PAID, FREE)))
            assertEquals(FREE, router.resolveExecutionTarget(AiRequest()).modelId)
        }
    }

    @Test fun legacyFreeAliasInRequestResolvesToCanonicalRoute() = runTest {
        val router = router(selection(null), Provider(OPENROUTER, listOf(PAID, FREE)))
        assertEquals(FREE, router.resolveExecutionTarget(AiRequest(modelId = "free")).modelId)
    }

    @Test fun missingFreeRouteCannotRunFirstPaidModel() = runTest {
        val provider = Provider(OPENROUTER, listOf(PAID))
        val router = router(selection(null), provider)
        var failure: AiProviderException? = null
        try { router.resolveExecutionTarget(AiRequest()) }
        catch (error: AiProviderException) { failure = error }
        assertEquals(AiErrorType.MODEL_NOT_FOUND, failure?.type)
        assertTrue(provider.generatedModels.isEmpty())
    }

    @Test fun explicitPaidConversationChoiceIsPreserved() = runTest {
        val router = router(selection(PAID), Provider(OPENROUTER, listOf(FREE, PAID)))
        assertEquals(PAID, router.resolveExecutionTarget(AiRequest()).modelId)
    }

    @Test fun explicitFreeVariantIsPreserved() = runTest {
        val variant = "example/model:free"
        val router = router(selection(variant), Provider(OPENROUTER, listOf(PAID, variant, FREE)))
        assertEquals(variant, router.resolveExecutionTarget(AiRequest()).modelId)
    }

    @Test fun missingFreeSelectionCannotFallThroughToAnotherRemoteProvider() = runTest {
        val router = router(selection(FREE, AiRoutingPolicy.PREFERRED_WITH_FALLBACK),
            Provider(OPENROUTER, listOf(PAID)), Provider("another-cloud", listOf(PAID)))
        var failure: AiProviderException? = null
        try { router.resolveExecutionTarget(AiRequest()) }
        catch (error: AiProviderException) { failure = error }
        assertNotNull(failure)
        assertNull(router.lastRoutingDecision.value?.resolvedProviderId)
    }

    @Test fun freeRouteMayUseLocalFallbackWhenPolicyAllowsIt() = runTest {
        val router = router(selection(FREE, AiRoutingPolicy.PREFERRED_WITH_FALLBACK),
            Provider(OPENROUTER, listOf(PAID)), Provider(LOCAL, listOf("local"), local = true),
            Provider("another-cloud", listOf(PAID)))
        val target = router.resolveExecutionTarget(AiRequest())
        assertEquals(LOCAL, target.providerId)
        assertEquals("local", target.modelId)
    }

    @Test fun freeRouteGenerationFallbackExcludesOtherRemoteProviders() = runTest {
        val router = router(selection(FREE, AiRoutingPolicy.PREFERRED_WITH_FALLBACK),
            Provider(OPENROUTER, listOf(FREE)), Provider("another-cloud", listOf(PAID)))
        assertNull(router.resolveFallbackExecutionTarget(AiRequest(modelId = FREE), AiWorkloadSpec(), OPENROUTER))
    }

    @Test fun freeRouteGenerationFallbackCanChooseLocal() = runTest {
        val router = router(selection(FREE, AiRoutingPolicy.PREFERRED_WITH_FALLBACK),
            Provider(OPENROUTER, listOf(FREE)), Provider("another-cloud", listOf(PAID)),
            Provider(LOCAL, listOf("local"), local = true))
        assertEquals(LOCAL, router.resolveFallbackExecutionTarget(AiRequest(modelId = FREE), AiWorkloadSpec(), OPENROUTER)?.providerId)
    }

    @Test fun inheritedReasoningUsesFreeDefault() = runTest {
        val storage = selection(null)
        AiSelectionRepository(storage).writeRoleBinding(AiRoleBinding(AiModelRole.REASONING,
            null, null, AiRoleSelection.INHERIT_DEFAULT))
        val router = router(storage, Provider(OPENROUTER, listOf(PAID, FREE)))
        assertEquals(FREE, router.resolveExecutionTarget(AiRequest(), reasoning()).modelId)
    }

    @Test fun automaticReasoningDoesNotUpgradeFreeDefault() = runTest {
        val storage = selection(FREE)
        AiSelectionRepository(storage).writeRoleBinding(AiRoleBinding(AiModelRole.REASONING,
            null, null, AiRoleSelection.AUTOMATIC))
        val router = router(storage, Provider("aaa-paid-cloud", listOf(PAID)),
            Provider(OPENROUTER, listOf(PAID, FREE)))
        val target = router.resolveExecutionTarget(AiRequest(), reasoning())
        assertEquals(OPENROUTER, target.providerId)
        assertEquals(FREE, target.modelId)
    }

    @Test fun measuredAutomaticTargetCannotOverrideFreeBoundary() = runTest {
        val storage = selection(FREE)
        AiSelectionRepository(storage).writeRoleBinding(AiRoleBinding(AiModelRole.REASONING,
            null, null, AiRoleSelection.AUTOMATIC))
        val router = DefaultAiProviderRouter(
            AiProviderRegistry.from(listOf(Provider(OPENROUTER, listOf(PAID, FREE)),
                Provider("another-cloud", listOf(PAID)))), storage,
            qualifiedAutomaticTarget = { AiModelRef("another-cloud", PAID) })
        val target = router.resolveExecutionTarget(AiRequest(), reasoning())
        assertEquals(OPENROUTER, target.providerId)
        assertEquals(FREE, target.modelId)
    }

    @Test fun explicitPaidRoleRemainsAnIndependentOptIn() = runTest {
        val storage = selection(FREE)
        AiSelectionRepository(storage).writeRoleBinding(AiRoleBinding(AiModelRole.REASONING,
            "another-cloud", PAID, AiRoleSelection.EXPLICIT))
        val router = router(storage, Provider(OPENROUTER, listOf(FREE)),
            Provider("another-cloud", listOf(PAID)))
        val target = router.resolveExecutionTarget(AiRequest(), reasoning())
        assertEquals("another-cloud", target.providerId)
        assertEquals(PAID, target.modelId)
    }

    @Test fun requestCannotQuietlyReplaceFreeSelectionWithPaidModel() = runTest {
        val provider = Provider(OPENROUTER, listOf(FREE, PAID))
        val router = router(selection(FREE), provider)
        var failure: AiProviderException? = null
        try { router.resolveExecutionTarget(AiRequest(modelId = PAID)) }
        catch (error: AiProviderException) { failure = error }
        assertEquals(AiErrorType.CAPABILITY_DISABLED, failure?.type)
        assertTrue(provider.generatedModels.isEmpty())
        assertEquals(0, provider.catalogReads)
    }

    @Test fun failedFreeRequestStaysFreeEvenAfterPreferenceChanges() = runTest {
        val router = router(selection(PAID, AiRoutingPolicy.PREFERRED_WITH_FALLBACK),
            Provider(OPENROUTER, listOf(FREE, PAID)), Provider("another-cloud", listOf(PAID)))
        assertNull(router.resolveFallbackExecutionTarget(AiRequest(modelId = FREE), AiWorkloadSpec(), OPENROUTER))
    }

    @Test fun localOnlyPolicyStillExcludesOpenRouter() = runTest {
        val router = router(selection(FREE, AiRoutingPolicy.LOCAL_ONLY),
            Provider(OPENROUTER, listOf(FREE)), Provider(LOCAL, listOf("local"), local = true))
        assertEquals(LOCAL, router.resolveExecutionTarget(AiRequest()).providerId)
    }

    @Test fun nonOpenRouterSelectionIsUnchanged() = runTest {
        val storage = selection(PAID).apply { putString(AiSelectionRepository.PROVIDER_KEY, "another-cloud") }
        val router = router(storage, Provider(OPENROUTER, listOf(FREE)),
            Provider("another-cloud", listOf(PAID)))
        assertEquals("another-cloud", router.resolveExecutionTarget(AiRequest()).providerId)
    }

    private fun reasoning() = AiWorkloadSpec(preferredRole = AiModelRole.REASONING)
    private fun selection(model: String?, policy: AiRoutingPolicy = AiRoutingPolicy.EXPLICIT) = MemoryStorage().apply {
        putString(AiSelectionRepository.PROVIDER_KEY, OPENROUTER)
        putString(AiSelectionRepository.MODEL_KEY, model)
        putString(AiSelectionRepository.POLICY_KEY, policy.name)
        putBoolean(AiSelectionRepository.MIGRATION_COMPLETE_KEY, true)
    }
    private fun router(storage: Storage, vararg providers: AiProvider) = DefaultAiProviderRouter(providers.toList(), storage)

    private class Provider(override val id: String, private val models: List<String>, private val local: Boolean = false,
        private val visionModels: Set<String> = emptySet()) : AiInferenceProvider {
        override val displayName = id
        override val isLocal = local
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        val generatedModels = mutableListOf<String?>()
        var catalogReads = 0
            private set
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = if (visionModels.isEmpty()) setOf(AiCapability.TEXT)
            else setOf(AiCapability.TEXT, AiCapability.IMAGE)
        override suspend fun listModels(): List<AiModel> {
            catalogReads++
            return models.map {
                AiModel(it, it, id, if (it in visionModels) setOf(AiCapability.TEXT, AiCapability.IMAGE)
                    else setOf(AiCapability.TEXT), 4096, false, false,
                    capabilityEvidence = if (it in visionModels) mapOf(AiCapability.IMAGE to CapabilityEvidence.AUTHORITATIVE_METADATA) else emptyMap(),
                    isFree = OpenRouterFreeRouting.isFreeModel(it),
                    executionLocation = if (local) AiExecutionLocation.ON_DEVICE else AiExecutionLocation.REMOTE)
            }
        }
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            generatedModels += request.modelId
            return AiResponse(text = "ok", providerId = id, modelId = request.modelId)
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
    }

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

    private companion object {
        const val OPENROUTER = "openrouter"
        const val FREE = "openrouter/free"
        const val PAID = "aaa/paid-model"
        const val LOCAL = "provider.litert.local"
    }
}
