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
import com.halo.sdk.provider.AiWorkloadSpec
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.CognitiveRequestStarted
import com.halo.sdk.runtime.CognitiveCompleted
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.InteractionTurnChanged
import com.halo.sdk.runtime.InteractionTurnState
import com.halo.sdk.runtime.Source
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import com.halo.cognition.language.ConversationIntent
import com.halo.cognition.language.CurrentTimeIntent
import com.halo.runtime.cognition.facts.RuntimeFactResolver
import com.halo.runtime.cognition.facts.RuntimeFactResult
import com.halo.runtime.cognition.blackboard.Confidence
import com.halo.runtime.cognition.blackboard.Interpretation
import com.halo.runtime.cognition.blackboard.InterpretationKnowledge
import com.halo.runtime.cognition.blackboard.Knowledge
import com.halo.runtime.cognition.blackboard.KnowledgeId
import com.halo.runtime.cognition.blackboard.KnowledgeSource
import kotlinx.datetime.Clock
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
    fun `interpreted keyboard conversation sends one tool free request`() = runTest {
        val events = SharedEventBus()
        val provider = BlockingStreamingProvider()
        val blackboard = DefaultBlackboard()
        val store = InMemoryConversationStore()
        val session = CognitiveSession(eventBus = events, blackboard = blackboard, providerRouter = FixedRouter(provider),
            conversationStore = store, contextAssembler = DefaultContextAssembler(store, blackboard), scope = this)
        runCurrent()
        blackboard.publish(Knowledge(id = KnowledgeId("typed"), payload = InterpretationKnowledge(
            Interpretation(transcript = "explain gravity", intent = ConversationIntent,
                metadata = mapOf("inputModality" to "TEXT", "interactionId" to "typed-turn"))),
            confidence = Confidence(1f, 1f, 1f), source = KnowledgeSource.USER, createdAt = Clock.System.now()))
        runCurrent()
        assertEquals(1, provider.streamCalls)
        assertEquals(com.halo.sdk.provider.ToolUseMode.NONE, provider.lastRequest?.toolUseMode)
        assertTrue(provider.lastContext?.availableTools.orEmpty().isEmpty())
        assertEquals("typed-turn", events.published.filterIsInstance<CognitiveRequestStarted>().single().interactionId)
        provider.release.complete(Unit)
        advanceUntilIdle()
        assertTrue(session.state.value.toString().contains("Completed"))
    }

    @Test
    fun `speech events do not start a second transaction while the first is active`() = runTest {
        val eventBus = SharedEventBus()
        val provider = BlockingStreamingProvider()
        val router = FixedRouter(provider)
        val store = InMemoryConversationStore()
        val blackboard = DefaultBlackboard()
        val session = CognitiveSession(
            eventBus = eventBus,
            blackboard = blackboard,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = DefaultContextAssembler(store, blackboard),
            scope = this
        )

        runCurrent()
        blackboard.publish(
            Knowledge(
                id = KnowledgeId("k1"),
                payload = InterpretationKnowledge(
                    Interpretation(transcript = "first", intent = ConversationIntent)
                ),
                confidence = Confidence(1.0f, 1.0f, 1.0f),
                source = KnowledgeSource.USER,
                createdAt = Clock.System.now()
            )
        )
        runCurrent()
        assertEquals(1, provider.streamCalls)

        blackboard.publish(
            Knowledge(
                id = KnowledgeId("k2"),
                payload = InterpretationKnowledge(
                    Interpretation(transcript = "second", intent = ConversationIntent)
                ),
                confidence = Confidence(1.0f, 1.0f, 1.0f),
                source = KnowledgeSource.USER,
                createdAt = Clock.System.now()
            )
        )
        runCurrent()

        assertEquals("Only the first speech event may own the active transaction", 1, provider.streamCalls)
        assertEquals(1, eventBus.published.filterIsInstance<CognitiveRequestStarted>().size)

        provider.release.complete(Unit)
        advanceUntilIdle()

        assertTrue(session.state.value.toString().contains("Completed"))
    }

    @Test
    fun `local fact delivers one committed visible and spoken answer without invoking a model`() = runTest {
        val events = SharedEventBus()
        val blackboard = DefaultBlackboard()
        val store = InMemoryConversationStore()
        val provider = BlockingStreamingProvider()
        val session = CognitiveSession(eventBus = events, blackboard = blackboard,
            providerRouter = FixedRouter(provider), conversationStore = store,
            contextAssembler = DefaultContextAssembler(store, blackboard), scope = this,
            factResolver = object : RuntimeFactResolver {
                override suspend fun resolve(intent: com.halo.cognition.language.SystemFactIntent) =
                    RuntimeFactResult("The local time is 14:37.")
            })
        runCurrent()
        blackboard.publish(Knowledge(id = KnowledgeId("local-time"), payload = InterpretationKnowledge(
            Interpretation(transcript = "what time is it", intent = CurrentTimeIntent,
                source = Source.MICROPHONE, metadata = mapOf("interactionId" to "clock-turn"))),
            confidence = Confidence(1f, 1f, 1f), source = KnowledgeSource.USER, createdAt = Clock.System.now()))
        advanceUntilIdle()

        assertEquals(0, provider.streamCalls)
        assertEquals(1, store.turns.value.size)
        assertEquals("The local time is 14:37.", store.turns.value.single().assistantText)
        assertEquals("clock-turn", events.published.filterIsInstance<CognitiveCompleted>().single().interactionId)
        assertEquals("clock-turn", events.published.filterIsInstance<AssistantResponsePublished>().single().interactionId)
        assertEquals("clock-turn", events.published.filterIsInstance<TtsRequested>().single().interactionId)
        assertEquals(1, events.published.filterIsInstance<InteractionTurnChanged>()
            .count { it.state is InteractionTurnState.Completed })
        assertTrue(session.state.value.toString().contains("Completed"))
    }

    @Test
    fun `cancelled local fact cannot commit or speak after its resolver is delayed`() = runTest {
        val events = SharedEventBus()
        val blackboard = DefaultBlackboard()
        val store = InMemoryConversationStore()
        val provider = BlockingStreamingProvider()
        val release = CompletableDeferred<Unit>()
        val session = CognitiveSession(eventBus = events, blackboard = blackboard,
            providerRouter = FixedRouter(provider), conversationStore = store,
            contextAssembler = DefaultContextAssembler(store, blackboard), scope = this,
            factResolver = object : RuntimeFactResolver {
                override suspend fun resolve(intent: com.halo.cognition.language.SystemFactIntent): RuntimeFactResult {
                    release.await()
                    return RuntimeFactResult("Late answer")
                }
            })
        runCurrent()
        blackboard.publish(Knowledge(id = KnowledgeId("delayed-time"), payload = InterpretationKnowledge(
            Interpretation(transcript = "time please", intent = CurrentTimeIntent,
                source = Source.MICROPHONE, metadata = mapOf("interactionId" to "cancel-clock"))),
            confidence = Confidence(1f, 1f, 1f), source = KnowledgeSource.USER, createdAt = Clock.System.now()))
        runCurrent()
        assertEquals(1, events.published.filterIsInstance<CognitiveRequestStarted>().size)
        session.cancelInteraction("cancel-clock")
        runCurrent()
        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(store.turns.value.isEmpty())
        assertTrue(events.published.none { it is TtsRequested || it is AssistantResponsePublished || it is CognitiveCompleted })
        assertTrue(events.published.filterIsInstance<InteractionTurnChanged>()
            .any { it.state is InteractionTurnState.Cancelled })
        assertEquals(0, provider.streamCalls)
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
        var lastRequest: AiRequest? = null
        var lastContext: AiContext? = null

        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.STREAMING)

        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
            throw AssertionError("The serialization test must use the streaming path")

        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
            streamCalls++
            lastRequest = request
            lastContext = context
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

        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
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
