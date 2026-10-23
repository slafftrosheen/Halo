package com.halo.app.ui.screens.home

import androidx.compose.runtime.Composable
import com.halo.app.language.uiText
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.interaction.PresentationChecklistItem
import com.halo.runtime.cognition.interaction.PresentationLimits
import com.halo.runtime.cognition.interaction.PresentationNode
import com.halo.runtime.cognition.interaction.PresentationStepState
import com.halo.runtime.cognition.interaction.StructuredPresentation

/** Home exposes fixed user copy; the original journal stays available to its existing owner. */
internal fun homeTaskProgress(steps: List<AgentStep>): StructuredPresentation {
    val actions = linkedMapOf<String, Pair<String, PresentationStepState>>()
    val labels = mutableMapOf<String, String>()
    fun outcome(id: String, success: Boolean, truth: ActionTruth, toolId: String? = null) {
        actions[id] = when {
            !success || truth == ActionTruth.FAILED -> "Could not finish" to PresentationStepState.FAILED
            truth == ActionTruth.CONFIRMED -> "Action confirmed" to PresentationStepState.COMPLETED
            toolId == "halo_open_settings" && truth == ActionTruth.DISPATCHED ->
                "Action confirmed" to PresentationStepState.COMPLETED
            else -> "Delivery not confirmed" to PresentationStepState.SKIPPED
        }
    }
    steps.forEach { step ->
        val identity: Pair<String, String>? = when (step) {
            is AgentStep.ToolInvocationPrepared -> step.toolCallId to step.toolId
            is AgentStep.ToolExecutionClaimed -> step.toolCallId to step.toolId
            is AgentStep.ToolOutcomeRecorded -> step.toolCallId to step.toolId
            is AgentStep.ToolCompleted -> step.toolCallId to step.toolId
            is AgentStep.ToolFailed -> step.toolCallId to step.toolId
            is AgentStep.ToolRejected -> step.toolCallId to step.toolId
            is AgentStep.ResourceBlocked -> "resource-${step.toolId}" to step.toolId
            else -> null
        }
        identity?.let { (id, name) -> labels[id] = homeProgressActionLabel(name) }
        when (step) {
            is AgentStep.ToolInvocationPrepared -> actions.putIfAbsent(step.toolCallId, "Ready to start" to PresentationStepState.PENDING)
            is AgentStep.ToolExecutionClaimed -> actions[step.toolCallId] = "Working on your request" to PresentationStepState.ACTIVE
            is AgentStep.ToolOutcomeRecorded -> outcome(step.toolCallId, step.success, step.actionTruth, step.toolId)
            is AgentStep.ToolCompleted -> outcome(step.toolCallId, step.success, step.actionTruth, step.toolId)
            is AgentStep.ToolFailed -> actions[step.toolCallId] = "Could not finish" to PresentationStepState.FAILED
            is AgentStep.ToolRejected -> actions[step.toolCallId] = "Action not run" to
                if (step.requiresUserAction) PresentationStepState.PENDING else PresentationStepState.SKIPPED
            is AgentStep.ResourceBlocked -> actions["resource-${step.toolId}"] = "Waiting for another task to finish" to PresentationStepState.PENDING
            else -> Unit
        }
    }
    // Phone workflows journal their own transactions instead of ordinary tool outcomes.
    // Keep their receipt/approval truth while omitting screen contents and internal phases.
    steps.filterIsInstance<AgentStep.UiTransactionRecorded>().groupBy { it.transaction.toolCallId }.forEach { (id, history) ->
        val latest = history.last().transaction
        val receipt = history.mapNotNull { it.transaction.receipt }.lastOrNull()
        labels[id] = "Phone action"
        when {
            receipt != null -> outcome(id, receipt.actionTruth != ActionTruth.FAILED, receipt.actionTruth)
            latest.phase == com.halo.runtime.cognition.ui.UiWorkflowPhase.WAITING_FOR_APPROVAL ->
                actions[id] = "Waiting for you" to PresentationStepState.PENDING
            latest.searchStatus != null || latest.operation == null && latest.observation != null ->
                actions[id] = "Screen checked" to PresentationStepState.COMPLETED
            else -> actions[id] = "Working on your request" to PresentationStepState.ACTIVE
        }
    }
    val resolvedInputs = steps.filterIsInstance<AgentStep.InputResolved>().mapTo(mutableSetOf()) { it.requestId }
    val pendingInputs = steps.filterIsInstance<AgentStep.InputRequested>().filter { it.requestId !in resolvedInputs }
    if (pendingInputs.isNotEmpty()) actions.replaceAll { _, progress ->
        if (progress.second == PresentationStepState.ACTIVE) "Waiting for you" to PresentationStepState.PENDING else progress
    }
    pendingInputs.forEach { request ->
        actions["input-${request.requestId}"] = "Waiting for you" to PresentationStepState.PENDING
    }
    if (steps.any { it is AgentStep.RunGuardTriggered }) {
        actions.replaceAll { _, progress ->
            if (progress.second in setOf(PresentationStepState.ACTIVE, PresentationStepState.PENDING))
                "Stopped safely" to PresentationStepState.SKIPPED else progress
        }
        actions["run-stop"] = "Stopped safely" to PresentationStepState.SKIPPED
    }
    val items = actions.entries.toList().takeLast(PresentationLimits.MAX_CHECKLIST_ITEMS).mapIndexed { index, (id, progress) ->
        PresentationChecklistItem("step-$index", labels[id]?.let { "$it · ${progress.first}" } ?: progress.first, progress.second)
    }
    return StructuredPresentation(root = PresentationNode.Checklist("home-progress", items, "Task progress"))
}

private fun homeProgressActionLabel(name: String): String = when {
    name == "halo_open_settings" -> "Settings"
    name.startsWith("halo_appfn_") -> "Current app action"
    name.contains("calendar") -> "Calendar"
    name.contains("message") || name.contains("send") -> "Messages"
    name.contains("search") -> "Search"
    name.contains("timer") || name.contains("alarm") -> "Clock"
    name.contains("note") -> "Workspace"
    name.startsWith("halo_ui_") -> "Phone action"
    else -> "App action"
}

/** Only the fixed-copy Home projection is translated here, never a model/user presentation. */
@Composable
internal fun HomeTaskProgress(presentation: StructuredPresentation) {
    val checklist = presentation.root as? PresentationNode.Checklist ?: return
    if (checklist.items.isEmpty()) return
    val localized = checklist.copy(label = uiText("Task progress"), items = checklist.items.map {
        val parts = it.text.split(" · ", limit = 2)
        it.copy(text = parts.map { part -> uiText(part) }.joinToString(" · "))
    })
    com.halo.app.interaction.StructuredPresentationRenderer(presentation.copy(root = localized))
}
