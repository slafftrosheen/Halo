package com.halo.runtime.observer

import com.halo.sdk.runtime.*
import org.junit.Assert.*
import org.junit.Test

class VoicePipelineTraceRecorderTest {
    private fun endpoint(id: String, start: Long = 0, end: Long? = 100) = InteractionLatencyUpdated(
        trace = InteractionLatencyTrace(interactionId = id, startedAtMs = start, speechEndedAtMs = end))

    @Test fun contradictoryOutputCallbacksCannotCompleteAnotherInteraction() {
        val store = VoicePipelineTraceStore()
        store.accept(endpoint("a")); store.accept(endpoint("b"))
        store.accept(TtsRequested(text = "private", transactionId = "t", sourceCaptureId = "a", timestamp = 110))
        assertFalse(store.accept(TtsStarted(transactionId = "t", interactionId = "b", timestamp = 120)))
        assertFalse(store.accept(TtsCompleted(transactionId = "t", interactionId = "b", timestamp = 130)))
        assertFalse(store.accept(TtsCancelled(transactionId = "t", interactionId = "b", timestamp = 140)))
        assertFalse(store.accept(TtsFailed(transactionId = "t", interactionId = "b", reason = "private", timestamp = 150)))
        val trace = store.snapshots().first()
        assertNull(trace.first(VoicePipelineStage.FIRST_AUDIO))
        assertNull(trace.first(VoicePipelineStage.OUTPUT_FINISHED))
        assertNull(trace.first(VoicePipelineStage.CANCELLED))
        assertNull(trace.first(VoicePipelineStage.FAILED))
        assertTrue(store.accept(TtsStarted(transactionId = "t", interactionId = "a", timestamp = 160)))
    }

    @Test fun contradictoryInteractionCannotOverwriteARequestOrOutput() {
        val store = VoicePipelineTraceStore()
        store.accept(endpoint("a")); store.accept(endpoint("b"))
        store.accept(CognitiveRequestStarted(requestId = "r", interactionId = "a", timestamp = 110))
        store.accept(CognitiveTokenReceived(requestId = "r", interactionId = "b", token = "wrong turn", timestamp = 120))
        store.accept(TtsRequested(text = "wrong turn", transactionId = "t", sourceCaptureId = "a", interactionId = "b", timestamp = 130))
        store.accept(TtsStarted(transactionId = "t", timestamp = 140))
        assertNull(store.snapshots().first().first(VoicePipelineStage.FIRST_TOKEN))
        assertNull(store.snapshots().first().first(VoicePipelineStage.FIRST_AUDIO))
    }

    @Test fun cancelledOutputAndUnfinishedGenerationNeverBecomeCompletedOverlap() {
        val store = VoicePipelineTraceStore()
        store.accept(endpoint("a"))
        store.accept(CognitiveRequestStarted(requestId = "r", interactionId = "a", timestamp = 110))
        store.accept(CognitiveReasoningStarted(requestId = "r", providerId = "p", modelId = "m", timestamp = 120))
        store.accept(TtsRequested(text = "private", transactionId = "t", sourceCaptureId = "a", timestamp = 130))
        store.accept(TtsStarted(transactionId = "t", timestamp = 150))
        store.accept(TtsCancelled(transactionId = "t", timestamp = 180))
        assertNull(store.snapshots().single().generationAudioOverlapMs)
        assertEquals(180L, store.snapshots().single().first(VoicePipelineStage.CANCELLED))
        assertNull(store.snapshots().single().first(VoicePipelineStage.OUTPUT_FINISHED))
    }

    @Test fun overlappingOutputIntervalsAreCountedOnce() {
        val store = VoicePipelineTraceStore()
        store.accept(endpoint("a"))
        store.accept(CognitiveRequestStarted(requestId = "r", interactionId = "a", timestamp = 110))
        store.accept(CognitiveReasoningStarted(requestId = "r", providerId = "p", modelId = "m", timestamp = 120))
        for (id in listOf("one", "two")) {
            store.accept(TtsRequested(text = "private", transactionId = id, sourceCaptureId = "a", timestamp = 130))
            store.accept(TtsStarted(transactionId = id, timestamp = 150))
            store.accept(TtsCompleted(transactionId = id, timestamp = 200))
        }
        store.accept(CognitiveCompleted(requestId = "r", response = "private", timestamp = 250))
        assertEquals(50L, store.snapshots().single().generationAudioOverlapMs)
    }

