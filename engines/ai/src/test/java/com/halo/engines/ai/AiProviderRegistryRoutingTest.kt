package com.halo.engines.ai

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiProviderRegistry
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.AiHandoffProvider
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiRoutingPolicy
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderRegistryRoutingTest {

    private class FakeStorage : Storage {
        private val data = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }

    private class TypedInferenceProvider(
        override val id: String,
        private val modelId: String = "typed-model"
    ) : AiInferenceProvider {
        override val displayName: String = id
        override val isLocal: Boolean = true
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)

        override suspend fun getStatus(): ProviderStatus = status.value
        override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT)
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
            AiResponse("typed response", providerId = id, modelId = request.modelId)
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels(): List<AiModel> = listOf(
            AiModel(modelId, "Typed Model", id, setOf(AiCapability.TEXT), 4096, false, false)
        )
    }

    private class TypedHandoffProvider : AiProvider, AiHandoffProvider {
        override val id: String = "handoff"
        override val displayName: String = "External Assistant"
        override val isLocal: Boolean = false
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)

        override suspend fun getStatus(): ProviderStatus = status.value
        override suspend fun getCapabilities(): Set<AiCapability> = emptySet()
        override fun isAvailable(): Boolean = true
        override fun assistantPackageName(): String = "com.example.assistant"
        override fun handoff(prompt: String?) = Unit
    }

    @Test
    fun `registry separates inference and handoff contracts`() {
        val inference = TypedInferenceProvider("typed")
        val handoff = TypedHandoffProvider()
        val registry = AiProviderRegistry.from(listOf(inference, handoff))

        assertEquals(listOf(inference), registry.inference)
        assertEquals(listOf(handoff), registry.handoff)
        assertEquals(listOf(inference, handoff), registry.all)
        assertTrue(registry.inference.none { it is AiHandoffProvider })
        assertTrue(registry.handoff.all { it is AiHandoffProvider })
    }

    @Test
    fun `router primary constructor routes only typed inference providers`() = runTest {
        val inference = TypedInferenceProvider("typed")
        val handoff = TypedHandoffProvider()
        val registry = AiProviderRegistry.from(listOf(inference, handoff))
        val router = DefaultAiProviderRouter(registry, FakeStorage())

        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("typed")

        val response = router.execute(AiRequest(text = "hello"), AiContext.EMPTY)
        assertEquals("typed", response.providerId)
        assertEquals("typed-model", response.modelId)
    }

    @Test
    fun `handoff provider remains visible but cannot be an inference target`() = runTest {
        val inference = TypedInferenceProvider("typed")
        val handoff = TypedHandoffProvider()
        val registry = AiProviderRegistry.from(listOf(inference, handoff))
        val router = DefaultAiProviderRouter(registry, FakeStorage())

        assertTrue(router.getAllProviders().contains(handoff))
        assertTrue(
            router.getAllProviders()
                .filterIsInstance<AiInferenceProvider>()
                .none { it.id == handoff.id }
        )

        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("handoff")

        try {
            router.execute(AiRequest(text = "hello"), AiContext.EMPTY)
            throw AssertionError("Expected UNSUPPORTED_CAPABILITY")
        } catch (error: AiProviderException) {
            assertEquals(AiErrorType.UNSUPPORTED_CAPABILITY, error.type)
        }
    }
}
