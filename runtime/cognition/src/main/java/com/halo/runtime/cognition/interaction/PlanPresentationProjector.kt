package com.halo.runtime.cognition.interaction

import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.TaskState
import com.halo.runtime.cognition.agent.AgentStep

/**
 * MINING-P2-001 plan projection.
 *
 * Halo already owns the planner, `ExecutionPlan`, `Task`/`TaskState` and the durable `AgentRun`
 * journal. This projector does not create a second plan truth: it *reads* them and produces a
 * checklist.
 *
 * Harvested conceptually from Android-Agent's visible plan steps (PENDING / IN_PROGRESS / COMPLETED
 * progress projection). Their plan store was not copied — Halo's journal remains canonical, and the
 * agentic path derives **only verified execution phases** from journal evidence.
 */
object PlanPresentationProjector {

    /** From an explicit plan. Step text is derived from the task, never from model prose. */
    fun fromExecutionPlan(
        plan: ExecutionPlan,
        title: String? = null,
        conclusion: String? = null,
    ): StructuredPresentation {
        val items = plan.tasks.mapIndexed { index, task ->
            PresentationDirectiveItem(
                text = describe(task),
                state = mapTaskState(task.state),
            )
        }
        return PresentationTemplates.checklist(
            title = title ?: plan.goal.description?.takeIf { it.isNotBlank() },
            items = items.ifEmpty { listOf(PresentationDirectiveItem("Nothing to do", PresentationStepState.SKIPPED)) },
            conclusion = conclusion,
        )
    }

    /**
     * From the durable journal, for an agentic tool loop where no explicit plan exists.
     *
     * Only phases the journal actually evidences are emitted. The model cannot mark an action
     * complete: a step reads COMPLETED only from a `ToolOutcomeRecorded(success)` or `ToolCompleted`,
     * and FAILED only from a recorded failure. Anything still awaiting user input is PENDING.
     */
    fun fromJournal(
        steps: List<AgentStep>,
        title: String? = null,
        summary: String? = null,
    ): StructuredPresentation {
        val items = mutableListOf<PresentationDirectiveItem>()
        for (step in steps) {
            when (step) {
                is AgentStep.PlanCreated -> items += PresentationDirectiveItem(
                    text = step.summary.take(300),
                    state = PresentationStepState.COMPLETED,
                )
                is AgentStep.ToolInvocationPrepared -> items += PresentationDirectiveItem(
                    text = "${step.toolId}: prepared",
                    state = PresentationStepState.PENDING,
                )
                is AgentStep.ToolExecutionClaimed -> {
                    // A claim is not an outcome: if a later record exists it wins, otherwise ACTIVE.
                    val outcome = steps.any { later ->
                        (later is AgentStep.ToolOutcomeRecorded && later.toolCallId == step.toolCallId) ||
                            (later is AgentStep.ToolFailed && later.toolCallId == step.toolCallId) ||
                            (later is AgentStep.ToolRejected && later.toolCallId == step.toolCallId)
                    }
                    if (!outcome) {
                        items += PresentationDirectiveItem(
                            text = "${step.toolId}: running",
                            state = PresentationStepState.ACTIVE,
                        )
                    }
                }
                is AgentStep.ToolOutcomeRecorded -> items += PresentationDirectiveItem(
                    text = "${step.toolId}: ${if (step.success) "completed" else "failed"}",
                    state = if (step.success) PresentationStepState.COMPLETED else PresentationStepState.FAILED,
                )
                is AgentStep.ToolCompleted -> items += PresentationDirectiveItem(
                    text = "${step.toolId}: completed",
                    state = if (step.success) PresentationStepState.COMPLETED else PresentationStepState.FAILED,
                )
                is AgentStep.ToolFailed -> items += PresentationDirectiveItem(
                    text = "${step.toolId}: failed",
                    state = PresentationStepState.FAILED,
                )
                is AgentStep.ToolRejected -> items += PresentationDirectiveItem(
                    text = "${step.toolId}: not run",
                    state = if (step.requiresUserAction) PresentationStepState.PENDING else PresentationStepState.SKIPPED,
                )
                is AgentStep.ResourceBlocked -> items += PresentationDirectiveItem(
                    text = "${step.toolId}: waiting for a resource",
                    state = PresentationStepState.PENDING,
                )
                is AgentStep.InputRequested -> {
                    val resolved = steps.any { later ->
                        later is AgentStep.InputResolved && later.requestId == step.requestId
                    }
                    if (!resolved) {
                        items += PresentationDirectiveItem(
                            text = "Waiting for your input",
                            state = PresentationStepState.PENDING,
                        )
                    }
                }
                is AgentStep.RunGuardTriggered -> items += PresentationDirectiveItem(
                    text = step.summary.take(300),
                    state = PresentationStepState.SKIPPED,
                )
                else -> Unit
            }
        }
        val bounded = items.take(PresentationLimits.MAX_CHECKLIST_ITEMS)
        return PresentationTemplates.checklist(
            title = title,
            items = bounded.ifEmpty { listOf(PresentationDirectiveItem("No recorded steps", PresentationStepState.SKIPPED)) },
            conclusion = summary,
        )
    }

    /** Compact "3 / 4 steps complete" line for collapsed Activity rows. */
    fun progressLine(steps: List<AgentStep>): String? {
        val presentation = fromJournal(steps)
        val checklist = presentation.root.childChecklist() ?: return null
        val total = checklist.items.size
        if (total == 0) return null
        val done = checklist.items.count { it.state == PresentationStepState.COMPLETED }
        val waiting = checklist.items.any { it.state == PresentationStepState.PENDING }
        return buildString {
            append("$done / $total steps complete")
            if (waiting) append(" · Waiting for approval")
        }
    }

    private fun describe(task: Task): String = when (task) {
        is com.halo.runtime.cognition.ApplicationTask -> task.displayName.ifBlank { task.packageName }
        is com.halo.runtime.cognition.DeviceTask -> task.action
        is com.halo.runtime.cognition.MediaTask -> task.mediaAction
        is com.halo.runtime.cognition.NavigationTask -> "Navigate"
        is com.halo.runtime.cognition.CommunicationTask -> "Send message"
        is com.halo.runtime.cognition.MemoryTask -> task.memoryAction
        is com.halo.runtime.cognition.WebTask -> "Read web page"
        is com.halo.runtime.cognition.SystemTask -> task.command
        else -> "Step"
    }.take(PresentationLimits.MAX_LIST_ITEM_CHARS)

    private fun mapTaskState(state: TaskState): PresentationStepState = when (state) {
        TaskState.PENDING -> PresentationStepState.PENDING
        TaskState.RUNNING -> PresentationStepState.ACTIVE
        TaskState.COMPLETED -> PresentationStepState.COMPLETED
        TaskState.FAILED -> PresentationStepState.FAILED
        TaskState.CANCELLED -> PresentationStepState.SKIPPED
    }

    private fun PresentationNode.childChecklist(): PresentationNode.Checklist? = when (this) {
        is PresentationNode.Checklist -> this
        is PresentationNode.Card -> children.firstNotNullOfOrNull { it.childChecklist() }
        is PresentationNode.Column -> children.firstNotNullOfOrNull { it.childChecklist() }
        is PresentationNode.Row -> children.firstNotNullOfOrNull { it.childChecklist() }
        else -> null
    }
}
