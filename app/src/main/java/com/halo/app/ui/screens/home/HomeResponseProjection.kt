package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.conversation.ConversationTurn
import com.halo.runtime.cognition.interaction.PresentationMetadata
import com.halo.runtime.cognition.interaction.StructuredPresentation
import com.halo.sdk.provider.CognitiveSessionState

enum class ResponsePresentationSource { STREAMING, SESSION_COMPLETED, COMMITTED_CONVERSATION }

data class PresentedAssistantResponse(
    val conversationId: String,
    val turnId: String? = null,
    val interactionId: String? = null,
    val requestId: String? = null,
    val text: String,
    val source: ResponsePresentationSource,
    /**
     * MINING-P2-001: the committed structured presentation, rebuilt from conversation truth.
     *
     * It is a VIEW and nothing more. Text stays authoritative everywhere: speech, wearable hosts, the
     * Notebook fallback and model context all use [text] only. Absent (or undecodable, e.g. a newer
     * schema version) means Home simply renders prose.
     */
    val structured: StructuredPresentation? = null,
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
        val committed = turns.lastOrNull {
            it.conversationId == activeConversationId && it.assistantText.isNotBlank()
        }
        // Restored once, from the committed turn, so a card survives process death without a second
        // surface store. Nothing transient (loading, progress animation) is ever persisted.
        val committedStructured = committed?.let { PresentationMetadata.decode(it.metadata) }
        val state = sessionState.takeIf { allowTransientSession } ?: CognitiveSessionState.Idle
        return when (state) {
            is CognitiveSessionState.Streaming -> state.text.takeIf(String::isNotBlank)?.let {
                PresentedAssistantResponse(
                    conversationId = activeConversationId,
                    text = it,
                    // Streaming text arrives token by token; a structured surface is only ever shown
                    // once its complete payload is validated and committed.
                    source = ResponsePresentationSource.STREAMING,
                )
            }
            is CognitiveSessionState.Completed -> state.response.text.takeIf(String::isNotBlank)?.let { text ->
                val matchesCommitted = committed?.assistantText == text
                PresentedAssistantResponse(
                    conversationId = activeConversationId,
                    turnId = committed?.id.takeIf { matchesCommitted },
                    interactionId = committed?.metadata?.get("interactionId").takeIf { matchesCommitted },
                    requestId = committed?.metadata?.get("requestId").takeIf { matchesCommitted },
                    text = text,
                    source = ResponsePresentationSource.SESSION_COMPLETED,
                    structured = committedStructured.takeIf { matchesCommitted },
                )
            }
            else -> null
        } ?: committed?.let { turn ->
            PresentedAssistantResponse(
                conversationId = activeConversationId,
                turnId = turn.id,
                interactionId = turn.metadata["interactionId"],
                requestId = turn.metadata["requestId"],
                text = turn.assistantText,
                source = ResponsePresentationSource.COMMITTED_CONVERSATION,
                structured = committedStructured,
            )
        } ?: publishedResponse?.takeIf {
            it.conversationId == activeConversationId && it.text.isNotBlank()
        }
    }
}
