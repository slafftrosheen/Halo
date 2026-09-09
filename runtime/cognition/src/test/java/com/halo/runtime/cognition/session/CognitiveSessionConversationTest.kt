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
    ) : AiProvider {
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

    private class FakeRouter(private val provider: AiProvider) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(provider.id)
        override val selectedModelId = MutableStateFlow<String?>(null)
        override fun selectProvider(id: String) {}
        override fun selectModel(modelId: String?) {}
        override fun getAllProviders() = listOf(provider)
        override suspend fun getEligibleProvider(request: AiRequest) = provider
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

        val session = CognitiveSession(
            eventBus = eventBus,
            blackboard = blackboard,
            providerRouter = router,
            conversationStore = store,
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

        val session = CognitiveSession(
            eventBus = eventBus,
            blackboard = blackboard,
            providerRouter = router,
            conversationStore = store,
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

        val session = CognitiveSession(
            eventBus = eventBus,
            blackboard = blackboard,
            providerRouter = router,
            conversationStore = store,
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

        val session = CognitiveSession(
            eventBus = eventBus,
            blackboard = blackboard,
            providerRouter = router,
            conversationStore = store,
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

        val session = CognitiveSession(
            eventBus = eventBus,
            blackboard = blackboard,
            providerRouter = router,
            conversationStore = store,
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
