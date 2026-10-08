package com.halo.app.daily

import com.halo.runtime.cognition.agent.*
import com.halo.runtime.cognition.attention.*
import com.halo.runtime.cognition.routine.*
import com.halo.app.data.study.*
import com.halo.sdk.runtime.InteractionAudioRouteState
import java.time.Instant

enum class DailyAssistantKind { APPROVAL, CLARIFICATION, ATTENTION, NOTIFICATIONS, STUDY_REVIEW, STUDY_RESUME, AUTOMATION_FAILURE, AUTOMATION_UPCOMING, LEARNING_REVIEW, DEVICE_ROUTE }
enum class DailyAssistantPriority { NEEDS_YOU_NOW, DUE, SOON, AVAILABLE }
sealed interface DailyAssistantAction {
    data object OpenInbox : DailyAssistantAction
    data class OpenActivity(val runId: String? = null) : DailyAssistantAction
    data object OpenLearningSuggestions : DailyAssistantAction
    data class OpenStudy(val sessionId: String, val review: Boolean) : DailyAssistantAction
    data class OpenAutomation(val routineId: String?) : DailyAssistantAction
    data object OpenDevices : DailyAssistantAction
    data object OpenVoice : DailyAssistantAction
    data class OpenProvider(val providerId: String?) : DailyAssistantAction
}
data class DailyAssistantItem(val id: String, val kind: DailyAssistantKind, val priority: DailyAssistantPriority,
    val title: String, val summary: String?, val action: DailyAssistantAction, val deduplicationKey: String,
    val occurredAt: Instant?, val importance: AttentionImportance = AttentionImportance.NORMAL)
data class DailyAssistantCounts(val needsYou: Int = 0, val due: Int = 0, val notifications: Int = 0,
    val replyableNotifications: Int = 0, val learningSuggestions: Int = 0)
data class DailyRoutinePreview(val routineId: String, val name: String, val at: Instant)
data class DailyCompanionState(val label: String, val voice: String? = null)
data class DailyVoiceFact(val origin: String, val intended: String, val resolved: String?, val state: InteractionAudioRouteState)
data class DailyAssistantInput(
    val attention: List<QueuedAttentionRequest> = emptyList(), val runs: List<AgentRun> = emptyList(),
    val notifications: Int = 0, val replyable: Int = 0, val dueCards: List<StudyPracticeItem> = emptyList(),
    val lessons: List<StudySessionRecord> = emptyList(), val routines: List<Routine> = emptyList(),
    val routineRuns: List<RoutineRunSummary> = emptyList(), val learningCount: Int = 0,
    val showLearning: Boolean = false, val readyDeviceLabels: List<String> = emptyList(),
    val connectedDeviceLabels: List<String> = emptyList(), val voice: DailyVoiceFact? = null,
)
data class DailyAssistantSnapshot(val primary: DailyAssistantItem? = null,
    val secondary: List<DailyAssistantItem> = emptyList(), val items: List<DailyAssistantItem> = emptyList(),
    val counts: DailyAssistantCounts = DailyAssistantCounts(), val nextRoutine: DailyRoutinePreview? = null,
    val companion: DailyCompanionState? = null, val isCaughtUp: Boolean = true) {
    val summary: String get() = listOfNotNull(
        counts.needsYou.takeIf { it > 0 }?.let { "$it things need you" },
        counts.replyableNotifications.takeIf { it > 0 }?.let { "$it replies available" },
        counts.due.takeIf { it > 0 }?.let { "$it Study cards due" },
    ).joinToString(" · ").ifBlank { if (isCaughtUp) "You're caught up" else "Today · ${items.size} updates" }
}

