package com.halo.engines.ai

import com.halo.sdk.provider.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AiProviderRouterTest {

    private class MockProvider(
        override val id: String,
        override val name: String,
        override val capabilities: Set<AiCapability>,
        private val available: Boolean = true
    ) : AiProvider {
        override fun isAvailable(): Boolean = available
        override suspend fun execute(request: AiRequest): AiResponse = AiResponse("Response from $id")
    }

    @Test
    fun `router selects available provider with matching capabilities`() = runTest {
        val provider1 = MockProvider("p1", "P1", setOf(AiCapability.TEXT))
        val provider2 = MockProvider("p2", "P2", setOf(AiCapability.IMAGE))
        val router = AiProviderRouter(listOf(provider1, provider2))

        val response = router.execute(AiRequest(text = "Hello"))
        assertEquals("Response from p1", response.text)
    }

    @Test
    fun `router selects preferred provider if available and compatible`() = runTest {
        val provider1 = MockProvider("p1", "P1", setOf(AiCapability.TEXT))
        val provider2 = MockProvider("p2", "P2", setOf(AiCapability.TEXT))
        val router = AiProviderRouter(listOf(provider1, provider2))
        
        router.selectProvider("p2")
        val response = router.execute(AiRequest(text = "Hello"))
        assertEquals("Response from p2", response.text)
    }

    @Test
    fun `router falls back if preferred provider is unavailable`() = runTest {
        val provider1 = MockProvider("p1", "P1", setOf(AiCapability.TEXT))
        val provider2 = MockProvider("p2", "P2", setOf(AiCapability.TEXT), available = false)
        val router = AiProviderRouter(listOf(provider1, provider2))
        
        router.selectProvider("p2")
        val response = router.execute(AiRequest(text = "Hello"))
        assertEquals("Response from p1", response.text)
    }

    @Test(expected = IllegalStateException::class)
    fun `router throws if no provider supports requested capabilities`() = runTest {
        val provider1 = MockProvider("p1", "P1", setOf(AiCapability.TEXT))
        val router = AiProviderRouter(listOf(provider1))

        router.execute(AiRequest(image = byteArrayOf(1, 2, 3)))
    }
}
