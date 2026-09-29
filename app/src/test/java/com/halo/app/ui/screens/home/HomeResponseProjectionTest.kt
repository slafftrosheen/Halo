package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.conversation.ConversationTurn
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.CognitiveSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeResponseProjectionTest {
    private val committed = ConversationTurn(
        id = "turn-1",
        userText = "question",
        assistantText = "durable answer",
        timestamp = 1L,
        conversationId = "active",
        metadata = mapOf("interactionId" to "interaction-1", "requestId" to "request-1"),
    )

    @Test fun completedWhileHomeObservingUsesCurrentSession() {
        val result = project(CognitiveSessionState.Completed(AiResponse(text = "session answer", providerId = "provider")))
        assertEquals(ResponsePresentationSource.SESSION_COMPLETED, result?.source)
        assertEquals("session answer", result?.text)
    }

    @Test fun completedBeforeHomeSubscribesComesFromCommittedConversation() {
        val result = project(CognitiveSessionState.Idle)
        assertEquals(ResponsePresentationSource.COMMITTED_CONVERSATION, result?.source)
        assertEquals("durable answer", result?.text)
        assertEquals("request-1", result?.requestId)
    }

    @Test fun activityNavigationRoundTripUsesSameDurableTurn() {
        assertEquals(project(CognitiveSessionState.Idle), project(CognitiveSessionState.Idle))
    }

    @Test fun viewModelStyleReconstructionUsesSameDurableTurn() {
        val first = HomeResponseProjector.project("active", CognitiveSessionState.Idle, null, listOf(committed))
        val reconstructed = HomeResponseProjector.project("active", CognitiveSessionState.Idle, null, listOf(committed))
        assertEquals(first, reconstructed)
    }

    @Test fun processStyleReconstructionDoesNotRequirePublishedEvent() {
        val result = HomeResponseProjector.project("active", CognitiveSessionState.Idle, null, listOf(committed))
        assertEquals("durable answer", result?.text)
    }

    @Test fun newConversationDoesNotReplayPreviousConversation() {
        assertNull(HomeResponseProjector.project("new", CognitiveSessionState.Idle, null, listOf(committed)))
    }

    @Test fun clearConversationDisplaysNothing() {
        assertNull(HomeResponseProjector.project("active", CognitiveSessionState.Idle, null, emptyList()))
    }

    @Test fun streamingHasHighestPriority() {
        val result = project(CognitiveSessionState.Streaming("partial"))
        assertEquals(ResponsePresentationSource.STREAMING, result?.source)
        assertEquals("partial", result?.text)
    }

    private fun project(state: CognitiveSessionState) =
        HomeResponseProjector.project("active", state, null, listOf(committed))
}
