package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.agent.*
import java.time.Instant
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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

    @Test fun `navigation during delayed inference cannot steal the semantic commit`() = runTest {
        val store = InMemoryConversationStore()
        val original = store.activeConversationId()
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val provider = RecordingProvider(beforeResponse = { entered.complete(Unit); release.await() })
        val session = CognitiveSession(FakeEventBus(), FakeRouter(provider), store,
            com.halo.runtime.cognition.context.DefaultContextAssembler(store, DefaultBlackboard()), scope = this)
        session.submit(AiRequest(text = "turn A"))
        runCurrent()
        assertTrue(entered.isCompleted)
        val other = store.newConversation()
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(other, store.activeConversationId())
        assertTrue(store.turns.value.isEmpty())
        assertEquals("turn A", store.turnsForConversation(original).single().userText)
    }
    @Test fun `fresh surface followups share conversation but never interaction or request identity`() = runTest {
        val store = InMemoryConversationStore()
        val provider = RecordingProvider()
        val session = CognitiveSession(FakeEventBus(), FakeRouter(provider), store,
            com.halo.runtime.cognition.context.DefaultContextAssembler(store, DefaultBlackboard()), scope = this)
        session.submit(AiRequest(text = "phone")); advanceUntilIdle()
        session.submit(AiRequest(text = "wearable"), origin = com.halo.runtime.cognition.blackboard.InteractionOrigin(Source.MICROPHONE,
            "device", "capture-new", SpeechInputRoute.Device("device", null, "Wearable", AudioDeviceKind.WEARABLE)))
        advanceUntilIdle()
        val turns = store.turns.value
        assertEquals(2, turns.size)
        assertEquals(turns[0].conversationId, turns[1].conversationId)
        assertNotEquals(turns[0].metadata["interactionId"], turns[1].metadata["interactionId"])
        assertNotEquals(turns[0].metadata["requestId"], turns[1].metadata["requestId"])
        assertEquals("WEARABLE", turns[1].metadata["inputSurface"])
    }

    @Test fun `Android assistant followup preserves source with a new interaction in the same conversation`() = runTest {
        val store = InMemoryConversationStore(); val provider = RecordingProvider(); val events = FakeEventBus()
        val session = CognitiveSession(events, FakeRouter(provider), store,
            com.halo.runtime.cognition.context.DefaultContextAssembler(store, DefaultBlackboard()), scope = this)
        session.submit(AiRequest(text = "phone")); advanceUntilIdle()
        session.submit(AiRequest(text = "assistant"), origin = com.halo.runtime.cognition.blackboard.InteractionOrigin(Source.MICROPHONE,
            captureId = "assistant-capture", speechRoute = SpeechInputRoute.Phone,
            observationOrigin = ObservationOrigin(com.halo.sdk.host.ObservationSource.AndroidAssist)))
        advanceUntilIdle()
        assertEquals(2, store.turns.value.size)
        assertEquals(store.turns.value[0].conversationId, store.turns.value[1].conversationId)
        assertEquals("ANDROID_ASSISTANT", store.turns.value[1].metadata["inputSurface"])
        assertNotEquals(store.turns.value[0].metadata["interactionId"], store.turns.value[1].metadata["interactionId"])
    }

    private class Runs : AgentRunStore {
        val rows = MutableStateFlow<List<AgentRun>>(emptyList())
        override fun observeRuns(): Flow<List<AgentRun>> = rows
        override suspend fun get(runId: AgentRunId) = rows.value.find { it.id == runId }
        override suspend fun create(run: AgentRun) { rows.value += run }
        override suspend fun update(run: AgentRun) { rows.value = rows.value.map { if (it.id == run.id) run else it } }
        override suspend fun appendStep(step: AgentStep): Boolean { val run = get(step.runId) ?: return false; update(run.copy(steps = run.steps + step)); return true }
        override suspend fun nonTerminalRuns() = rows.value.filterNot { it.state.isTerminal }
        override suspend fun claimResume(runId: AgentRunId, expectedState: AgentRunState): Boolean {
            val run = get(runId) ?: return false
            if (run.state != expectedState) return false
            update(run.copy(state = AgentRunState.EXECUTING)); return true
        }
        override suspend fun resolveInputIfPending(requestId: AgentInputRequestId, resolution: AgentInputResolution) = false
    }
    private suspend fun recordedRun(runs: Runs, conversationId: String?): AgentRunId {
        val id = AgentRunId("recover"); val at = Instant.now()
        runs.create(AgentRun(id, com.halo.cognition.language.InteractionId("original-capture"), "original question", AgentRunState.SUSPENDED,
            origin = ObservationOrigin(com.halo.sdk.host.ObservationSource.HeyCyanGlasses, "exact-device"), createdAt = at, updatedAt = at,
            sourceCaptureId = "original-capture", speechOutputTarget = SpeechOutputTarget.Device("exact-device", null, "Glasses", AudioDeviceKind.WEARABLE),
            steps = listOf(AgentStep.RunPrepared(AgentStepId("prepared"), id, at, AgentRunKind.INTERACTIVE, RunRecoveryPolicy.USER_RESUME,
                conversationId, ResponseDelivery.SPEECH_AND_VISUAL), AgentStep.ResponseProduced(AgentStepId("produced"), id, at, "original-response", "recorded answer"))))
        return id
    }
    @Test fun `simultaneous resume commits original response once with wearable route and bound conversation`() = runTest {
        val events = FakeEventBus(); val conversations = InMemoryConversationStore(); val original = conversations.activeConversationId()
        val runs = Runs(); val id = recordedRun(runs, original)
        val coordinator = DefaultAgentRunCoordinator(runs, backgroundScope)
        val provider = RecordingProvider()
        val session = CognitiveSession(events, FakeRouter(provider), conversations,
            com.halo.runtime.cognition.context.DefaultContextAssembler(conversations, DefaultBlackboard()), scope = this,
            agentRunCoordinator = coordinator, agentRunStore = runs)
        val other = conversations.newConversation()
        session.resume(id); session.resume(id); advanceUntilIdle()
        assertEquals(other, conversations.activeConversationId()); assertTrue(conversations.turns.value.isEmpty())
        assertEquals("recorded answer", conversations.turnsForConversation(original).single().assistantText)
        assertEquals(1, runs.get(id)!!.steps.filterIsInstance<AgentStep.RecoveryClaimed>().size)
        assertEquals(1, runs.get(id)!!.steps.filterIsInstance<AgentStep.ResponseCommitted>().size)
        val tts = events.published.filterIsInstance<TtsRequested>().single()
        assertEquals("exact-device", (tts.target as SpeechOutputTarget.Device).deviceId)
        assertEquals(SpeechOutputFallback.DISALLOW_PHONE, tts.fallback)
        assertEquals("original-capture", tts.sourceCaptureId)
        assertTrue(provider.capturedRequests.isEmpty())
    }
    @Test fun `missing owned conversation blocks until explicit current conversation rebind`() = runTest {
        val events = FakeEventBus(); val conversations = InMemoryConversationStore(); val runs = Runs()
        val id = recordedRun(runs, "deleted"); val coordinator = DefaultAgentRunCoordinator(runs, backgroundScope)
        val session = CognitiveSession(events, FakeRouter(RecordingProvider()), conversations,
            com.halo.runtime.cognition.context.DefaultContextAssembler(conversations, DefaultBlackboard()), scope = this,
            agentRunCoordinator = coordinator, agentRunStore = runs)
        session.resume(id); advanceUntilIdle()
        assertTrue(conversations.turns.value.isEmpty())
        assertTrue(runs.get(id)!!.steps.none { it is AgentStep.RecoveryClaimed })
        assertEquals(1, events.published.filterIsInstance<com.halo.runtime.cognition.agent.recovery.AgentRecoveryBlocked>().size)
        session.resume(id, continueInCurrentConversation = true); advanceUntilIdle()
        assertEquals("recorded answer", conversations.turns.value.single().assistantText)
        assertEquals(conversations.activeConversationId(), com.halo.runtime.cognition.continuity.RunContinuityResolver.conversationId(runs.get(id)!!))
    }
    @Test fun `fresh physical turn interrupts tracked recovery and cannot be cleared by its old job`() = runTest {
        val events = FakeEventBus(); val conversations = InMemoryConversationStore(); val runs = Runs()
        val id = AgentRunId("slow-recovery"); val at = Instant.now(); val interaction = com.halo.cognition.language.InteractionId("old-interaction")
        runs.create(AgentRun(id, interaction, "original", AgentRunState.SUSPENDED, createdAt = at, updatedAt = at,
            steps = listOf(AgentStep.RunPrepared(AgentStepId("p"), id, at, AgentRunKind.INTERACTIVE, RunRecoveryPolicy.USER_RESUME,
                conversations.activeConversationId()), AgentStep.ProviderTurnStarted(AgentStepId("started"), id, at, ProviderTurnId("turn"),
                "fake.provider", "test-model", "identity", emptySet(), emptySet()))))
        val coordinator = DefaultAgentRunCoordinator(runs, backgroundScope)
        val entered = kotlinx.coroutines.CompletableDeferred<Unit>(); val never = kotlinx.coroutines.CompletableDeferred<Unit>(); var calls = 0
        val provider = RecordingProvider(beforeResponse = { calls++; if (calls == 1) { entered.complete(Unit); never.await() } })
        val session = CognitiveSession(events, FakeRouter(provider), conversations,
            com.halo.runtime.cognition.context.DefaultContextAssembler(conversations, DefaultBlackboard()), scope = this,
            agentRunCoordinator = coordinator, agentRunStore = runs)
        session.resume(id); runCurrent(); assertTrue(entered.isCompleted)
        session.submit(AiRequest(text = "successor")); advanceUntilIdle()
        assertEquals(AgentRunState.CANCELLED, runs.get(id)!!.state)
        assertEquals("successor", conversations.turns.value.single().userText)
        assertEquals(1, events.published.filterIsInstance<InteractionTurnChanged>().count { it.state is InteractionTurnState.Interrupted })
        assertEquals(2, calls)
        assertEquals(1, events.published.filterIsInstance<ConversationTurnCompleted>().size)
    }

    @Test fun `legacy explicit resume binds once without rewriting original prepared evidence`() = runTest {
        val events = FakeEventBus(); val conversations = InMemoryConversationStore(); val runs = Runs()
        val id = recordedRun(runs, null); val coordinator = DefaultAgentRunCoordinator(runs, backgroundScope)
        val session = CognitiveSession(events, FakeRouter(RecordingProvider()), conversations,
            com.halo.runtime.cognition.context.DefaultContextAssembler(conversations, DefaultBlackboard()), scope = this,
            agentRunCoordinator = coordinator, agentRunStore = runs)
        session.resume(id); session.resume(id); advanceUntilIdle()
        assertNull(runs.get(id)!!.steps.filterIsInstance<AgentStep.RunPrepared>().single().conversationId)
        assertEquals(1, runs.get(id)!!.steps.filterIsInstance<AgentStep.ContinuityBound>().size)
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
        val responseText: String = "Paris.",
        val beforeResponse: suspend () -> Unit = {}
    ) : AiInferenceProvider {
        val capturedContexts = mutableListOf<AiContext>()
        val capturedRequests = mutableListOf<AiRequest>()

        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.STREAMING)

        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            beforeResponse()
            capturedRequests.add(request)
            capturedContexts.add(context)
            if (shouldFail) throw AiProviderException(AiErrorType.GENERATION_FAILED, "Simulated error")
            return AiResponse(text = responseText, providerId = id)
        }

        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
            beforeResponse()
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

        override suspend fun listModels() = listOf(AiModel("test-model", "Test Model", providerId = id,
            inputCapabilities = setOf(AiCapability.TEXT), contextLength = 4096, supportsStreaming = true, supportsTools = true))
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
        val conversationTurns = turn2Context.conversation.filter { it.role != Role.SYSTEM }
        assertEquals(2, conversationTurns.size)
        assertEquals(Role.USER, conversationTurns[0].role)
        assertEquals("What is the capital of France?", conversationTurns[0].content)
        assertEquals(Role.ASSISTANT, conversationTurns[1].role)
        assertEquals("Paris.", conversationTurns[1].content)
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
        assertTrue(turn2Context.conversation.filter { it.role != Role.SYSTEM }.isEmpty())
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
