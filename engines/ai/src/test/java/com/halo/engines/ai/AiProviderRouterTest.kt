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

class AiProviderRouterTest {

    private class FakeStorage : Storage {
        private val data = mutableMapOf<String, Any?>("active_ai_model" to "test-model")
        override fun getString(key: String, defaultValue: String?) = data[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { data[key] = value }
        override fun getInt(key: String, defaultValue: Int) = data[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { data[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = data[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { data[key] = value }
        override fun remove(key: String) { data.remove(key) }
        override fun clear() { data.clear() }
    }

    private open class MockProvider(
        override val id: String,
        override val displayName: String,
        private val initialStatus: ProviderStatus = ProviderStatus.READY,
        override val isLocal: Boolean = true,
        val throwsException: Boolean = false,
        val supportedCapabilities: Set<AiCapability> = setOf(AiCapability.TEXT),
        val modelId: String = "test-model",
        val failureType: AiErrorType = AiErrorType.PROVIDER_UNAVAILABLE
    ) : AiProvider {
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(initialStatus)
        val receivedRequests = mutableListOf<AiRequest>()

        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = supportedCapabilities
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            receivedRequests += request
            if (throwsException) throw AiProviderException(failureType, "Simulated failure")
            return AiResponse("Response from $id", providerId = id, modelId = request.modelId)
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels() = listOf(
            AiModel(modelId, "Test Model", id, supportedCapabilities, 4096, false, false)
        )
    }

    private class HandoffMockProvider : MockProvider(
        id = "handoff",
        displayName = "External Assistant"
    ), AiHandoffProvider {
        var handoffCount = 0
        override fun isAvailable() = true
        override fun assistantPackageName() = "com.example.assistant"
        override fun handoff(prompt: String?) { handoffCount++ }
    }

    @Test
    fun `router selects preferred provider from storage`() = runTest {
        val storage = FakeStorage()
        storage.putString("active_ai_provider", "p2")
        val provider1 = MockProvider("p1", "P1")
        val provider2 = MockProvider("p2", "P2")
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        assertEquals("p2", router.selectedProviderId.value)
        val response = router.execute(AiRequest(text = "Hello"))
        assertEquals("p2", response.providerId)
    }

    @Test
    fun `changing provider restores its own model instead of clearing all selections`() = runTest {
        val storage = FakeStorage()
        val p1 = MockProvider("p1", "P1", modelId = "model-p1")
        val p2 = MockProvider("p2", "P2", modelId = "model-p2")
        val router = DefaultAiProviderRouter(listOf(p1, p2), storage)
        router.selectProvider("p1")
        router.selectModel("model-p1")
        router.selectProvider("p2")
        router.selectModel("model-p2")
        router.selectProvider("p1")
        assertEquals("model-p1", router.selectedModelId.value)
        assertEquals("model-p1", storage.getString("active_ai_model"))
    }

    @Test
    fun `provider-scoped model selection is persisted independently`() = runTest {
        val storage = FakeStorage()
        val p1 = MockProvider("p1", "P1", modelId = "model-p1")
        val p2 = MockProvider("p2", "P2", modelId = "model-p2")
        val router = DefaultAiProviderRouter(listOf(p1, p2), storage)
        router.selectProvider("p1")
        router.selectModel("model-p1")
        router.selectProvider("p2")
        router.selectModel("model-p2")
        val restored = DefaultAiProviderRouter(listOf(p1, p2), storage)
        restored.selectProvider("p1")
        assertEquals("model-p1", restored.selectedModelId.value)
        restored.selectProvider("p2")
        assertEquals("model-p2", restored.selectedModelId.value)
    }

    @Test
    fun `handoff provider is excluded from explicit cognitive inference`() = runTest {
        val storage = FakeStorage()
        val handoff = HandoffMockProvider()
        val inference = MockProvider("p1", "P1")
        val router = DefaultAiProviderRouter(listOf(handoff, inference), storage)
        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("handoff")
        try {
            router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
            fail("Expected UNSUPPORTED_CAPABILITY")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.UNSUPPORTED_CAPABILITY, e.type)
        }
        assertEquals(0, handoff.handoffCount)
    }

    @Test
    fun `automatic routing skips handoff provider`() = runTest {
        val storage = FakeStorage()
        val handoff = HandoffMockProvider()
        val inference = MockProvider("p1", "P1")
        val router = DefaultAiProviderRouter(listOf(handoff, inference), storage)
        router.setRoutingPolicy(AiRoutingPolicy.AUTOMATIC)
        router.selectProvider("handoff")
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p1", response.providerId)
        assertEquals(0, handoff.handoffCount)
    }

    @Test
    fun `standby provider is not executable`() = runTest {
        val storage = FakeStorage()
        val provider = MockProvider("p1", "P1", initialStatus = ProviderStatus(ProviderStatus.State.STANDBY))
        val router = DefaultAiProviderRouter(listOf(provider), storage)
        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("p1")
        try {
            router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
            fail("Expected AiProviderException")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.PROVIDER_UNAVAILABLE, e.type)
        }
    }

    @Test
    fun `loading provider is not executable`() = runTest {
        val storage = FakeStorage()
        val provider = MockProvider("p1", "P1", initialStatus = ProviderStatus(ProviderStatus.State.LOADING))
        val router = DefaultAiProviderRouter(listOf(provider), storage)
        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("p1")
        try {
            router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
            fail("Expected AiProviderException")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.PROVIDER_UNAVAILABLE, e.type)
        }
    }

    @Test
    fun `router falls back if preferred provider is unavailable`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1")
        val provider2 = MockProvider("p2", "P2", initialStatus = ProviderStatus.UNAVAILABLE)
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        router.selectProvider("p2")
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p1", response.providerId)
    }

