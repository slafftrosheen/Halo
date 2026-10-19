package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test

class AiProviderRouterTest {
    @Test fun `old completion cannot overwrite the newer in flight target diagnostics`() = runTest {
        val oldEntered = CompletableDeferred<Unit>()
        val oldFinish = CompletableDeferred<Unit>()
        val newEntered = CompletableDeferred<Unit>()
        val newFinish = CompletableDeferred<Unit>()
        val old = object : MockProvider("old", "Old") {
            override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
                oldEntered.complete(Unit); oldFinish.await(); return super.generate(request, context)
            }
        }
        val next = object : MockProvider("next", "Next") {
            override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
                newEntered.complete(Unit); newFinish.await(); return super.generate(request, context)
            }
        }
        val router = DefaultAiProviderRouter(listOf(old, next), FakeStorage())
        router.selectTarget(AiModelRef(old.id, "test-model"))
        val first = async { router.execute(AiRequest(text = "old")) }
        oldEntered.await()
        router.selectTarget(AiModelRef(next.id, "test-model"))
        val second = async { router.execute(AiRequest(text = "new")) }
        newEntered.await()
        oldFinish.complete(Unit); first.await()
        assertTrue(router.executionDiagnostics.value.inFlight)
        assertEquals(next.id, router.executionDiagnostics.value.providerId)
        newFinish.complete(Unit); second.await()
        assertFalse(router.executionDiagnostics.value.inFlight)
        assertEquals(next.id, router.executionDiagnostics.value.providerId)
    }

    @Test fun `preflight pin survives settings change without mutating the new selection`() = runTest {
        val first = MockProvider("first", "First")
        val second = MockProvider("second", "Second")
        val router = DefaultAiProviderRouter(listOf(first, second), FakeStorage())
        router.selectTarget(AiModelRef(first.id, "test-model"))
        val target = router.resolveExecutionTarget(AiRequest(text = "preflight"))
        router.selectTarget(AiModelRef(second.id, "test-model"))
        val response = router.execute(AiRequest(text = "actual"),
            AiWorkloadSpec(pinnedTarget = AiModelRef(target.providerId, target.modelId)))
        assertEquals(first.id, response.providerId)
        assertEquals(second.id, router.selectedProviderId.value)
        assertTrue(second.receivedRequests.isEmpty())
    }

    @Test fun `pinned failure cannot silently execute on another provider`() = runTest {
        val first = MockProvider("first", "First", throwsException = true)
        val second = MockProvider("second", "Second")
        val router = DefaultAiProviderRouter(listOf(first, second), FakeStorage())
        router.setRoutingPolicy(AiRoutingPolicy.AUTOMATIC)
        try {
            router.execute(AiRequest(text = "actual"),
                AiWorkloadSpec(pinnedTarget = AiModelRef(first.id, "test-model")))
            fail("Pinned target must fail explicitly")
        } catch (_: AiProviderException) { }
        assertTrue(second.receivedRequests.isEmpty())
    }

    @Test fun `pinned target cannot bypass phone only privacy`() = runTest {
        val remote = MockProvider("remote", "Remote", isLocal = false)
        val router = DefaultAiProviderRouter(listOf(remote), FakeStorage())
        try {
            router.execute(AiRequest(text = "private"), AiWorkloadSpec(allowRemote = false,
                pinnedTarget = AiModelRef(remote.id, "test-model")))
            fail("Remote pin must be rejected before generation")
        } catch (_: AiProviderException) { }
        assertTrue(remote.receivedRequests.isEmpty())
    }

    @Test fun `standalone requests use current app language without changing selected model or prompt`() = runTest {
        val contexts = mutableListOf<AiContext>()
        val provider = object : MockProvider("local", "Local") {
            override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
                contexts += context
                return super.generate(request, context)
            }
        }
        var language = "ru"
        val router = DefaultAiProviderRouter(AiProviderRegistry.from(listOf(provider)), FakeStorage(),
            responseLanguage = { language })
        val request = AiRequest(text = "Explain in English", toolUseMode = ToolUseMode.NONE)
        router.execute(request, AiContext.EMPTY)
        language = "lv"
        router.execute(request, AiContext.EMPTY)
        assertTrue(contexts[0].conversation.first().content.contains("Russian"))
        assertTrue(contexts[1].conversation.first().content.contains("Latvian"))
        assertEquals(listOf("Explain in English", "Explain in English"), provider.receivedRequests.map { it.text })
        assertEquals(1, provider.receivedRequests.map { it.modelId }.distinct().size)
        assertTrue(contexts.all { it.availableTools.isEmpty() })
    }


    @Test fun `streaming workspace requests receive the same language preference`() = runTest {
        val provider = StreamingTextProvider()
        val router = DefaultAiProviderRouter(AiProviderRegistry.from(listOf(provider)),
            FakeStorage().apply { putString("active_ai_provider", provider.id) }, responseLanguage = { "lv" })
        router.execute(AiRequest(text = "Workspace question", toolUseMode = ToolUseMode.NONE), AiContext.EMPTY)
        assertTrue(provider.streamedContext!!.conversation.first().content.contains("Latvian"))
        assertEquals("Workspace question", provider.streamedRequest!!.text)
        assertEquals(1, provider.streamCalls)
    }

    private class StreamingTextProvider(private val end: String = "complete") : MockProvider(
        "remote", "Remote", isLocal = false, supportedCapabilities = setOf(AiCapability.TEXT, AiCapability.STREAMING)) {
        var streamCalls = 0
        var streamedRequest: AiRequest? = null
        var streamedContext: AiContext? = null
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
            streamCalls++
            streamedRequest = request
            streamedContext = context
            assertTrue(context.availableTools.isEmpty())
            emit(AiStreamEvent.Token("answer"))
            when (end) {
                "complete" -> emit(AiStreamEvent.Completed(AiResponse("answer", providerId = id, modelId = request.modelId)))
                "empty" -> emit(AiStreamEvent.Completed(AiResponse("", providerId = id, modelId = request.modelId)))
                "fail" -> emit(AiStreamEvent.Failed(AiProviderException(AiErrorType.NETWORK_ERROR, "disconnected")))
            }
        }
    }

    @Test fun `standalone remote text uses streaming without changing model or output budget`() = runTest {
        val provider = StreamingTextProvider()
        val storage = FakeStorage().apply { putString("active_ai_provider", provider.id) }
        val router = DefaultAiProviderRouter(listOf(provider), storage)
        val result = router.execute(AiRequest(text = "study question", toolUseMode = ToolUseMode.NONE, maxOutputTokens = 600))
        assertEquals("answer", result.text)
        assertEquals(1, provider.streamCalls)
        assertTrue(provider.receivedRequests.isEmpty())
        assertEquals("test-model", provider.streamedRequest?.modelId)
        assertEquals(600, provider.streamedRequest?.maxOutputTokens)
        assertEquals("test-model", router.selectedModelId.value)
    }

    @Test fun `standalone partial stream failure never falls back or returns partial success`() = runTest {
        val provider = StreamingTextProvider("fail")
        val fallback = MockProvider("fallback", "Fallback")
        val storage = FakeStorage().apply { putString("active_ai_provider", provider.id) }
        val router = DefaultAiProviderRouter(listOf(provider, fallback), storage)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        try {
            router.execute(AiRequest(text = "workspace question", toolUseMode = ToolUseMode.NONE))
            fail("Partial response must fail")
        } catch (error: AiProviderException) {
            assertEquals("STREAM_FAILED_AFTER_OUTPUT", error.details.providerCode)
            assertEquals(Recoverability.PERMANENT, error.details.recoverability)
        }
        assertEquals(1, provider.streamCalls)
        assertTrue(fallback.receivedRequests.isEmpty())
    }

    @Test fun `standalone stream requires terminal completed response`() = runTest {
        val provider = StreamingTextProvider("missing")
        val storage = FakeStorage().apply { putString("active_ai_provider", provider.id) }
        val router = DefaultAiProviderRouter(listOf(provider), storage)
        try {
            router.execute(AiRequest(text = "question", toolUseMode = ToolUseMode.NONE))
            fail("Missing completion must fail")
        } catch (error: AiProviderException) {
            assertEquals(AiErrorType.INVALID_RESPONSE, error.type)
        }
    }

    @Test fun `invalid completed response after tokens cannot trigger fallback`() = runTest {
        val provider = StreamingTextProvider("empty")
        val fallback = MockProvider("fallback", "Fallback")
        val storage = FakeStorage().apply { putString("active_ai_provider", provider.id) }
        val router = DefaultAiProviderRouter(listOf(provider, fallback), storage)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        try {
            router.execute(AiRequest(text = "question", toolUseMode = ToolUseMode.NONE))
            fail("Empty completion must fail")
        } catch (error: AiProviderException) {
            assertEquals("STREAM_FAILED_AFTER_OUTPUT", error.details.providerCode)
        }
        assertTrue(fallback.receivedRequests.isEmpty())
    }

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
    ) : AiInferenceProvider {
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
            AiModel(modelId, "Test Model", id, supportedCapabilities, 4096, false, false,
                executionLocation = if (isLocal) AiExecutionLocation.ON_DEVICE else AiExecutionLocation.REMOTE)
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
    fun `changing provider restores its remembered model`() = runTest {
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
    fun `current selection and remembered provider model survive restart`() = runTest {
        val storage = FakeStorage()
        val p1 = MockProvider("p1", "P1", modelId = "model-p1")
        val p2 = MockProvider("p2", "P2", modelId = "model-p2")
        val router = DefaultAiProviderRouter(listOf(p1, p2), storage)
        router.selectProvider("p1")
        router.selectModel("model-p1")
        router.selectProvider("p2")
        router.selectModel("model-p2")
        val restored = DefaultAiProviderRouter(listOf(p1, p2), storage)
        assertEquals("p2", restored.selectedProviderId.value)
        assertEquals("model-p2", restored.selectedModelId.value)
        restored.selectProvider("p1")
        assertEquals("model-p1", restored.selectedModelId.value)
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
        router.selectProvider("handoff")
        router.setRoutingPolicy(AiRoutingPolicy.AUTOMATIC)
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
        router.selectProvider("p2")
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p1", response.providerId)
    }

    @Test
    fun `router LOCAL_ONLY policy skips non-local providers`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "Cloud", isLocal = false)
        val provider2 = MockProvider("p2", "Local", isLocal = true)
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        router.selectProvider("p1")
        router.setRoutingPolicy(AiRoutingPolicy.LOCAL_ONLY)
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
        router.selectProvider("p1")
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p2", response.providerId)
    }

    @Test
    fun `router falls back on transient generation failure`() = runTest {
        val storage = FakeStorage()
        val provider1 = MockProvider("p1", "P1", throwsException = true, failureType = AiErrorType.GENERATION_FAILED)
        val provider2 = MockProvider("p2", "P2")
        val router = DefaultAiProviderRouter(listOf(provider1, provider2), storage)
        router.selectProvider("p1")
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("p2", response.providerId)
    }

    @Test
    fun `designated default provider wins registration order on clean install`() = runTest {
        val storage = FakeStorage()
        val ollama = MockProvider("provider.ollama", "Ollama", isLocal = true)
        val native = MockProvider("provider.litert.local", "LiteRT", isLocal = true)
        val router = DefaultAiProviderRouter(
            providers = listOf(ollama, native),
            storage = storage,
            defaultInferenceProviderId = native.id
        )

        assertEquals(native.id, router.selectedProviderId.value)
        assertEquals(AiRoutingPolicy.AUTOMATIC, router.routingPolicy.value)
    }

    @Test
    fun `persisted provider selection wins designated default`() = runTest {
        val storage = FakeStorage().apply {
            putString("active_ai_provider", "provider.ollama")
            putString("ai_routing_policy", AiRoutingPolicy.EXPLICIT.name)
        }
        val ollama = MockProvider("provider.ollama", "Ollama", isLocal = true)
        val native = MockProvider("provider.llamacpp.native", "llama.cpp", isLocal = true)
        val router = DefaultAiProviderRouter(
            providers = listOf(native, ollama),
            storage = storage,
            defaultInferenceProviderId = native.id
        )

        assertEquals(ollama.id, router.selectedProviderId.value)
        assertEquals(AiRoutingPolicy.EXPLICIT, router.routingPolicy.value)
    }

    @Test
    fun `router defaults to automatic routing`() = runTest {
        val storage = FakeStorage()
        val cloud = MockProvider("cloud", "Cloud", isLocal = false)
        val local = MockProvider("local", "Local", isLocal = true)
        val router = DefaultAiProviderRouter(listOf(cloud, local), storage)

        assertEquals(AiRoutingPolicy.AUTOMATIC, router.routingPolicy.value)
        val response = router.execute(AiRequest(text = "Hello"), AiContext.EMPTY)
        assertEquals("local", response.providerId)
        assertEquals(0, cloud.receivedRequests.size)
    }

    @Test
    fun `persisted automatic policy still prefers the default local provider`() = runTest {
        val storage = FakeStorage()
        storage.putString("ai_routing_policy", AiRoutingPolicy.AUTOMATIC.name)
        val cloud = MockProvider("cloud", "Cloud", isLocal = false)
        val local = MockProvider("local", "Local", isLocal = true)
        val router = DefaultAiProviderRouter(listOf(cloud, local), storage)

        assertEquals(AiRoutingPolicy.AUTOMATIC, router.routingPolicy.value)
        assertEquals("local", router.execute(AiRequest(text = "Hello"), AiContext.EMPTY).providerId)
        assertEquals(0, cloud.receivedRequests.size)
    }

    @Test
    fun `automatic and preferred fallback are available only when explicitly selected`() = runTest {
        val storage = FakeStorage()
        val cloud = MockProvider("cloud", "Cloud", isLocal = false)
        val local = MockProvider("local", "Local", isLocal = true)
        val router = DefaultAiProviderRouter(listOf(cloud, local), storage)

        router.selectProvider("cloud")
        router.setRoutingPolicy(AiRoutingPolicy.AUTOMATIC)
        assertEquals(AiRoutingPolicy.AUTOMATIC, router.routingPolicy.value)
        router.setRoutingPolicy(AiRoutingPolicy.PREFERRED_WITH_FALLBACK)
        assertEquals(AiRoutingPolicy.PREFERRED_WITH_FALLBACK, router.routingPolicy.value)
        assertEquals("cloud", router.execute(AiRequest(text = "Hello"), AiContext.EMPTY).providerId)
        assertEquals(1, cloud.receivedRequests.size)
    }

    @Test
    fun `explicit cloud selection remains explicit and never becomes automatic`() = runTest {
        val storage = FakeStorage()
        val cloud = MockProvider("cloud", "Cloud", isLocal = false)
        val local = MockProvider("local", "Local", isLocal = true)
        val router = DefaultAiProviderRouter(listOf(cloud, local), storage)
        router.setRoutingPolicy(AiRoutingPolicy.EXPLICIT)
        router.selectProvider("cloud")

        assertEquals(AiRoutingPolicy.EXPLICIT, router.routingPolicy.value)
        assertEquals("cloud", router.execute(AiRequest(text = "Hello"), AiContext.EMPTY).providerId)
        assertEquals(1, cloud.receivedRequests.size)
        assertEquals(0, local.receivedRequests.size)
    }
}
