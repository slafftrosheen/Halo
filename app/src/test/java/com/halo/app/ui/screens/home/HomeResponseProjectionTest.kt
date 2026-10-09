package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.conversation.ConversationTurn
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.CognitiveSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeResponseProjectionTest {
    @Test fun incompleteResponseKeepsExactIdentityAndDoesNotMoveConversations() {
        val failed = CognitiveSessionState.Failed("Transport interrupted", "Partial answer", "active", "i", "r")
        val result = project(failed)
        assertEquals(ResponsePresentationSource.INCOMPLETE, result?.source)
        assertEquals("Partial answer", result?.text)
        assertEquals("r", result?.requestId)
        assertNull(result?.turnId)
        assertNull(HomeResponseProjector.project("other", failed, null, emptyList()))
    }
    private val committed = ConversationTurn(
        id = "turn-1",
        userText = "question",
        assistantText = "durable answer",
        timestamp = 1L,
        conversationId = "active",
        metadata = mapOf("interactionId" to "interaction-1", "requestId" to "request-1"),
    )

    @Test fun completedWhileHomeObservingUsesDurableIdentity() {
        val result = project(CognitiveSessionState.Completed(AiResponse(text = "session answer", providerId = "provider")))
        assertEquals(ResponsePresentationSource.COMMITTED_CONVERSATION, result?.source)
        assertEquals("durable answer", result?.text)
        assertEquals("turn-1", result?.turnId)
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

    @Test fun identicalAnswersKeepTheLatestExactTurnIdentity() {
        val latest = committed.copy(id = "turn-2", timestamp = 2L,
            metadata = mapOf("interactionId" to "interaction-2", "requestId" to "request-2"))
        val result = HomeResponseProjector.project("active",
            CognitiveSessionState.Completed(AiResponse(text = "durable answer", providerId = "provider")),
            null, listOf(committed, latest))
        assertEquals("turn-2", result?.turnId)
        assertEquals("interaction-2", result?.interactionId)
        assertEquals("request-2", result?.requestId)
    }

    @Test fun streamingHasHighestPriority() {
        val result = project(CognitiveSessionState.Streaming("partial"))
        assertEquals(ResponsePresentationSource.STREAMING, result?.source)
        assertEquals("partial", result?.text)
    }

    private fun project(state: CognitiveSessionState) =
        HomeResponseProjector.project("active", state, null, listOf(committed))
}
