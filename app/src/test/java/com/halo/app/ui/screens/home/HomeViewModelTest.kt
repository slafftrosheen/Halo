package com.halo.app.ui.screens.home

import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiProviderDiagnostic
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.runtime.BlackboardSnapshot
import com.halo.sdk.runtime.RuntimeHealth
import com.halo.sdk.runtime.RuntimeSnapshot
import com.halo.sdk.runtime.RuntimeState
import com.halo.sdk.runtime.SpeechSessionState
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeViewModelTest {

    private val mapper = HomeUiMapper()

    private fun createSnapshot(speechState: SpeechSessionState): RuntimeSnapshot {
        return RuntimeSnapshot(
            runtimeVersion = "1.0",
            buildNumber = 1,
            startedAt = Instant.now(),
            health = RuntimeHealth(healthy = true, uptime = Duration.ZERO, degraded = false, lastFailure = null),
            runtimeState = RuntimeState.Running,
            speech = speechState,
            engines = emptyList(),
            timeline = emptyList(),
            providers = emptyList(),
            blackboard = BlackboardSnapshot(factCount = 0, recentFacts = emptyList())
        )
    }

    @Test
    fun test1_capturingSpeech() {
        val snapshot = createSnapshot(SpeechSessionState.Capturing)
        val uiState = mapper.map(snapshot, CognitiveSessionState.Idle)

        assertEquals(HaloRuntimeState.Capturing, uiState.runtimeState)
        assertTrue(uiState.speechActive)
    }

    @Test
    fun test2_recognizingSpeech() {
        val snapshot = createSnapshot(SpeechSessionState.Recognizing)
        val uiState = mapper.map(snapshot, CognitiveSessionState.Idle)

        assertEquals(HaloRuntimeState.Recognizing, uiState.runtimeState)
        assertTrue(uiState.speechActive)
    }

    @Test
    fun test3_preparingContextDoesNotMapToMicrophone() {
        val snapshot = createSnapshot(SpeechSessionState.Thinking)
        val uiState = mapper.map(snapshot, CognitiveSessionState.PreparingContext)

        assertNotEquals(HaloRuntimeState.Capturing, uiState.runtimeState)
        assertNotEquals(HaloRuntimeState.Recognizing, uiState.runtimeState)
        assertEquals(HaloRuntimeState.Reasoning, uiState.runtimeState)
        assertFalse(uiState.speechActive)
    }

    @Test
    fun test4_reasoningSession() {
        val snapshot = createSnapshot(SpeechSessionState.Thinking)
        val uiState = mapper.map(snapshot, CognitiveSessionState.Reasoning("provider_id"))

        assertEquals(HaloRuntimeState.Reasoning, uiState.runtimeState)
        assertFalse(uiState.speechActive)
    }

    @Test
    fun test5_executingToolSession() {
        val snapshot = createSnapshot(SpeechSessionState.Processing)
        val uiState = mapper.map(snapshot, CognitiveSessionState.ExecutingTool("some_tool"))

        assertEquals(HaloRuntimeState.Executing, uiState.runtimeState)
        assertFalse(uiState.speechActive)
    }

    @Test
    fun test6_speakingWithCompletedCognitiveSession() {
        val snapshot = createSnapshot(SpeechSessionState.Speaking)
        val response = AiResponse(text = "Hello world", providerId = "test_provider")
        val uiState = mapper.map(snapshot, CognitiveSessionState.Completed(response))

        assertEquals(HaloRuntimeState.Speaking, uiState.runtimeState)
        assertFalse(uiState.speechActive)
    }

    @Test
    fun test7_idleSpeechWithCompletedCognitiveSession() {
        val snapshot = createSnapshot(SpeechSessionState.Idle)
        val response = AiResponse(text = "Hello world", providerId = "test_provider")
        val uiState = mapper.map(snapshot, CognitiveSessionState.Completed(response))

        assertEquals(HaloRuntimeState.Idle, uiState.runtimeState)
        assertFalse(uiState.speechActive)
    }

    @Test
    fun test8_failedCognitiveSession() {
        val snapshot = createSnapshot(SpeechSessionState.Idle)
        val uiState = mapper.map(snapshot, CognitiveSessionState.Failed("Connection error"))

        assertEquals(HaloRuntimeState.Idle, uiState.runtimeState)
        assertEquals("Error: Connection error", uiState.contextualText)
        assertFalse(uiState.speechActive)
    }

    @Test
    fun test9_streamingResponseIsNotPhysicalSpeech() {
        val snapshot = createSnapshot(SpeechSessionState.Idle)
        val uiState = mapper.map(snapshot, CognitiveSessionState.Streaming("Partial response"))

        assertEquals(HaloRuntimeState.Reasoning, uiState.runtimeState)
        assertFalse(uiState.speechActive)
    }

    @Test
    fun test10_actualSpeechWinsWhileResponseIsStreaming() {
        val snapshot = createSnapshot(SpeechSessionState.Speaking)
        val uiState = mapper.map(snapshot, CognitiveSessionState.Streaming("Partial response"))

        assertEquals(HaloRuntimeState.Speaking, uiState.runtimeState)
        assertFalse(uiState.speechActive)
    }
    @Test
    fun test11_idleCognitiveSessionExposesBufferedSpeechProcessing() {
        val snapshot = createSnapshot(SpeechSessionState.Thinking)
        val uiState = mapper.map(snapshot, CognitiveSessionState.Idle)

        assertEquals(HaloRuntimeState.Reasoning, uiState.runtimeState)
        assertFalse(uiState.speechActive)
        assertTrue(uiState.speechProcessing)
        assertEquals("Transcribing speech…", uiState.contextualText)
    }

    @Test
    fun test12_cognitiveReasoningOwnsThinkingAfterSpeechHandoff() {
        val snapshot = createSnapshot(SpeechSessionState.Thinking)
        val uiState = mapper.map(
            snapshot,
            CognitiveSessionState.Reasoning("provider_id")
        )

        assertEquals(HaloRuntimeState.Reasoning, uiState.runtimeState)
        assertFalse(uiState.speechActive)
        assertFalse(uiState.speechProcessing)
    }

    @Test
    fun test13_providerDiagnosticProjectsRecoveryState() {
        val snapshot = createSnapshot(SpeechSessionState.Idle)
        val diagnostic = AiProviderDiagnostic(
            providerId = "provider.test",
            displayName = "Test Provider",
            state = ProviderStatus.State.UNCONFIGURED,
            message = "API key missing",
            capabilities = emptySet(),
            selected = true,
            selectedModelId = null,
            local = false
        )

        val uiState = mapper.map(
            snapshot = snapshot,
            sessionState = CognitiveSessionState.Idle,
            selectedProviderId = "provider.test",
            diagnostics = listOf(diagnostic)
        )

        assertEquals(ProviderStatus.State.UNCONFIGURED, uiState.activeProviderState)
        assertEquals("API key missing", uiState.activeProviderMessage)
        assertEquals("Test Provider", uiState.activeProvider)
    }


}
