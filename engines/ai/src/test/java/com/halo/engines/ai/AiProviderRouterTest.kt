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

    private class MockProvider(
        override val id: String,
        override val displayName: String,
        private val initialStatus: ProviderStatus = ProviderStatus.READY,
        override val isLocal: Boolean = true,
        val throwsException: Boolean = false
    ) : AiProvider {
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(initialStatus)
        
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            if (throwsException) throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "Simulated failure")
            return AiResponse("Response from $id", providerId = id)
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels() = emptyList<AiModel>()
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
        assertEquals("p2", storage.getString("active_ai_provider"))
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
        router.selectProvider("p1") // Selected but not local!
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
        
        // Even though p1 is READY, it throws on execution, router should catch and fallback to p2
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p2", response.providerId)
    }
}
