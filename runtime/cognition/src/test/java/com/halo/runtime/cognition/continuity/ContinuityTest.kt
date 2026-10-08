package com.halo.runtime.cognition.continuity

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.runtime.cognition.turn.*
import com.halo.sdk.runtime.*
import com.halo.sdk.provider.AiResponse
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ContinuityTest {
    private class Store : AgentRunStore {
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
    private class Events : EventBus {
        val published = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { published += event }
        override fun events(): Flow<HaloEvent> = emptyFlow()
        override fun <T: HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }
    @Test fun `audio and watch handoffs preserve distinct channels and original run`() = runTest {
        val store = Store(); val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val id = coordinator.start(AgentRequest(InteractionId("i"), "question", conversationId = "c"))
        val applied = mutableListOf<ContinuityDelivery>()
        val continuity = InteractionContinuityCoordinator(store, coordinator, { true }, applyAudioHandoff = { _, delivery -> applied += delivery })
        continuity.bind("c", "i", InteractionEndpoint.Phone); continuity.attach(id)
        val headphones = InteractionEndpoint.AudioAccessory("opaque", "Headphones", AudioDeviceKind.HEADSET)
        continuity.handoff("i", headphones)
        assertEquals(InteractionEndpoint.Phone, continuity.state.value.inputEndpoint)
        assertEquals(InteractionEndpoint.Phone, continuity.state.value.presentationEndpoint)
        val watch = InteractionEndpoint.WearOs("nearby")
        continuity.handoff("i", watch)
        assertEquals(headphones, continuity.state.value.audioOutputEndpoint)
        assertEquals(watch, continuity.state.value.presentationEndpoint)
        assertEquals(id, continuity.state.value.runId)
        assertEquals("i", continuity.state.value.interactionId)
        assertEquals(1, applied.size)
        val delivery = continuity.finalDelivery("i", id, ContinuityDelivery(SpeechOutputTarget.HostDefault, SpeechOutputFallback.ALLOW_PHONE, ResponseDelivery.SPEECH_AND_VISUAL))
        assertEquals("opaque", (delivery.target as SpeechOutputTarget.Device).deviceId)
        assertEquals(watch, delivery.presentationEndpoint)
        assertEquals(SpeechOutputFallback.DISALLOW_PHONE, delivery.fallback)
        assertEquals(1, store.rows.value.size)
        val restored = InteractionContinuityCoordinator(store, coordinator, { true })
        restored.restore(store.get(id)!!)
        assertEquals(watch, restored.state.value.presentationEndpoint)
        assertEquals(headphones, restored.state.value.audioOutputEndpoint)
    }
    @Test fun `waiting status is not a final commit or a completed run`() = runTest {
        val store = Store(); val conversations = InMemoryConversationStore(); val events = Events()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val id = coordinator.start(AgentRequest(InteractionId("i"), "q", conversationId = conversations.activeConversationId()))
        store.update(store.get(id)!!.copy(state = AgentRunState.WAITING_FOR_APPROVAL))
        DefaultCognitiveResponseCommitter(conversations, coordinator, events).commit(CognitiveResponseCommit(
            id, InteractionId("i"), "waiting", "q", AiResponse("Waiting for approval", providerId = "fake"),
            ResponseDelivery.VISUAL_ONLY, SpeechOutputTarget.Phone, SpeechOutputFallback.ALLOW_PHONE, null, emptyMap(),
            conversations.activeConversationId(), terminal = false))
        assertEquals(AgentRunState.WAITING_FOR_APPROVAL, store.get(id)!!.state)
        assertTrue(conversations.turns.value.isEmpty())
        assertTrue(store.get(id)!!.steps.none { it is AgentStep.ResponseCommitted })
    }
    @Test fun `stable final identity never presents or speaks twice after navigation`() = runTest {
        val conversations = InMemoryConversationStore(); val events = Events()
        val id = conversations.activeConversationId()
        val committer = DefaultCognitiveResponseCommitter(conversations, null, events)
        val commit = CognitiveResponseCommit(null, InteractionId("i"), "stable", "q", AiResponse("answer", providerId = "fake"),
            ResponseDelivery.SPEECH_AND_VISUAL, SpeechOutputTarget.Phone, SpeechOutputFallback.ALLOW_PHONE, null, emptyMap(), id)
        committer.commit(commit); val other = conversations.newConversation(); committer.commit(commit)
        assertEquals(1, events.published.filterIsInstance<TtsRequested>().size)
        assertEquals(1, events.published.filterIsInstance<AssistantResponsePublished>().size)
        assertEquals(other, conversations.activeConversationId()); assertTrue(conversations.turns.value.isEmpty())
        assertEquals(1, conversations.turnsForConversation(id).size)
    }
    @Test fun `process-local handoff during preparation is attached to the original run journal`() = runTest {
        val store = Store(); val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val continuity = InteractionContinuityCoordinator(store, coordinator, { true })
        continuity.bind("c", "i", InteractionEndpoint.Phone)
        continuity.handoff("i", InteractionEndpoint.ManagedDevice("exact"))
        val id = coordinator.start(AgentRequest(InteractionId("i"), "q", conversationId = "c"))
        continuity.attach(id)
        assertEquals(InteractionEndpoint.ManagedDevice("exact"), RunContinuityResolver.endpoint(store.get(id)!!))
        val restarted = InteractionContinuityCoordinator(store, coordinator, { true })
        assertNull(restarted.state.value.interactionId)
        restarted.restore(store.get(id)!!)
        assertEquals(InteractionEndpoint.ManagedDevice("exact"), restarted.state.value.current)
    }

    @Test fun `global commit dedup does not activate another conversation`() = runTest {
        val store = InMemoryConversationStore(); val a = store.activeConversationId()
        val first = store.appendIfAbsent(a, "global", "user", "answer")
        val b = store.newConversation()
        val duplicate = store.appendIfAbsent(b, "global", "different", "different")
        assertEquals(first, duplicate); assertTrue(store.turns.value.isEmpty()); assertEquals(b, store.activeConversationId())
        assertFalse(store.activateExistingConversation("missing"))
    }
    @Test fun `concurrent duplicate commits append once`() = runTest {
        val store = InMemoryConversationStore(); val id = store.activeConversationId()
        val a = async { store.appendIfAbsent(id, "global", "user", "answer") }
        val b = async { store.appendIfAbsent(id, "global", "user", "answer") }
        assertEquals(a.await(), b.await()); assertEquals(1, store.turns.value.size)
    }
    @Test fun `journal overrides route and binding while preserving origin`() = runTest {
        val store = Store(); val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val device = SpeechOutputTarget.Device("exact", null, "Glasses", AudioDeviceKind.WEARABLE)
        val id = coordinator.start(AgentRequest(InteractionId("i"), "question", speechOutputTarget = device,
            conversationId = "original", responseDelivery = ResponseDelivery.SPEECH_AND_VISUAL))
        coordinator.recordStep(AgentStep.ContinuityBound(AgentStepId("bound"), id, Instant.now(), "explicit"))
        coordinator.recordStep(AgentStep.InteractionHandoffRecorded(AgentStepId("handoff"), id, Instant.now(),
            InteractionEndpoint.ManagedDevice("exact"), InteractionEndpoint.Phone, InteractionHandoffReason.USER_REQUESTED))
        val run = store.get(id)!!
        assertEquals("explicit", RunContinuityResolver.conversationId(run))
        assertEquals(device, run.speechOutputTarget)
        assertEquals(SpeechOutputFallback.ALLOW_PHONE, RunContinuityResolver.delivery(run).fallback)
    }
    @Test fun `handoff keeps exact run and affects only one final delivery`() = runTest {
        val store = Store(); val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val id = coordinator.start(AgentRequest(InteractionId("i"), "question", conversationId = "c"))
        val other = coordinator.start(AgentRequest(InteractionId("other"), "other", conversationId = "c"))
        val applied = mutableListOf<ContinuityDelivery>()
        val continuity = InteractionContinuityCoordinator(store, coordinator, { true }, applyAudioHandoff = { _, delivery -> applied += delivery })
        continuity.bind("c", "i", InteractionEndpoint.Phone); continuity.attach(id)
        assertEquals(InteractionHandoffStatus.APPLIED, continuity.handoff("i", InteractionEndpoint.ManagedDevice("exact")))
        val resolved = continuity.finalDelivery("i", id, ContinuityDelivery(SpeechOutputTarget.Phone, SpeechOutputFallback.ALLOW_PHONE, ResponseDelivery.VISUAL_ONLY))
        assertEquals("exact", (resolved.target as SpeechOutputTarget.Device).deviceId)
        assertEquals(SpeechOutputFallback.DISALLOW_PHONE, resolved.fallback)
        assertEquals(InteractionHandoffStatus.NO_ACTIVE_INTERACTION, continuity.handoff("i", InteractionEndpoint.Phone))
        assertTrue(store.get(other)!!.steps.none { it is AgentStep.InteractionHandoffRecorded })
        assertEquals(2, store.rows.value.size); assertEquals(1, applied.size)
    }
    @Test fun `unavailable endpoint never becomes authority`() = runTest {
        val store = Store(); val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        var calls = 0
        val continuity = InteractionContinuityCoordinator(store, coordinator, { false }, applyAudioHandoff = { _, _ -> calls++ })
        continuity.bind("c", "i", InteractionEndpoint.Phone)
        assertEquals(InteractionHandoffStatus.UNAVAILABLE, continuity.handoff("i", InteractionEndpoint.ManagedDevice("absent")))
        assertEquals(InteractionEndpoint.Phone, continuity.state.value.current); assertEquals(0, calls)
    }
    @Test fun `commit selects journal handoff without changing semantic response`() = runTest {
        val store = Store(); val conversations = InMemoryConversationStore(); val events = Events()
        val coordinator = DefaultAgentRunCoordinator(store, backgroundScope)
        val id = coordinator.start(AgentRequest(InteractionId("i"), "q", conversationId = conversations.activeConversationId()))
        val continuity = InteractionContinuityCoordinator(store, coordinator, { true })
        continuity.bind(conversations.activeConversationId(), "i", InteractionEndpoint.Phone); continuity.attach(id)
        continuity.handoff("i", InteractionEndpoint.ManagedDevice("exact"))
        DefaultCognitiveResponseCommitter(conversations, coordinator, events, continuity = continuity).commit(CognitiveResponseCommit(
            id, InteractionId("i"), "response", "q", AiResponse("same final text", providerId = "fake"),
            ResponseDelivery.VISUAL_ONLY, SpeechOutputTarget.Phone, SpeechOutputFallback.ALLOW_PHONE, null, emptyMap(), conversations.activeConversationId()))
        assertEquals("same final text", conversations.turns.value.single().assistantText)
        val tts = events.published.filterIsInstance<TtsRequested>().single()
        assertEquals("exact", (tts.target as SpeechOutputTarget.Device).deviceId)
        assertEquals(SpeechOutputFallback.DISALLOW_PHONE, tts.fallback)
        assertEquals(AgentRunState.COMPLETED, store.get(id)!!.state)
    }
}
