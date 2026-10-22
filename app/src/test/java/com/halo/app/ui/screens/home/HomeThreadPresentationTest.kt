package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.conversation.ConversationTurn
import org.junit.Assert.*
import org.junit.Test

class HomeThreadPresentationTest {
    private fun turn(id: String, conversation: String = "chat", interaction: String = id) =
        ConversationTurn(id, "Same question", "Same answer", 1, conversation, mapOf("interactionId" to interaction))

    @Test fun identicalEarlierRepliesRemainVisible() {
        val current = PresentedAssistantResponse("chat", "latest", text = "Same answer",
            source = ResponsePresentationSource.COMMITTED_CONVERSATION)
        assertTrue(homeShowsHistoricalReply(turn("earlier"), current))
        assertFalse(homeShowsHistoricalReply(turn("latest"), current))
        assertTrue(homeShowsHistoricalReply(turn("latest", "another"), current))
    }

    @Test fun streamingTextDoesNotHideAnEqualSavedReply() {
        val streaming = PresentedAssistantResponse("chat", text = "Same answer", source = ResponsePresentationSource.STREAMING)
        assertTrue(homeShowsHistoricalReply(turn("earlier"), streaming))
    }

    @Test fun failedFollowUpWithoutPartialTextKeepsTheSavedAnswerInTheThread() {
        val saved = PresentedAssistantResponse("chat", "saved", text = "Same answer",
            source = ResponsePresentationSource.COMMITTED_CONVERSATION)
        val visible = homeVisibleReplyIdentity(saved, "", priorSavedReply = false)
        assertNull(visible)
        assertTrue(homeShowsHistoricalReply(turn("saved"), visible))
        assertEquals(saved, homeVisibleReplyIdentity(saved, saved.text, priorSavedReply = false))
    }

    @Test fun pendingQuestionAppearsBeforeCommitAndOnlyItsExactInteractionRemovesIt() {
        val pending = TextSubmissionState(TextSubmissionPhase.PROCESSING, "pending", "chat", "Same question")
        assertEquals("Same question", homePendingQuestion(pending, "chat", listOf(turn("earlier"))))
        assertEquals("Same question", homePendingQuestion(pending, "chat", listOf(turn("elsewhere", "other", "pending"))))
        assertNull(homePendingQuestion(pending, "chat", listOf(turn("saved", interaction = "pending"))))
    }

    @Test fun pendingQuestionNeverLeaksAcrossChatsOrAppearsBeforeSubmission() {
        val pending = TextSubmissionState(TextSubmissionPhase.SUBMITTING, "pending", "chat", "Private draft")
        assertNull(homePendingQuestion(pending, "other", emptyList()))
        assertNull(homePendingQuestion(pending.copy(phase = TextSubmissionPhase.READY), "chat", emptyList()))
        assertNull(homePendingQuestion(pending.copy(interactionId = null), "chat", emptyList()))
    }

    @Test fun requestIdentityCanResolveAnOlderCommitFormatButCannotOverrideContradictoryInteraction() {
        val pending = TextSubmissionState(TextSubmissionPhase.COMPLETED, "interaction", "chat", "Same question", requestId = "request")
        val legacy = turn("saved").copy(metadata = mapOf("requestId" to "request"))
        assertNull(homePendingQuestion(pending, "chat", listOf(legacy)))
        val contradictory = legacy.copy(metadata = legacy.metadata + ("interactionId" to "another-interaction"))
        assertEquals("Same question", homePendingQuestion(pending, "chat", listOf(contradictory)))
    }
}
