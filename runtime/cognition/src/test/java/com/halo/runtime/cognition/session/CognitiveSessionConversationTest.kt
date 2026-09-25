package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CognitiveSessionConversationTest {

    @Test
    fun `request fails before provider execution when no model is selected`() = runTest {
        val eventBus = FakeEventBus()
        val store = InMemoryConversationStore()
        val provider = RecordingProvider()
        val router = FakeRouter(provider)
        router.selectedModelId.value = null
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, DefaultBlackboard(), null),
            scope = this
        )

        session.submit(AiRequest(text = "hello"))
        advanceUntilIdle()

        assertEquals(0, provider.capturedRequests.size)
        assertTrue(session.state.value is CognitiveSessionState.Failed)
    }

    private class FakeEventBus : EventBus {
        val published = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { published.add(event) }
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow {}
        override fun events(): Flow<HaloEvent> = flow {}
    }

    private class RecordingProvider(
        override val id: String = "fake.provider",
        override val displayName: String = "Fake Provider",
        override val isLocal: Boolean = true,
        val shouldFail: Boolean = false,
        val responseText: String = "Paris."
    ) : AiInferenceProvider {
        val capturedContexts = mutableListOf<AiContext>()
        val capturedRequests = mutableListOf<AiRequest>()

        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.STREAMING)

        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            capturedRequests.add(request)
            capturedContexts.add(context)
            if (shouldFail) throw AiProviderException(AiErrorType.GENERATION_FAILED, "Simulated error")
            return AiResponse(text = responseText, providerId = id)
        }

        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
            capturedRequests.add(request)
            capturedContexts.add(context)
            if (shouldFail) {
                emit(AiStreamEvent.Started)
                emit(AiStreamEvent.Failed(AiProviderException(AiErrorType.GENERATION_FAILED, "Stream error")))
                return@flow
            }
            emit(AiStreamEvent.Started)
            emit(AiStreamEvent.Token(responseText))
            emit(AiStreamEvent.Completed(AiResponse(text = responseText, providerId = id)))
        }

        override suspend fun listModels() = emptyList<AiModel>()
    }

    private class FakeRouter(private val provider: AiInferenceProvider) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(provider.id)
        override val selectedModelId = MutableStateFlow<String?>("test-model")
        override fun selectProvider(id: String) {}
        override fun selectModel(modelId: String?) {}
        override fun getAllProviders() = listOf(provider)
        override suspend fun getEligibleProvider(request: AiRequest) = provider
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
            val modelId = request.modelId ?: selectedModelId.value
                ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "No model selected")
            val model = AiModel(
                id = modelId,
                displayName = "Test Model",
                providerId = provider.id,
                inputCapabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE),
                contextLength = 4096,
                supportsStreaming = true,
                supportsTools = true
            )
            return AiExecutionTarget(
                provider = provider,
                model = model,
                providerCapabilities = provider.getCapabilities()
            )
        }
        override suspend fun execute(request: AiRequest, context: AiContext) = provider.generate(request, context)
        override fun start() {}
        override fun stop() {}
    }

    @Test
    fun `first request sends empty conversation history and appends turn on completion`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val provider = RecordingProvider(responseText = "Paris.")
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            scope = this
        )

        session.submit(AiRequest(text = "What is the capital of France?"))
        advanceUntilIdle()

        assertEquals(1, store.turns.value.size)
        val turn = store.turns.value[0]
        assertEquals("What is the capital of France?", turn.userText)
        assertEquals("Paris.", turn.assistantText)

        val turnCompletedEvent = eventBus.published.filterIsInstance<ConversationTurnCompleted>().firstOrNull()
        assertNotNull(turnCompletedEvent)
        assertEquals(turn.id, turnCompletedEvent?.turnId)
    }

    @Test
    fun `second request receives previous turns in conversation context`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val provider = RecordingProvider(responseText = "Paris.")
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            scope = this
        )

        // Turn 1
        session.submit(AiRequest(text = "What is the capital of France?"))
        advanceUntilIdle()
        assertEquals(1, store.turns.value.size)

        // Turn 2
        session.submit(AiRequest(text = "What country is that in?"))
        advanceUntilIdle()
        assertEquals(2, store.turns.value.size)

        assertEquals(2, provider.capturedContexts.size)
        val turn2Context = provider.capturedContexts[1]

        // Conversation history in context should contain Turn 1 (USER + ASSISTANT)
        assertEquals(2, turn2Context.conversation.size)
        assertEquals(Role.USER, turn2Context.conversation[0].role)
        assertEquals("What is the capital of France?", turn2Context.conversation[0].content)
        assertEquals(Role.ASSISTANT, turn2Context.conversation[1].role)
        assertEquals("Paris.", turn2Context.conversation[1].content)
    }

    @Test
    fun `failed generation does not pollute conversation store`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val provider = RecordingProvider(shouldFail = true)
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            scope = this
        )

        session.submit(AiRequest(text = "Hello"))
        advanceUntilIdle()
        assertTrue(store.turns.value.isEmpty())
        assertTrue(eventBus.published.filterIsInstance<ConversationTurnCompleted>().isEmpty())
    }

    @Test
    fun `includeConversation false excludes previous turns from context`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val provider = RecordingProvider(responseText = "Paris.")
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            scope = this
        )

        // Turn 1
        session.submit(AiRequest(text = "Turn 1"))
        advanceUntilIdle()

        // Turn 2 with policy.includeConversation = false
        val noConvPolicy = AiContextPolicy(includeConversation = false)
        session.submit(AiRequest(text = "Turn 2"), policy = noConvPolicy)
        advanceUntilIdle()

        val turn2Context = provider.capturedContexts[1]
        assertTrue(turn2Context.conversation.isEmpty())
    }

    @Test
    fun `clearConversation removes history`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val provider = RecordingProvider(responseText = "Paris.")
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            scope = this
        )

        session.submit(AiRequest(text = "Turn 1"))
        advanceUntilIdle()
        assertEquals(1, store.turns.value.size)

        session.clearConversation()
        advanceUntilIdle()
        assertTrue(store.turns.value.isEmpty())
    }
}
