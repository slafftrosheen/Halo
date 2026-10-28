package com.halo.runtime.cognition.context

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Identity is not authority. Resolvers must validate source and policy on every turn. */
sealed interface ContextReference {
    data class WorkspaceObject(val objectId: String, val version: String?,
        val textAnchor: com.halo.sdk.runtime.study.StudyTextAnchor? = null) : ContextReference
    data class MediaAsset(val assetId: String, val ownerInteractionId: String?) : ContextReference
    data class StudySession(val sessionId: String, val version: String? = null) : ContextReference
    data class ConversationTurn(val conversationId: String, val turnId: String) : ContextReference
}
enum class ContextCategory { VISUAL, WORKSPACE, STUDY, RESULT }
enum class ContextFocusReason { EXPLICIT_ATTACHMENT, CAPTURE_REVIEW, STUDY_SESSION, USER_SELECTED_WORKSPACE, CONTINUING_RESULT }
enum class ContextReferenceState { AVAILABLE, SELECTED, BOUND_TO_TURN, CONSUMED, EXPIRED, SOURCE_UNAVAILABLE }
data class InteractionContextFocus(
    val conversationId: String, val reference: ContextReference, val category: ContextCategory,
    val reason: ContextFocusReason, val selectedAtMs: Long, val expiresAtMs: Long?,
    val sourceInteractionId: String? = null, val state: ContextReferenceState = ContextReferenceState.SELECTED,
)

/** One reference per category per conversation; bounded, process-only, no content or IO. */
class InteractionContextContinuity(private val nowMs: () -> Long) {
    private val mutable = MutableStateFlow<List<InteractionContextFocus>>(emptyList())
    val state = mutable.asStateFlow()
    @Synchronized fun select(focus: InteractionContextFocus) {
        require(focus.conversationId.isNotBlank())
        mutable.value = (mutable.value.filterNot { it.conversationId == focus.conversationId && it.category == focus.category } + focus).takeLast(32)
    }
    @Synchronized fun current(conversationId: String): List<InteractionContextFocus> {
        mutable.value = mutable.value.map { if (it.expiresAtMs?.let { expiry -> nowMs() >= expiry } == true &&
            it.state !in setOf(ContextReferenceState.EXPIRED, ContextReferenceState.SOURCE_UNAVAILABLE)) it.copy(state = ContextReferenceState.EXPIRED) else it }
        return mutable.value.filter { it.conversationId == conversationId }
    }
    @Synchronized fun transition(conversationId: String, reference: ContextReference, state: ContextReferenceState, interactionId: String? = null) {
        mutable.value = mutable.value.map { if (it.conversationId == conversationId && it.reference == reference)
            it.copy(state = state, sourceInteractionId = interactionId ?: it.sourceInteractionId) else it }
    }
    @Synchronized fun remove(conversationId: String, category: ContextCategory) {
        mutable.value = mutable.value.filterNot { it.conversationId == conversationId && it.category == category }
    }
    @Synchronized fun clear(conversationId: String) { mutable.value = mutable.value.filterNot { it.conversationId == conversationId } }
}
