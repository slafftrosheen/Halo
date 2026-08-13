package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
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
        private val status: ProviderStatus = ProviderStatus.READY
    ) : AiProvider {
        override suspend fun getStatus() = status
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun generate(request: AiRequest, context: AiContext) = AiResponse("Response from $id", providerId = id)
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels() = emptyList<AiModel>()
    }

    @Test
    fun `router selects preferred provider from storage`() = runTest {
        val storage = FakeStorage()
        storage.putString("active_ai_provider", "p2")
        
        val provider1 = MockProvider("p1", "P1")
        val provider2 = MockProvider("p2", "P2")
        val router = AiProviderRouter(listOf(provider1, provider2), storage)

        assertEquals("p2", router.selectedProviderId.value)
        
        val response = router.execute(AiRequest(text = "Hello"))
        assertEquals("p2", response.providerId)
    }

    @Test
    fun `router persists selection to storage`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1")
        val provider2 = MockProvider("p2", "P2")
        val router = AiProviderRouter(listOf(provider1, provider2), storage)
        
        router.selectProvider("p2")
        assertEquals("p2", storage.getString("active_ai_provider"))
    }

    @Test
    fun `router falls back if preferred provider is unavailable`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1")
        val provider2 = MockProvider("p2", "P2", status = ProviderStatus.UNAVAILABLE)
        val router = AiProviderRouter(listOf(provider1, provider2), storage)
        
        router.selectProvider("p2")
        val response = router.execute(AiRequest(text = "Hello"))
        assertEquals("p1", response.providerId)
    }
}
