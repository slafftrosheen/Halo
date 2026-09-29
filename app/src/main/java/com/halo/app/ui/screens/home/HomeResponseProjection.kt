package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.conversation.ConversationTurn
import com.halo.sdk.provider.CognitiveSessionState

enum class ResponsePresentationSource { STREAMING, SESSION_COMPLETED, COMMITTED_CONVERSATION }

data class PresentedAssistantResponse(
    val conversationId: String,
    val turnId: String? = null,
    val interactionId: String? = null,
    val requestId: String? = null,
    val text: String,
    val source: ResponsePresentationSource,
)

/** Pure projection of Home's answer from transient session state and durable conversation truth. */
object HomeResponseProjector {
    fun project(
        activeConversationId: String,
        sessionState: CognitiveSessionState,
        publishedResponse: PresentedAssistantResponse?,
        turns: List<ConversationTurn>,
        allowTransientSession: Boolean = true,
    ): PresentedAssistantResponse? {
        if (activeConversationId.isBlank()) return null
        val state = sessionState.takeIf { allowTransientSession } ?: CognitiveSessionState.Idle
        return when (state) {
            is CognitiveSessionState.Streaming -> state.text.takeIf(String::isNotBlank)?.let {
                PresentedAssistantResponse(
                    conversationId = activeConversationId,
                    text = it,
                    source = ResponsePresentationSource.STREAMING,
                )
            }
            is CognitiveSessionState.Completed -> state.response.text.takeIf(String::isNotBlank)?.let {
                PresentedAssistantResponse(
                    conversationId = activeConversationId,
                    text = it,
                    source = ResponsePresentationSource.SESSION_COMPLETED,
                )
            }
            else -> null
        } ?: turns.lastOrNull {
            it.conversationId == activeConversationId && it.assistantText.isNotBlank()
        }?.let { turn ->
            PresentedAssistantResponse(
                conversationId = activeConversationId,
                turnId = turn.id,
                interactionId = turn.metadata["interactionId"],
                requestId = turn.metadata["requestId"],
                text = turn.assistantText,
                source = ResponsePresentationSource.COMMITTED_CONVERSATION,
            )
        } ?: publishedResponse?.takeIf {
            it.conversationId == activeConversationId && it.text.isNotBlank()
        }
    }
}