    @Test fun interleavedTurnsAndUnknownRequestsNeverStealEvents() {
        val store = VoicePipelineTraceStore()
        store.accept(endpoint("a")); store.accept(endpoint("b"))
        store.accept(CognitiveRequestStarted(requestId = "ra", interactionId = "a", timestamp = 120))
        store.accept(CognitiveRequestStarted(requestId = "rb", interactionId = "b", timestamp = 130))
        store.accept(CognitiveTokenReceived(requestId = "unrelated", interactionId = "b", token = "secret", timestamp = 140))
        store.accept(CognitiveTokenReceived(requestId = "ra", interactionId = "a", token = "private-token", timestamp = 150))
        assertEquals(150L, store.snapshots().first { it.captureId == "a" }.first(VoicePipelineStage.FIRST_TOKEN))
        assertNull(store.snapshots().first { it.captureId == "b" }.first(VoicePipelineStage.FIRST_TOKEN))
    }

    @Test fun streamingAudioOverlapIsMeasuredAndUnrelatedOutputIsIgnored() {
        val store = VoicePipelineTraceStore()
        store.accept(endpoint("a"))
        store.accept(CognitiveRequestStarted(requestId = "r", interactionId = "a", timestamp = 110))
        store.accept(CognitiveReasoningStarted(requestId = "r", interactionId = "a", providerId = "local", modelId = "q", timestamp = 120))
        store.accept(TtsRequested(text = "private output", transactionId = "chunk", sourceCaptureId = "a", timestamp = 180))
        store.accept(TtsStarted(transactionId = "unknown", timestamp = 185))
        store.accept(TtsStarted(transactionId = "chunk", timestamp = 200))
        store.accept(CognitiveCompleted(requestId = "r", response = "private final", interactionId = "a", timestamp = 350))
        store.accept(TtsCompleted(transactionId = "chunk", timestamp = 400))
        val trace = store.snapshots().single()
        assertEquals(100L, trace.endOfSpeechToFirstAudioMs)
        assertEquals(150L, trace.generationAudioOverlapMs)
        assertFalse(store.report().contains("private"))
        assertFalse(store.report().contains("secret"))
    }

    @Test fun missingEndpointAndClockDiscontinuityStayUnmeasured() {
        val store = VoicePipelineTraceStore()
        store.accept(endpoint("live", end = null))
        store.accept(TtsRequested(text = "x", transactionId = "t", sourceCaptureId = "live", timestamp = 200))
        store.accept(TtsStarted(transactionId = "t", timestamp = 210))
        assertNull(store.snapshots().single().endOfSpeechToFirstAudioMs)
        store.accept(endpoint("skew", start = 1000, end = 1100))
        store.accept(TtsRequested(text = "x", transactionId = "skew-t", sourceCaptureId = "skew", timestamp = 1200))
        store.accept(TtsStarted(transactionId = "skew-t", timestamp = 900))
        assertNull(store.snapshots().last().endOfSpeechToFirstAudioMs)
        assertTrue(store.report().contains("NOT_MEASURED"))
    }

    @Test fun tokensAndAbandonedTurnsHaveBoundedStorage() {
        val store = VoicePipelineTraceStore(maxTraces = 2, maxPoints = 8)
        store.accept(endpoint("old")); store.accept(endpoint("a"))
        store.accept(CognitiveRequestStarted(requestId = "r", interactionId = "a", timestamp = 120))
        repeat(1000) { store.accept(CognitiveTokenReceived(requestId = "r", token = "never retain", timestamp = 150L + it)) }
        assertEquals(1, store.snapshots().last().points.count { it.stage == VoicePipelineStage.FIRST_TOKEN })
        store.accept(endpoint("b"))
        store.accept(CognitiveFailed(requestId = "stale", interactionId = "a", message = "private-error", timestamp = 2000))
        assertEquals(listOf("a", "b"), store.snapshots().map { it.captureId })
        assertTrue(store.snapshots().all { it.points.size <= 8 })
        assertFalse(store.report().contains("private-error"))
        assertFalse(store.report().contains("never retain"))
    }
}
