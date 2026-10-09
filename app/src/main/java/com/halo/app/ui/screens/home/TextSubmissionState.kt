package com.halo.app.ui.screens.home

import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TextSubmissionPhase { READY, SUBMITTING, ACCEPTED, PROCESSING, COMPLETED, FAILED, CANCELLED, TIMED_OUT }

data class TextSubmissionState(
    val phase: TextSubmissionPhase = TextSubmissionPhase.READY,
    val interactionId: String? = null,
    val conversationId: String? = null,
    val draft: String = "",
    val requestId: String? = null,
    val message: String? = null,
    val receivedOutput: Boolean = false,
) {
    val busy: Boolean get() = phase in setOf(TextSubmissionPhase.SUBMITTING, TextSubmissionPhase.ACCEPTED, TextSubmissionPhase.PROCESSING)
    val accepted: Boolean get() = requestId != null
    val needsRecovery: Boolean get() = phase in setOf(TextSubmissionPhase.FAILED, TextSubmissionPhase.CANCELLED, TextSubmissionPhase.TIMED_OUT)
    val retryable: Boolean get() = needsRecovery && !receivedOutput && !requestId.orEmpty().startsWith("native:")
}

/** UI receipt tracking only. Interpretation and CognitiveSession remain execution owners. */
class TextSubmissionTracker {
    private val mutable = MutableStateFlow(TextSubmissionState())
    val state = mutable.asStateFlow()

    fun begin(interactionId: String, conversationId: String?, draft: String): Boolean {
        while (true) {
            val current = mutable.value
            if (current.busy || draft.isBlank()) return false
            if (mutable.compareAndSet(current, TextSubmissionState(TextSubmissionPhase.SUBMITTING, interactionId, conversationId, draft))) return true
        }
    }

    fun resolveConversation(id: String, conversation: String) = change(id) { it.copy(conversationId = conversation) }
    fun fail(id: String, message: String) = change(id) { it.copy(phase = TextSubmissionPhase.FAILED, message = message) }
    fun cancel(id: String) = change(id) { it.copy(phase = TextSubmissionPhase.CANCELLED,
        message = if (it.requestId.orEmpty().startsWith("native:")) "Action stopped. Review its recorded outcome before sending again." else "Message cancelled. You can retry it.") }
    fun admissionExpired(id: String) = change(id) {
        if (it.accepted) it else it.copy(phase = TextSubmissionPhase.TIMED_OUT, message = "Message was not accepted. Try again.")
    }
    fun ownerEnded(id: String, committed: Boolean) = change(id) {
        it.copy(phase = if (committed) TextSubmissionPhase.COMPLETED else TextSubmissionPhase.FAILED,
            message = if (committed) null else "The request ended without a reply. You can retry your message.")
    }
    fun reset() { mutable.value = TextSubmissionState() }

    fun onEvent(event: HaloEvent) {
        when (event) {
            is CognitiveRequestStarted -> event.interactionId?.let { id -> change(id) { it.copy(phase = TextSubmissionPhase.ACCEPTED, requestId = event.requestId) } }
            is CognitiveReasoningStarted -> event.interactionId?.let { id -> change(id) { it.copy(phase = TextSubmissionPhase.PROCESSING, requestId = event.requestId) } }
            is CognitiveTokenReceived -> event.interactionId?.let { id -> change(id) { it.copy(phase = TextSubmissionPhase.PROCESSING, receivedOutput = it.receivedOutput || event.token.isNotEmpty()) } }
            is CognitiveCompleted -> event.interactionId?.let { id -> change(id) { it.copy(phase = TextSubmissionPhase.COMPLETED, requestId = event.requestId) } }
            is CognitiveFailed -> event.interactionId?.let { id -> fail(id, event.message) }
            is AssistantResponsePublished -> event.interactionId?.let { id -> change(id) { it.copy(phase = TextSubmissionPhase.COMPLETED, requestId = event.transactionId) } }
            is InteractionTurnChanged -> when (val outcome = event.state) {
                is InteractionTurnState.Failed -> fail(outcome.interactionId, outcome.reason)
                is InteractionTurnState.Cancelled, is InteractionTurnState.Interrupted -> outcome.interactionId?.let(::cancel)
                is InteractionTurnState.Completed -> change(outcome.interactionId) { it.copy(phase = TextSubmissionPhase.COMPLETED) }
                else -> Unit
            }
        }
    }

    private fun change(id: String, transform: (TextSubmissionState) -> TextSubmissionState) {
        while (true) {
            val current = mutable.value
            if (current.interactionId != id || !current.busy) return
            if (mutable.compareAndSet(current, transform(current))) return
        }
    }
}