/** Presentation only: no source bodies, writes, inference, schedule calculation or delivery authority. */
object DailyAssistantProjector {
    fun project(input: DailyAssistantInput, now: Instant): DailyAssistantSnapshot {
        val items = mutableListOf<DailyAssistantItem>()
        val resolvedInputs = input.runs.flatMap { run ->
            run.steps.filterIsInstance<AgentStep.InputResolved>().map { it.requestId.value } +
                run.steps.filterIsInstance<AgentStep.InputRequested>().filter { step ->
                    run.state in setOf(AgentRunState.COMPLETED, AgentRunState.FAILED, AgentRunState.CANCELLED, AgentRunState.INTERRUPTED) ||
                        step.input.expiresAt?.let { it <= now } == true
                }.map { it.requestId.value }
        }.toSet()
        val attention = input.attention.take(50).filter { q ->
            val inputId = when (val intent = q.request.deliveryIntent) {
                is DeliveryIntent.RequestApproval -> intent.approvalId
                is DeliveryIntent.RequestClarification -> intent.clarificationId
                else -> null
            }
            inputId !in resolvedInputs &&
            listOfNotNull(q.expiresAt, q.request.expiresAt).all { it > now }
        }
        val roots = input.runs.filter { it.parentRunId == null }
        val pending = roots.filter { it.state in setOf(AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION) }
            .mapNotNull { run ->
                val resolved = run.steps.filterIsInstance<AgentStep.InputResolved>().map { it.requestId }.toSet()
                run.steps.filterIsInstance<AgentStep.InputRequested>().filter { it.requestId !in resolved && (it.input.expiresAt == null || it.input.expiresAt!! > now) }
                    .maxByOrNull { it.createdAt }?.let { run to it }
            }
        attention.forEach { q ->
            val r = q.request
            val requestId = when (val intent = r.deliveryIntent) {
                is DeliveryIntent.RequestApproval -> intent.approvalId
                is DeliveryIntent.RequestClarification -> intent.clarificationId
                else -> null
            }
            val runId = pending.firstOrNull { it.second.requestId.value == requestId }?.first?.id?.value
                ?: roots.firstOrNull { it.interactionId == r.interactionId && r.interactionId != null }?.id?.value
            val routineFailure = input.routineRuns.firstOrNull { run ->
                r.source == AttentionSource.Routine &&
                    (r.interactionId?.value in setOf(run.id, run.agentRunId) || r.deduplicationKey == "result:${run.id}") &&
                    run.state in setOf(RoutineRunState.FAILED, RoutineRunState.BLOCKED, RoutineRunState.PARTIAL) &&
                    (run.finishedAt ?: run.triggerAt) in now.minusSeconds(86400)..now
            }
            val kind = when (r.deliveryIntent) {
                is DeliveryIntent.RequestApproval -> DailyAssistantKind.APPROVAL
                is DeliveryIntent.RequestClarification -> DailyAssistantKind.CLARIFICATION
                else -> if (routineFailure != null) DailyAssistantKind.AUTOMATION_FAILURE else DailyAssistantKind.ATTENTION
            }
            val priority = when {
                kind in setOf(DailyAssistantKind.APPROVAL, DailyAssistantKind.CLARIFICATION, DailyAssistantKind.AUTOMATION_FAILURE) || r.urgency == AttentionUrgency.IMMEDIATE -> DailyAssistantPriority.NEEDS_YOU_NOW
                r.urgency == AttentionUrgency.SOON -> DailyAssistantPriority.SOON
                else -> DailyAssistantPriority.AVAILABLE
            }
            val title = when (kind) { DailyAssistantKind.APPROVAL -> "Needs your approval"; DailyAssistantKind.CLARIFICATION -> "Needs your answer"; DailyAssistantKind.AUTOMATION_FAILURE -> "Automation needs attention"; else -> "Halo update waiting" }
            // Keep private notification, memory, clarification and routine bodies at their owners.
            items += DailyAssistantItem("attention:${r.id.value}", kind, priority, title,
                if (kind == DailyAssistantKind.AUTOMATION_FAILURE) "An automation didn't finish" else "Review the update in Activity",
                if (kind == DailyAssistantKind.AUTOMATION_FAILURE) DailyAssistantAction.OpenAutomation(routineFailure!!.routineId.value) else DailyAssistantAction.OpenActivity(runId),
                requestId?.let { "input:$it" } ?: "attention:${r.deduplicationKey ?: r.id.value}", r.createdAt, r.importance)
        }
        pending.forEach { (run, step) ->
            val key = "input:${step.requestId.value}"
            if (items.none { it.deduplicationKey == key || (it.action as? DailyAssistantAction.OpenActivity)?.runId == run.id.value && it.kind in setOf(DailyAssistantKind.APPROVAL, DailyAssistantKind.CLARIFICATION) }) {
                val approval = step.input is AgentInputRequest.Approval
                items += DailyAssistantItem("run:${run.id.value}:input", if (approval) DailyAssistantKind.APPROVAL else DailyAssistantKind.CLARIFICATION,
                    DailyAssistantPriority.NEEDS_YOU_NOW, if (approval) "Needs your approval" else "Needs your answer",
                    "Halo is waiting before continuing a task", DailyAssistantAction.OpenActivity(run.id.value), key, step.createdAt)
            }
        }
        val routineAttention = attention.filter { it.request.source == AttentionSource.Routine }
        input.routineRuns.filter { it.state in setOf(RoutineRunState.FAILED, RoutineRunState.BLOCKED, RoutineRunState.PARTIAL) &&
            (it.finishedAt ?: it.triggerAt) >= now.minusSeconds(86400) && (it.finishedAt ?: it.triggerAt) <= now }
            .groupBy { it.routineId }.values.mapNotNull { runs -> runs.maxByOrNull { it.triggerAt } }.forEach { run ->
                if (routineAttention.none { it.request.interactionId?.value in setOf(run.id, run.agentRunId) || it.request.deduplicationKey == "result:${run.id}" }) {
                    items += DailyAssistantItem("routine:failure:${run.id}", DailyAssistantKind.AUTOMATION_FAILURE, DailyAssistantPriority.NEEDS_YOU_NOW,
                        "Automation needs attention", "${input.routines.find { it.id == run.routineId }?.name?.take(80) ?: "An automation"} didn't finish",
                        DailyAssistantAction.OpenAutomation(run.routineId.value), "routine:failure:${run.id}", run.finishedAt ?: run.triggerAt)
                }
            }
        if (input.notifications > 0) items += DailyAssistantItem("inbox", DailyAssistantKind.NOTIFICATIONS,
            if (input.replyable > 0) DailyAssistantPriority.DUE else DailyAssistantPriority.AVAILABLE,
            if (input.replyable > 0) "Messages waiting" else "Notifications",
            "${input.notifications} notifications · ${input.replyable} replyable", DailyAssistantAction.OpenInbox, "inbox", null)
        val due = input.dueCards.filter { it.accepted && !it.mastered && it.dueAtEpochMs <= now.toEpochMilli() }.take(200)
        due.minWithOrNull(compareBy<StudyPracticeItem> { it.dueAtEpochMs }.thenBy { it.id })?.let { card ->
            val lessons = due.map { it.sessionId }.distinct().size
            items += DailyAssistantItem("study:due:${card.sessionId}", DailyAssistantKind.STUDY_REVIEW, DailyAssistantPriority.DUE,
                "Study review", "${due.size} cards due" + if (lessons > 1) " across $lessons lessons" else "",
                DailyAssistantAction.OpenStudy(card.sessionId, true), "study:due:${card.sessionId}", Instant.ofEpochMilli(card.dueAtEpochMs))
        }
        val next = input.routines.filter { it.enabled && it.nextRunAt?.let { at -> at > now && at <= now.plusSeconds(86400) } == true }
            .minWithOrNull(compareBy<Routine> { it.nextRunAt }.thenBy { it.id.value })?.let { DailyRoutinePreview(it.id.value, it.name.take(80), it.nextRunAt!!) }
        next?.let { items += DailyAssistantItem("routine:next:${it.routineId}", DailyAssistantKind.AUTOMATION_UPCOMING,
            DailyAssistantPriority.SOON, "Next automation", it.name, DailyAssistantAction.OpenAutomation(it.routineId), "routine:next:${it.routineId}", it.at) }
        val learning = if (input.showLearning) input.learningCount else 0
        if (learning > 0) items += DailyAssistantItem("learning", DailyAssistantKind.LEARNING_REVIEW, DailyAssistantPriority.AVAILABLE,
            "Learning suggestions", "$learning suggestions ready for review", DailyAssistantAction.OpenLearningSuggestions, "learning", null)
        if (due.isEmpty()) input.lessons.maxWithOrNull(compareBy<StudySessionRecord> { it.updatedAtEpochMs }.thenBy { it.id })?.let {
            items += DailyAssistantItem("study:resume:${it.id}", DailyAssistantKind.STUDY_RESUME, DailyAssistantPriority.AVAILABLE,
                "Continue Study", it.goal?.take(100) ?: "Resume your lesson", DailyAssistantAction.OpenStudy(it.id, false), "study:resume:${it.id}", Instant.ofEpochMilli(it.updatedAtEpochMs))
        }
        val voice = input.voice
        if (voice?.state in setOf(InteractionAudioRouteState.LOST, InteractionAudioRouteState.UNAVAILABLE)) items += DailyAssistantItem("voice-route",
            DailyAssistantKind.DEVICE_ROUTE, DailyAssistantPriority.SOON, "Voice route needs attention", "The last reply's audio route was unavailable",
            DailyAssistantAction.OpenVoice, "voice-route", null)
        val sorted = items.distinctBy { it.deduplicationKey }.sortedWith(compareBy<DailyAssistantItem> { rank(it) }
            .thenByDescending { it.importance.ordinal }.thenBy { it.occurredAt ?: Instant.MAX }.thenBy { it.id })
        val needs = sorted.count { it.priority == DailyAssistantPriority.NEEDS_YOU_NOW }
        val devices = when {
            input.readyDeviceLabels.size == 1 -> "${input.readyDeviceLabels.single()} ready"
            input.readyDeviceLabels.isNotEmpty() -> "${input.readyDeviceLabels.size} devices ready"
            input.connectedDeviceLabels.isNotEmpty() -> "${input.connectedDeviceLabels.size} devices connected"
            else -> "Phone · No wearable active"
        }
        val route = voice?.let { "${if (it.state == InteractionAudioRouteState.ENDED) "Last voice reply" else "Voice"} · ${it.origin} → ${it.resolved ?: it.intended}" +
            if (it.resolved == "Phone" && it.intended != "Phone") " · fallback used" else "" }
        return DailyAssistantSnapshot(sorted.firstOrNull(), sorted.drop(1).take(3), sorted,
            DailyAssistantCounts(needs, due.size, input.notifications, input.replyable, learning), next, DailyCompanionState(devices, route),
            sorted.none { it.kind != DailyAssistantKind.AUTOMATION_UPCOMING })
    }
    private fun rank(item: DailyAssistantItem): Int = when (item.kind) {
        DailyAssistantKind.APPROVAL, DailyAssistantKind.CLARIFICATION -> 0
        DailyAssistantKind.ATTENTION -> when (item.priority) { DailyAssistantPriority.NEEDS_YOU_NOW -> 1; DailyAssistantPriority.SOON -> 5; else -> 9 }
        DailyAssistantKind.AUTOMATION_FAILURE -> 2
        DailyAssistantKind.NOTIFICATIONS -> if (item.priority == DailyAssistantPriority.DUE) 3 else 9
        DailyAssistantKind.STUDY_REVIEW -> 4
        DailyAssistantKind.DEVICE_ROUTE -> 5
        DailyAssistantKind.AUTOMATION_UPCOMING -> 6
        DailyAssistantKind.LEARNING_REVIEW -> 7
        DailyAssistantKind.STUDY_RESUME -> 8
    }
}
