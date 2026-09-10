package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AiProviderRouterDiagnosticsTest {

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

    private class FakeEventBus : EventBus {
        val published = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { published.add(event) }
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
        override fun events(): Flow<HaloEvent> = emptyFlow()
    }

    private class MockProvider(
        override val id: String,
        override val displayName: String,
        private val initialStatus: ProviderStatus = ProviderStatus.READY,
        override val isLocal: Boolean = true,
        val throwsException: Boolean = false,
        val responseText: String = "Test response"
    ) : AiProvider {
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(initialStatus)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)

        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            if (throwsException) throw AiProviderException(AiErrorType.GENERATION_FAILED, "Provider failed generation")
            return AiResponse(text = responseText, providerId = id)
        }

        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels() = listOf(
            AiModel(id = "test-model", displayName = "Model 1", providerId = id, inputCapabilities = setOf(AiCapability.TEXT), contextLength = 4096, supportsStreaming = true, supportsTools = false)
        )
    }

    @Test
    fun `router emits ProviderRequest and ProviderResult on successful execution`() = runTest {
        val storage = FakeStorage()
        val eventBus = FakeEventBus()
        val provider = MockProvider("p1", "P1")
        val router = DefaultAiProviderRouter(listOf(provider), storage, eventBus = eventBus)

        val response = router.execute(AiRequest(text = "Hello"))
        assertEquals("p1", response.providerId)

        assertTrue(eventBus.published.any { it is ProviderRequest && it.providerId == "p1" })
        assertTrue(eventBus.published.any { it is ProviderResult && it.success })
    }

    @Test
    fun `router emits ProviderFailure on provider generation error`() = runTest {
        val storage = FakeStorage()
        val eventBus = FakeEventBus()
        val provider = MockProvider("p1", "P1", throwsException = true)
        val router = DefaultAiProviderRouter(listOf(provider), storage, eventBus = eventBus)

        try {
            router.execute(AiRequest(text = "Hello"))
            fail("Expected AiProviderException")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.GENERATION_FAILED, e.type)
        }

        assertTrue(eventBus.published.any { it is ProviderRequest && it.providerId == "p1" })
        assertTrue(eventBus.published.any { it is ProviderFailure && it.providerId == "p1" })
    }

    @Test
    fun `router diagnostics produces read model snapshot`() = runTest {
        val storage = FakeStorage()
        val p1 = MockProvider("p1", "P1 Local", isLocal = true)
        val p2 = MockProvider("p2", "P2 Remote", isLocal = false, initialStatus = ProviderStatus.UNCONFIGURED)
        val router = DefaultAiProviderRouter(listOf(p1, p2), storage)
        router.selectProvider("p1")

        val diagnostics = router.diagnostics()
        assertEquals(2, diagnostics.size)

        val d1 = diagnostics.find { it.providerId == "p1" }
        assertNotNull(d1)
        assertTrue(d1!!.selected)
        assertTrue(d1.local)
        assertEquals(ProviderStatus.State.READY, d1.state)

        val d2 = diagnostics.find { it.providerId == "p2" }
        assertNotNull(d2)
        assertFalse(d2!!.selected)
        assertFalse(d2.local)
        assertEquals(ProviderStatus.State.UNCONFIGURED, d2.state)
    }

    @Test
    fun `error presenter maps semantic errors to clear messages`() {
        val presenter = DefaultAiErrorPresenter()

        val e1 = AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "no model")
        assertEquals("No AI model selected.", presenter.message(e1))

        val e2 = AiProviderException(AiErrorType.MODEL_LOAD_FAILED, "failed")
        assertEquals("The local AI model could not be loaded.", presenter.message(e2))

        val e3 = AiProviderException(AiErrorType.AUTHENTICATION_FAILED, "bad key")
        assertEquals("Authentication failed. Check your API credentials.", presenter.message(e3))

        val e4 = AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "no vision")
        assertEquals("This AI provider cannot perform that type of request.", presenter.message(e4))
    }
}
