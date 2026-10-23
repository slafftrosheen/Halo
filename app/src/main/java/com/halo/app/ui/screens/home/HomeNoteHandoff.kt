package com.halo.app.ui.screens.home

import com.halo.runtime.cognition.agent.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull

/** Presentation follows the existing run; it never resumes or repeats an execution. */
internal suspend fun awaitCreatedHomeNote(runs: Flow<List<AgentRun>>, runId: AgentRunId, callId: String): String? {
    val terminal = runs.mapNotNull { values -> values.firstOrNull { it.id == runId } }
        .first { it.state.isTerminal || noteApprovalRefused(it, callId) }
    return if (terminal.state == AgentRunState.COMPLETED) createdHomeNoteId(terminal, callId) else null
}

private fun noteApprovalRefused(run: AgentRun, callId: String): Boolean {
    val invocation = run.steps.filterIsInstance<AgentStep.ToolInvocationPrepared>().lastOrNull {
        it.toolCallId == callId && it.toolId == "halo_create_empty_note"
    } ?: return false
    val request = run.steps.filterIsInstance<AgentStep.InputRequested>().lastOrNull {
        (it.input as? AgentInputRequest.Approval)?.invocationId == invocation.invocationId
    } ?: return false
    val resolution = run.steps.filterIsInstance<AgentStep.InputResolved>().lastOrNull {
        it.requestId == request.requestId
    }?.resolution ?: return false
    return resolution in setOf(AgentInputResolutions.encode(AgentInputResolution.Denied),
        AgentInputResolutions.encode(AgentInputResolution.Cancelled), AgentInputResolutions.EXPIRED)
}

internal fun createdHomeNoteId(run: AgentRun, callId: String): String? {
    val outcome = run.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().lastOrNull {
        it.toolCallId == callId && it.toolId == "halo_create_empty_note"
    } ?: return null
    if (!outcome.success || outcome.actionTruth != ActionTruth.CONFIRMED) return null
    return Regex("(?:^|\\n)Created note: ([A-Za-z0-9_-]{1,160}) -").find(outcome.output)?.groupValues?.get(1)
}
