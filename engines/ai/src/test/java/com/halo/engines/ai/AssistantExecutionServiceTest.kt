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

class AssistantExecutionServiceTest {

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
        override val id: String = "p1",
        override val displayName: String = "P1",
        override val isLocal: Boolean = true,
        val responseText: String = "Hello from AI"
    ) : AiProvider {
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            return AiResponse(text = responseText, providerId = id)
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels() = emptyList<AiModel>()
    }

    @Test
    fun `service executes request and returns valid response`() = runTest {
        val storage = FakeStorage()
        val provider = MockProvider(responseText = "Valid AI answer")
        val router = DefaultAiProviderRouter(listOf(provider), storage)
        val service = AssistantExecutionService(router)

        val response = service.execute(AiRequest(text = "Hello"))
        assertEquals("Valid AI answer", response.text)
        assertEquals("p1", response.providerId)
    }

    @Test
    fun `service throws exception on empty request text`() = runTest {
        val storage = FakeStorage()
        val provider = MockProvider()
        val router = DefaultAiProviderRouter(listOf(provider), storage)
        val service = AssistantExecutionService(router)

        try {
            service.execute(AiRequest(text = "   "))
            fail("Expected IllegalArgumentException for blank text")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("non-empty text") == true)
        }
    }
}
