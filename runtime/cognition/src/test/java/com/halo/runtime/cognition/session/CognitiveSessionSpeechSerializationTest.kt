package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.context.DefaultContextAssembler
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiProviderRouter
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.sdk.runtime.SpeechRecognized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CognitiveSessionSpeechSerializationTest {

    @Test
    fun `speech events do not start a second transaction while the first is active`() = runTest {
        val eventBus = SharedEventBus()
        val provider = BlockingStreamingProvider()
        val router = FixedRouter(provider)
        val store = InMemoryConversationStore()
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = DefaultContextAssembler(store, DefaultBlackboard()),
            scope = this
        )

        runCurrent()
        eventBus.publish(SpeechRecognized(text = "first"))
        runCurrent()
        assertEquals(1, provider.streamCalls)

        eventBus.publish(SpeechRecognized(text = "second"))
        runCurrent()

        assertEquals("Only the first speech event may own the active transaction", 1, provider.streamCalls)
        assertEquals(1, eventBus.published.filterIsInstance<CognitiveRequestStarted>().size)

        provider.release.complete(Unit)
        advanceUntilIdle()

        assertTrue(session.state.value.toString().contains("Completed"))
    }

    private class SharedEventBus : EventBus {
        private val events = MutableSharedFlow<HaloEvent>(extraBufferCapacity = 32)
        val published = mutableListOf<HaloEvent>()

        override suspend fun publish(event: HaloEvent) {
            published.add(event)
            events.emit(event)
        }

        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow {
            events.collect { event ->
                if (type.isInstance(event)) emit(type.cast(event))
            }
        }

        override fun events(): Flow<HaloEvent> = events
    }

    private class BlockingStreamingProvider : AiInferenceProvider {
        override val id = "test.provider"
        override val displayName = "Test Provider"
        override val isLocal = true
        override val status = MutableStateFlow(ProviderStatus.READY)
        val release = CompletableDeferred<Unit>()
        var streamCalls = 0
            private set

        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.STREAMING)

        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
            throw AssertionError("The serialization test must use the streaming path")

        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
            streamCalls++
            emit(AiStreamEvent.Started)
            release.await()
            emit(AiStreamEvent.Completed(AiResponse(text = "done", providerId = id, modelId = "test-model")))
        }

        override suspend fun listModels() = emptyList<AiModel>()
    }

    private class FixedRouter(private val provider: AiInferenceProvider) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(provider.id)
        override val selectedModelId = MutableStateFlow<String?>("test-model")
        override fun selectProvider(id: String) = Unit
        override fun selectModel(modelId: String?) = Unit
        override fun getAllProviders() = listOf(provider)
        override suspend fun getEligibleProvider(request: AiRequest) = provider

        override suspend fun resolveExecutionTarget(request: AiRequest): AiExecutionTarget {
            val model = AiModel(
                id = "test-model",
                displayName = "Test Model",
                providerId = provider.id,
                inputCapabilities = setOf(AiCapability.TEXT),
                contextLength = 4096,
                supportsStreaming = true,
                supportsTools = false
            )
            return AiExecutionTarget(
                provider = provider,
                model = model,
                providerCapabilities = provider.getCapabilities()
            )
        }

        override suspend fun execute(request: AiRequest, context: AiContext) = provider.generate(request, context)
        override fun start() = Unit
        override fun stop() = Unit
    }
}
