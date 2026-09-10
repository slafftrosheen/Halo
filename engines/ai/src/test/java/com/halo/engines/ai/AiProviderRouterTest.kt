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
        val modelId: String = "test-model"
    ) : AiProvider {
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(initialStatus)
        val receivedRequests = mutableListOf<AiRequest>()
        
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = supportedCapabilities
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            receivedRequests += request
            if (throwsException) throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "Simulated failure")
            return AiResponse("Response from $id", providerId = id)
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels() = listOf(
            AiModel(modelId, "Test Model", id, supportedCapabilities, 4096, false, false)
        )
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
    fun `router persists selection to storage`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1")
        val provider2 = MockProvider("p2", "P2")
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        
        router.selectProvider("p2")
        router.selectModel("test-model")
        assertEquals("p2", storage.getString("active_ai_provider"))
    }

    @Test
    fun `standby provider is not executable`() = runTest {
        val storage = FakeStorage()
        val provider = MockProvider("p1", "P1", initialStatus = ProviderStatus(ProviderStatus.State.STANDBY))
        val router = DefaultAiProviderRouter(listOf(provider), storage)
        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("p1")
        router.selectModel("test-model")

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
        router.selectModel("test-model")

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
        router.selectModel("test-model")
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
        router.selectProvider("p1") // Selected but not local!
        router.selectModel("test-model")
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p2", response.providerId) // Should fallback to local
    }

    @Test
    fun `router EXPLICIT policy fails instead of fallback`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1")
        val provider2 = MockProvider("p2", "P2", initialStatus = ProviderStatus.UNAVAILABLE)
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        
        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("p2")
        router.selectModel("test-model")
        
        var exceptionThrown = false
        try {
            router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        } catch (e: AiProviderException) {
            exceptionThrown = true
            assertEquals(AiErrorType.PROVIDER_UNAVAILABLE, e.type)
        }
        assertTrue(exceptionThrown)
    }

    @Test
    fun `router falls back dynamically on execution failure`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1", throwsException = true)
        val provider2 = MockProvider("p2", "P2")
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        router.selectProvider("p1")
        router.selectModel("test-model")
        
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p2", response.providerId)
    }

    @Test
    fun `router rejects model not advertised by selected provider`() = runTest {
        val storage = FakeStorage()
        storage.putString("active_ai_model", "other-provider-model")
        val provider = MockProvider("p1", "P1", modelId = "p1-model")
        val router = DefaultAiProviderRouter(listOf(provider), storage)

        try {
            router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
            fail("Expected MODEL_NOT_FOUND")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.MODEL_NOT_FOUND, e.type)
        }
        assertTrue(provider.receivedRequests.isEmpty())
    }

    @Test
    fun `fallback resolves its own advertised model`() = runTest {
        val storage = FakeStorage()
        storage.putString("active_ai_model", "model-a")
        val primary = MockProvider("p1", "P1", throwsException = true, modelId = "model-a")
        val fallback = MockProvider("p2", "P2", modelId = "model-b")
        val router = DefaultAiProviderRouter(listOf(primary, fallback), storage)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)

        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)

        assertEquals("p2", response.providerId)
        assertEquals("model-b", fallback.receivedRequests.single().modelId)
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
