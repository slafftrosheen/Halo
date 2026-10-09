package com.halo.runtime.cognition.context

enum class ContextualNextActionKind { ASK_FOLLOW_UP, SAVE_RESULT, STUDY_SOURCE, CREATE_NOTE, REVIEW_MEMORY, OPEN_SOURCE, CONTINUE_STUDY, REVIEW_RELATED_RUN }
sealed interface ContextualNextActionTarget {
    data class OpenStudy(val sourceId: String) : ContextualNextActionTarget
    data class ReviewSaveResult(val conversationId: String, val turnId: String) : ContextualNextActionTarget
    data class ReviewMemory(val sourceId: String) : ContextualNextActionTarget
    data class ReattachSource(val reference: ContextReference) : ContextualNextActionTarget
    data class FollowUp(val conversationId: String) : ContextualNextActionTarget
    data class OpenSource(val objectId: String) : ContextualNextActionTarget
    data class ContinueStudy(val sessionId: String) : ContextualNextActionTarget
    data class ReviewRun(val runId: String) : ContextualNextActionTarget
}
data class ContextualNextAction(val id: String, val kind: ContextualNextActionKind, val sourceReference: ContextReference,
    val title: String, val target: ContextualNextActionTarget, val expiresAtMs: Long?)
data class ContextualResultState(val result: ContextReference.ConversationTurn?, val completed: Boolean,
    val meaningful: Boolean, val selectedSource: ContextReference.WorkspaceObject? = null,
    val sourceAvailable: Boolean = false, val studySessionId: String? = null, val memorySourceId: String? = null,
    val waitingRunId: String? = null, val expiresAtMs: Long? = null, val unavailableSource: ContextReference? = null)

/** Pure projection. No models, repositories, tools, writes, or navigation. */
object ContextualNextActionProjector {
    fun project(state: ContextualResultState, nowMs: Long): List<ContextualNextAction> {
        if (state.expiresAtMs?.let { nowMs >= it } == true) return emptyList()
        val actions = mutableListOf<ContextualNextAction>()
        fun add(kind: ContextualNextActionKind, ref: ContextReference, title: String, target: ContextualNextActionTarget) {
            actions += ContextualNextAction("$kind:$ref", kind, ref, title, target, state.expiresAtMs)
        }
        state.memorySourceId?.let { add(ContextualNextActionKind.REVIEW_MEMORY, ContextReference.WorkspaceObject(it,null), "Review memory", ContextualNextActionTarget.ReviewMemory(it)) }
        state.studySessionId?.let { add(ContextualNextActionKind.CONTINUE_STUDY, ContextReference.StudySession(it), "Continue lesson", ContextualNextActionTarget.ContinueStudy(it)) }
        state.waitingRunId?.let { state.result?.let { ref -> add(ContextualNextActionKind.REVIEW_RELATED_RUN, ref, "View task", ContextualNextActionTarget.ReviewRun(it)) } }
        state.unavailableSource?.let { add(ContextualNextActionKind.ASK_FOLLOW_UP,it,"Reattach source",ContextualNextActionTarget.ReattachSource(it)) }
        if(!state.completed && state.sourceAvailable && state.selectedSource != null)
            add(ContextualNextActionKind.OPEN_SOURCE,state.selectedSource,"Open source",ContextualNextActionTarget.OpenSource(state.selectedSource.objectId))
        if (state.completed && state.meaningful) state.result?.let { result ->
            if (state.sourceAvailable && state.selectedSource != null) add(ContextualNextActionKind.STUDY_SOURCE, state.selectedSource, "Study this", ContextualNextActionTarget.OpenStudy(state.selectedSource.objectId))
            else add(ContextualNextActionKind.ASK_FOLLOW_UP, result, "Ask follow-up", ContextualNextActionTarget.FollowUp(result.conversationId))
            add(ContextualNextActionKind.SAVE_RESULT, result, "Save result", ContextualNextActionTarget.ReviewSaveResult(result.conversationId,result.turnId))
        }
        return actions.take(2)
    }
}