    @Test
    fun `router LOCAL_ONLY policy skips non-local providers`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "Cloud", isLocal = false)
        val provider2 = MockProvider("p2", "Local", isLocal = true)
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        router.setRoutingPolicy(AiRoutingPolicy.LOCAL_ONLY)
        router.selectProvider("p1")
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p2", response.providerId)
    }

    @Test
    fun `router EXPLICIT policy fails instead of fallback`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1")
        val provider2 = MockProvider("p2", "P2", initialStatus = ProviderStatus.UNAVAILABLE)
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("p2")
        try {
            router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
            fail("Expected AiProviderException")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.PROVIDER_UNAVAILABLE, e.type)
        }
    }

    @Test
    fun `router falls back dynamically on execution failure`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1", throwsException = true)
        val provider2 = MockProvider("p2", "P2")
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        router.selectProvider("p1")
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p2", response.providerId)
    }

    @Test
    fun `router does not fallback on invalid request`() = runTest {
        val storage = FakeStorage()
        val primary = MockProvider("p1", "P1", throwsException = true, failureType = AiErrorType.INVALID_REQUEST)
        val fallback = MockProvider("p2", "P2")
        val router = DefaultAiProviderRouter(listOf(primary, fallback), storage)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        router.selectProvider("p1")
        try {
            router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
            fail("Expected INVALID_REQUEST")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.INVALID_REQUEST, e.type)
        }
        assertTrue(fallback.receivedRequests.isEmpty())
    }

    @Test
    fun `router does not fallback on unsupported capability`() = runTest {
        val storage = FakeStorage()
        val primary = MockProvider("p1", "P1", throwsException = true, failureType = AiErrorType.UNSUPPORTED_CAPABILITY)
        val fallback = MockProvider("p2", "P2")
        val router = DefaultAiProviderRouter(listOf(primary, fallback), storage)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        router.selectProvider("p1")
        try {
            router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
            fail("Expected UNSUPPORTED_CAPABILITY")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.UNSUPPORTED_CAPABILITY, e.type)
        }
        assertTrue(fallback.receivedRequests.isEmpty())
    }

    @Test
    fun `fallback resolves its own advertised model`() = runTest {
        val storage = FakeStorage()
        val primary = MockProvider("p1", "P1", throwsException = true, modelId = "model-a")
        val fallback = MockProvider("p2", "P2", modelId = "model-b")
        val router = DefaultAiProviderRouter(listOf(primary, fallback), storage)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p2", response.providerId)
        assertEquals("model-b", fallback.receivedRequests.single().modelId)
    }

    @Test
    fun `automatic routing skips provider with incompatible models`() = runTest {
        val storage = FakeStorage()
        val textProvider = MockProvider("p1", "Text Provider", supportedCapabilities = setOf(AiCapability.TEXT), modelId = "text-model")
        val usableProvider = MockProvider("p2", "Image Provider", supportedCapabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE), modelId = "vision-model")
        val router = DefaultAiProviderRouter(listOf(textProvider, usableProvider), storage)
        router.setRoutingPolicy(AiRoutingPolicy.AUTOMATIC)
        val response = router.execute(AiRequest(text = "What is this?", image = byteArrayOf(1, 2, 3)), AiContext.EMPTY)
        assertEquals("p2", response.providerId)
    }

    @Test
    fun `router skips providers without required capability`() = runTest {
        val storage = FakeStorage()
        val pText = MockProvider("p1", "Text Only", supportedCapabilities = setOf(AiCapability.TEXT))
        val pImage = MockProvider("p2", "Image Only", supportedCapabilities = setOf(AiCapability.IMAGE))
        val router = DefaultAiProviderRouter(listOf(pText, pImage), storage)
        router.setRoutingPolicy(AiRoutingPolicy.AUTOMATIC)
        val resp1 = router.execute(AiRequest(text = "hello"))
        assertEquals("p1", resp1.providerId)
        val resp2 = router.execute(AiRequest(image = byteArrayOf(1, 2, 3)))
        assertEquals("p2", resp2.providerId)
    }

    @Test
    fun `getEligibleProvider throws if no provider supports request`() = runTest {
        val storage = FakeStorage()
        val pText = MockProvider("p1", "Text Only")
        val router = DefaultAiProviderRouter(listOf(pText), storage)
        try {
            router.getEligibleProvider(AiRequest(image = byteArrayOf(1)))
            fail("Expected AiProviderException")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.PROVIDER_UNAVAILABLE, e.type)
        }
    }
}
