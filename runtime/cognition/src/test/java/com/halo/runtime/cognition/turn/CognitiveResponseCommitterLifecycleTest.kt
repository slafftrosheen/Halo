package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveResponseCommitterLifecycleTest {
    private class RecordingBus : EventBus {
        val events = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { events += event }
        override fun events(): Flow<HaloEvent> = flow {}
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow {}
    }

    @Test fun spokenModelAnswerRetainsAdmittedCategoriesAndOriginalSharingRevision() = runTest {
        val bus = RecordingBus(); val store = InMemoryConversationStore()
        val committer = DefaultCognitiveResponseCommitter(store, null, bus)
        committer.commit(CognitiveResponseCommit(null, InteractionId("fixture"), "fixture", "synthetic",
            AiResponse("Synthetic answer.", providerId = "on-device"), ResponseDelivery.SPEECH_AND_VISUAL,
            SpeechOutputTarget.Phone, SpeechOutputFallback.DISALLOW_PHONE, null, emptyMap(),
            remoteProvenance = com.halo.sdk.provider.RemoteDispatchProvenance(
                setOf(com.halo.sdk.provider.RemoteDataCategory.WORKSPACE, com.halo.sdk.provider.RemoteDataCategory.SCREEN), 47)))
        val spoken = bus.events.filterIsInstance<TtsRequested>().single()
        assertEquals(setOf("WORKSPACE", "SCREEN"), spoken.remoteDataCategories)
        assertEquals(47L, spoken.remoteDispatchRevision)
    }

    @Test fun nonterminalActionIsVisibleButNotRecordedAsCompletedAndTerminalReceiptIsIdempotent() = runTest {
        val bus = RecordingBus()
        val store = InMemoryConversationStore()
        val committer = DefaultCognitiveResponseCommitter(
            conversationStore = store, agentRunCoordinator = null, eventBus = bus
        )
        val route = SpeechOutputTarget.Device(
            deviceId = "glasses-1", platformEndpointId = null,
            displayName = "Glasses", kind = AudioDeviceKind.WEARABLE
        )
        val pending = CognitiveResponseCommit(
            runId = null, interactionId = InteractionId("interaction-1"), requestId = "request-1",
            userText = "send the message", response = AiResponse(
                text = "Please approve sending the message.", providerId = "native"
            ),
            responseDelivery = ResponseDelivery.SPEECH_AND_VISUAL,
            outputTarget = route, outputFallback = SpeechOutputFallback.DISALLOW_PHONE,
            sourceCaptureId = "capture-1", metadata = emptyMap(),
            conversationId = store.activeConversationId(), terminal = false
        )
        committer.commit(pending)
        assertTrue(store.turns.value.isEmpty())
        assertTrue(bus.events.filterIsInstance<AssistantResponsePublished>().single().continuationPending)
        assertTrue(bus.events.none { it is CognitiveCompleted || it is TtsRequested })
        assertTrue(bus.events.none { it is InferenceObserved })

        val complete = pending.copy(
            response = AiResponse(text = "Message action confirmed.", providerId = "native"),
            terminal = true
        )
        committer.commit(complete)
        committer.commit(complete) // A stale duplicate receipt must do nothing.
        assertEquals(1, store.turns.value.size)
        assertEquals("Message action confirmed.", store.turns.value.single().assistantText)
        assertEquals(1, bus.events.filterIsInstance<CognitiveCompleted>().size)
        assertTrue("native actions must not inflate model inference successes",
            bus.events.none { it is InferenceObserved })
        assertEquals(1, bus.events.filterIsInstance<TtsRequested>().size)
        val speech = bus.events.filterIsInstance<TtsRequested>().single()
        assertEquals(SpeechOutputFallback.DISALLOW_PHONE, speech.fallback)
        assertEquals("glasses-1", (speech.target as SpeechOutputTarget.Device).deviceId)
        val published = bus.events.filterIsInstance<AssistantResponsePublished>()
        assertEquals(2, published.size)
        assertFalse(published.last().continuationPending)
        assertEquals("interaction-1", published.last().interactionId)
    }
    @Test fun actualModelReplyStillRecordsExactlyOneInference() = runTest {
        val bus = RecordingBus()
        val store = InMemoryConversationStore()
        val committer = DefaultCognitiveResponseCommitter(
            conversationStore = store, agentRunCoordinator = null, eventBus = bus
        )
        val commit = CognitiveResponseCommit(
            runId = null, interactionId = InteractionId("real-turn"), requestId = "real-request",
            userText = "Hi", response = AiResponse(text = "Hello", providerId = "litert"),
            responseDelivery = ResponseDelivery.VISUAL_ONLY,
            outputTarget = SpeechOutputTarget.Phone, outputFallback = SpeechOutputFallback.ALLOW_PHONE,
            sourceCaptureId = null, metadata = emptyMap(),
            conversationId = store.activeConversationId()
        )
        committer.commit(commit)
        committer.commit(commit)
        val events = bus.events.filterIsInstance<InferenceObserved>()
        assertEquals(1, events.size)
        assertEquals("litert", events.single().providerId)
        assertEquals(true, events.single().success)
    }
}
