package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.context.DefaultContextAssembler
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.runtime.cognition.tools.ToolRegistry
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiHandoffProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.TtsRequested
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CognitiveSessionHandoffTest {

    private class FakeEventBus : EventBus {
        val published = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { published += event }
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow { }
        override fun events(): Flow<HaloEvent> = flow { }
    }

    private class HandoffProvider : AiProvider, AiHandoffProvider {
        override val id = "android.system.assistant"
        override val displayName = "Android Assistant"
        override val isLocal = false
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)

        var handoffCount = 0
        override fun isAvailable() = true
        override fun assistantPackageName() = "com.example.assistant"
        override fun handoff(prompt: String?) { handoffCount++ }

        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
            throw AssertionError("Handoff provider must never be used by CognitiveSession as inference")
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow { }
        override suspend fun listModels() = listOf(
            AiModel(
                id = "android-assistant/handoff",
                displayName = "System Assistant Handoff",
                providerId = id,
                inputCapabilities = setOf(AiCapability.TEXT),
                contextLength = null,
                supportsStreaming = false,
                supportsTools = false
            )
        )
    }

    @Test
    fun `session does not emit TTS for terminal handoff marker`() = runTest {
        val eventBus = FakeEventBus()
        val store = InMemoryConversationStore()
        val provider = object : AiProvider {
            override val id = "p1"
            override val displayName = "P1"
            override val isLocal = true
            override val status = MutableStateFlow(ProviderStatus.READY)
            override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
            override suspend fun generate(request: AiRequest, context: AiContext) =
                AiResponse("HANDOFF_STARTED", providerId = id, modelId = request.modelId)
            override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow { }
            override suspend fun listModels() = listOf(
                AiModel("m1", "Model", id, setOf(AiCapability.TEXT), 4096, false, false)
            )
        }
        val router = object : AiProviderRouter {
            override val selectedProviderId = MutableStateFlow<String?>("p1")
            override val selectedModelId = MutableStateFlow<String?>("m1")
            override fun selectProvider(id: String) { }
            override fun selectModel(modelId: String?) { }
            override fun getAllProviders() = listOf(provider)
            override suspend fun getEligibleProvider(request: AiRequest) = provider
            override suspend fun resolveExecutionTarget(request: AiRequest) = AiExecutionTarget(
                provider,
                provider.listModels().first(),
                setOf(AiCapability.TEXT)
            )
            override suspend fun execute(request: AiRequest, context: AiContext) = provider.generate(request, context)
            override fun start() { }
            override fun stop() { }
        }
        val session = CognitiveSession(
            eventBus,
            router,
            store,
            DefaultContextAssembler(store, DefaultBlackboard()),
            ToolRegistry(emptyList()),
            null,
            this
        )

        session.submit(AiRequest(text = "open assistant"))
        advanceUntilIdle()

        assertFalse(eventBus.published.any { it is TtsRequested })
        assertEquals(0, store.turns.value.size)
    }

    @Test
    fun `handoff provider remains a distinct capability`() {
        val provider = HandoffProvider()
        assertTrue(provider is AiHandoffProvider)
        assertEquals("com.example.assistant", provider.assistantPackageName())
    }

    @Test
    fun `handoff provider is not an inference target`() = runTest {
        val provider = HandoffProvider()
        val router = object : AiProviderRouter {
            override val selectedProviderId = MutableStateFlow<String?>(provider.id)
            override val selectedModelId = MutableStateFlow<String?>("android-assistant/handoff")
            override fun selectProvider(id: String) { }
            override fun selectModel(modelId: String?) { }
            override fun getAllProviders() = listOf(provider)
            override suspend fun getEligibleProvider(request: AiRequest): AiProvider =
                throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "handoff only")
            override suspend fun resolveExecutionTarget(request: AiRequest): AiExecutionTarget =
                throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "handoff only")
            override suspend fun execute(request: AiRequest, context: AiContext): AiResponse =
                throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "handoff only")
            override fun start() { }
            override fun stop() { }
        }

        try {
            router.resolveExecutionTarget(AiRequest(text = "hello"))
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.UNSUPPORTED_CAPABILITY, e.type)
        }
    }
}
