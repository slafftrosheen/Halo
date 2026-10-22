package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.conversation.ConversationTurn

internal fun homeVisibleReplyIdentity(presented: PresentedAssistantResponse?, visibleText: String,
    priorSavedReply: Boolean): PresentedAssistantResponse? =
    presented.takeIf { visibleText.isNotBlank() && !priorSavedReply }

/** The committed turn, never equal prose, identifies the reply rendered by the live card. */
internal fun homeShowsHistoricalReply(turn: ConversationTurn, presented: PresentedAssistantResponse?): Boolean =
    turn.assistantText.isNotBlank() &&
        !(presented?.conversationId == turn.conversationId && presented.turnId == turn.id)

/** Show an admitted/in-flight question until its exact interaction is represented in the transcript. */
internal fun homePendingQuestion(submission: TextSubmissionState, conversationId: String?,
    turns: List<ConversationTurn>): String? {
    if (conversationId.isNullOrBlank() || submission.conversationId != conversationId ||
        submission.phase == TextSubmissionPhase.READY || submission.draft.isBlank() ||
        submission.interactionId.isNullOrBlank()) return null
    val committed = turns.any { turn ->
        if (turn.conversationId != conversationId) false
        else {
            val interaction = turn.metadata["interactionId"]?.takeIf(String::isNotBlank)
            val request = turn.metadata["requestId"]?.takeIf(String::isNotBlank)
            interaction == submission.interactionId || (interaction == null &&
                !submission.requestId.isNullOrBlank() && request == submission.requestId)
        }
    }
    return submission.draft.takeUnless { committed }
}
