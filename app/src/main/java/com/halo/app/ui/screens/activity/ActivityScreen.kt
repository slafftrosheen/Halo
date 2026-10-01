package com.halo.app.ui.screens.activity

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.halo.app.di.ManualDependencyGraph
import com.halo.runtime.cognition.agent.ActionTruth

import android.os.Trace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.app.ui.state.RuntimeUiFormatters
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.TimelineEvent
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.halo.runtime.cognition.agent.*
import com.halo.sdk.runtime.resource.ResourceId
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Composable
fun ActivityScreen(viewModel: ActivityViewModel, onOpenObject: (String) -> Unit = {}) {
    val reflectionStates by ManualDependencyGraph.personalLearning.reflections.collectAsStateWithLifecycle(emptyList())
    val learningSuggestions by ManualDependencyGraph.personalLearning.pending.collectAsStateWithLifecycle(emptyList())
    val pendingUpdates by ManualDependencyGraph.attentionQueue.pending.collectAsStateWithLifecycle()
    val deliveryReceipts by ManualDependencyGraph.attentionQueue.receipts.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filtered = uiState.events
    var showProcessingDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        HaloScreenHeader("Activity", "What Halo did")
        Spacer(Modifier.size(8.dp))

        if (filtered.isEmpty() && pendingUpdates.isEmpty() && deliveryReceipts.isEmpty() && reflectionStates.isEmpty() && learningSuggestions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing here yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
            item {
                if(pendingUpdates.isNotEmpty()) Text("${pendingUpdates.size} Halo updates waiting")
                pendingUpdates.take(5).forEach { queued ->
                    val content = (queued.request.deliveryIntent as? com.halo.runtime.cognition.attention.DeliveryIntent.Inform)?.content
                    content?.let {
                        Text(it.title.take(120), style = MaterialTheme.typography.titleSmall)
                        Text(it.body.take(240), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if(learningSuggestions.isNotEmpty()) Text("${learningSuggestions.size} learning suggestions pending review in Notebook")
                if (reflectionStates.isNotEmpty() || deliveryReceipts.isNotEmpty()) {
                    TextButton(onClick = { showProcessingDetails = !showProcessingDetails }) {
                        Text(if (showProcessingDetails) "Hide processing details" else "Processing details")
                    }
                    if (showProcessingDetails) {
                reflectionStates.take(5).forEach { state -> Text("Reflection · " + when(state.status) {
                    "NOT_REQUIRED" -> "No durable learning needed"
                    "PENDING" -> "Pending"
                    "FAILED" -> "Learning step failed; task result preserved"
                    "SKIPPED" -> "Model reflection skipped"
                    else -> "Completed"
                }) }
                deliveryReceipts.take(5).forEach { receipt -> Text("Update delivery · ${receipt.outcome.name.lowercase().replace('_', ' ')}") }
                    }
                }
            }

                items(filtered, key = { it.id }) { event ->
                    ActivityListItem(event, onOpenObject)
                }
            }
        }
    }
}

@Composable
private fun ActivityListItem(story: ActivityStory, onOpenObject: (String) -> Unit) {
    val signalKind = when (story.kind) {
        ActivityKind.ERROR -> HaloSignalKind.ERROR
        ActivityKind.USER -> HaloSignalKind.USER
        ActivityKind.SYSTEM -> HaloSignalKind.DEVICE
        ActivityKind.HALO -> HaloSignalKind.HALO
    }
    var expanded by remember(story.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = story.timestamp.formatTime(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(48.dp)
                .padding(top = 8.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (expanded) {
                story.planPresentation?.let { plan ->
                    com.halo.app.interaction.StructuredPresentationRenderer(
                        presentation = plan,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.size(6.dp))
                }
                story.steps.forEach { step ->
                    Text("${step.title}: ${step.message}", style = MaterialTheme.typography.bodySmall,
                        color = if (step.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                story.children.forEach { child ->
                    Column(Modifier.padding(start = 12.dp)) { ActivityListItem(child, onOpenObject) }
                }
                story.objectId?.let { id -> TextButton(onClick = { onOpenObject(id) }) { Text("Open Notebook object") } }
            }
            Text(
                text = story.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (story.status == ActivityStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = story.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4
            )
            story.speechProvenance?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            story.planProgress?.let { progress ->
                Spacer(Modifier.size(2.dp))
                Text(
                    text = progress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}



data class ActivityStep(
    val presentationId: String,
    val title: String,
    val message: String,
    val failed: Boolean = false
)

enum class ActivityKind { USER, SYSTEM, HALO, ERROR }
enum class ActivityStatus { PENDING, SUCCESS, FAILED, INTERRUPTED, UNKNOWN, CANCELLED }

data class ActivityStory(
    val id: String,
    val correlationId: String?,
    val kind: ActivityKind,
    val title: String,
    val summary: String,
    val timestamp: Instant,
    val status: ActivityStatus,
    val steps: List<ActivityStep>,
    val objectId: String? = null,
    /**
     * MINING-P2-001 Part K: progress projected from the AgentRun journal only. A model's prose can
     * never mark a step complete — the checklist reflects recorded execution evidence.
     */
    val planPresentation: com.halo.runtime.cognition.interaction.StructuredPresentation? = null,
    /** Compact "3 / 4 steps complete" line for the collapsed row. */
    val planProgress: String? = null,
    val children: List<ActivityStory> = emptyList(),
    val speechProvenance: String? = null,
)

data class ActivityUiState(
    val events: List<ActivityStory> = emptyList()
)

class ActivityUiMapper {
    fun map(timeline: List<TimelineEvent>): ActivityUiState {
        return try {
            Trace.beginSection("Halo.Activity.map")
            mapInternal(timeline)
        } catch (_: Throwable) {
            mapInternal(timeline)
        } finally {
            runCatching { Trace.endSection() }
        }
    }

    private fun mapInternal(timeline: List<TimelineEvent>): ActivityUiState {
        check(
            timeline.map { it.presentationId }
                .distinct()
                .size == timeline.size
        )
        val stories = mutableListOf<ActivityStory>()
        val byCorrelation = timeline.groupBy { it.correlationId }
        for ((correlationId, events) in byCorrelation) {
            if (correlationId == null) {
                events.forEach { event ->
                    stories.add(
                        ActivityStory(
                            id = event.presentationId,
                            correlationId = null,
                            kind = kindFor(event),
                            title = event.title,
                            summary = event.description,
                            speechProvenance = event.details["stt"],
                            timestamp = event.timestamp,
                            status = statusFor(event),
                            steps = listOf(ActivityStep(event.presentationId, event.title, event.description + if (event.details.isNotEmpty()) "\n" + event.details.entries.joinToString(" · ") { "${it.key}: ${it.value}" } else "", statusFor(event) == ActivityStatus.FAILED)),
                            objectId = event.objectId
                        )
                    )
                }
            } else {
                val sorted = events.sortedBy { it.timestamp }
                val start = sorted.first()
                val isError = sorted.any { statusFor(it) == ActivityStatus.FAILED }
                val lastMeaningful = sorted.lastOrNull { statusFor(it) != ActivityStatus.PENDING } ?: sorted.last()
                stories.add(
                    ActivityStory(
                        id = correlationId,
                        correlationId = correlationId,
                        kind = kindFor(start),
                        title = start.title,
                        summary = lastMeaningful.description,
                        speechProvenance = sorted.firstNotNullOfOrNull { it.details["stt"] },
                        timestamp = start.timestamp,
                        status = if (isError) ActivityStatus.FAILED else ActivityStatus.SUCCESS,
                        steps = sorted.map { ActivityStep(it.presentationId, it.title, it.description + if (it.details.isNotEmpty()) "\n" + it.details.entries.joinToString(" · ") { detail -> "${detail.key}: ${detail.value}" } else "", statusFor(it) == ActivityStatus.FAILED) },
                        objectId = sorted.firstNotNullOfOrNull { it.objectId }
                    )
                )
            }
        }
        val sortedStories = stories.sortedByDescending { it.timestamp }
        return ActivityUiState(events = sortedStories)
    }

    private fun kindFor(event: TimelineEvent): ActivityKind = when {
        statusFor(event) == ActivityStatus.FAILED -> ActivityKind.ERROR
        event.title == "You" -> ActivityKind.USER
        event.title.startsWith("Device") || event.title.startsWith("Capabilities") || event.title == "State Changed" -> ActivityKind.SYSTEM
        else -> ActivityKind.HALO
    }

    private fun statusFor(event: TimelineEvent): ActivityStatus = when {
        event.status.equals("Failed", ignoreCase = true) || event.status.equals("Error", ignoreCase = true) || event.status.equals("Rejected", ignoreCase = true) -> ActivityStatus.FAILED
        event.status.equals("Started", ignoreCase = true) || event.status.equals("Requested", ignoreCase = true) -> ActivityStatus.PENDING
        else -> ActivityStatus.SUCCESS
    }
}

class ActivityViewModel(
    snapshotManager: RuntimeSnapshotSource,
    private val mapper: ActivityUiMapper = ActivityUiMapper(),
    agentRunStore: AgentRunStore? = null
) : ViewModel() {
    val uiState: StateFlow<ActivityUiState> = combine(snapshotManager.snapshot,
        agentRunStore?.observeRuns() ?: flowOf(emptyList<AgentRun>())) { snapshot, runs ->
        val durableIds = runs.flatMap { run ->
            listOf(run.interactionId.value) + run.steps.mapNotNull { step -> when (step) {
                is AgentStep.PlanCreated -> step.planId
                is AgentStep.ToolRequested -> step.toolCallId
                is AgentStep.ToolDispatched -> step.toolCallId
                is AgentStep.ToolExecutionClaimed -> step.toolCallId
                is AgentStep.ToolCompleted -> step.toolCallId
                is AgentStep.ToolOutcomeRecorded -> step.toolCallId
                is AgentStep.ToolRejected -> step.toolCallId
                is AgentStep.ToolFailed -> step.toolCallId
                is AgentStep.ResponseProduced -> step.responseId
                else -> null
            } }
        }.toSet()
        val history = mapper.map(snapshot.timeline.takeLast(500)).events.filterNot { it.correlationId in durableIds }
        val roots = runs.filter { it.parentRunId == null }.map { parent ->
            val story = parent.toStory()
            val speech = snapshot.timeline.firstOrNull {
                it.correlationId == parent.interactionId.value && it.details.containsKey("stt")
            }
            story.copy(speechProvenance = speech?.details?.get("stt"),
                steps = story.steps + listOfNotNull(speech?.let {
                    ActivityStep(it.presentationId, "Speech recognition", it.details.entries.joinToString(" · ") { detail -> "${detail.key}: ${detail.value}" })
                }), children = runs.filter { it.parentRunId == parent.id }.map { child ->
                child.toStory().copy(title = child.delegatedRole?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Delegated task",
                    summary = "${RuntimeUiFormatters.formatRunState(child.state)} · ${child.pinnedProviderId.orEmpty()} / ${child.pinnedModelId.orEmpty()} · " +
                        "${child.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().count { it.success }} reads · ${child.steps.filterIsInstance<AgentStep.DelegationResultRecorded>().lastOrNull()?.evidence?.size ?: 0} sources · " +
                        "${((child.completedAt ?: child.updatedAt).toEpochMilli() - child.createdAt.toEpochMilli()).coerceAtLeast(0) / 1000}s")
            })
        }
        ActivityUiState((history + roots).sortedByDescending { it.timestamp }.take(100))
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityUiState()
        )
}

private fun AgentRun.toStory(): ActivityStory {
    val status = when {
        hasUnknownAction || steps.filterIsInstance<AgentStep.UiTransactionRecorded>().any {
            it.transaction.receipt?.actionTruth in setOf(ActionTruth.DISPATCHED, ActionTruth.UNKNOWN)
        } -> ActivityStatus.UNKNOWN
        state == AgentRunState.INTERRUPTED -> ActivityStatus.INTERRUPTED
        state == AgentRunState.FAILED -> ActivityStatus.FAILED
        state == AgentRunState.CANCELLED -> ActivityStatus.CANCELLED
        state == AgentRunState.COMPLETED -> ActivityStatus.SUCCESS
        else -> ActivityStatus.PENDING
    }
    val pendingInput = steps.filterIsInstance<AgentStep.InputRequested>()
        .lastOrNull { requested -> steps.none { it is AgentStep.InputResolved && it.requestId == requested.requestId } }?.input
    val waitingLabel = when (pendingInput) {
        is com.halo.runtime.cognition.agent.AgentInputRequest.Approval -> "Waiting for your approval"
        is com.halo.runtime.cognition.agent.AgentInputRequest.Clarification -> "Waiting for your answer: " + pendingInput.question
        null -> null
    }
    val formattedState = RuntimeUiFormatters.formatRunState(state)
    /*
     * MINING-P1-001: a deliberately stopped run reads as "Task stopped · <reason>", not as a runtime
     * failure. The summary is content-free by construction (counts, digests, tool ids only).
     */
    val guardStop = steps.filterIsInstance<AgentStep.RunGuardTriggered>().lastOrNull()
    val summaryText = listOfNotNull(
        guardStop?.summary ?: (waitingLabel ?: formattedState),
        "Outcome unknown".takeIf { hasUnknownAction },
        guardStop?.let { "${it.round} tool rounds" },
        failureReason,
    )
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    val activitySteps = steps.map { step ->
        val title = RuntimeUiFormatters.formatStepTitle(step)
        val message = when (step) {
            is AgentStep.ContextUsed -> "${step.objectIds.size} workspace reference(s) · ${step.memoryIds.size} memory reference(s)"
            is AgentStep.UiTransactionRecorded -> step.transaction.let {
                listOfNotNull(it.preview?.description, it.phase.name.lowercase().replace('_', ' '),
                    it.searchStatus?.let { status -> "Search ${status.name.lowercase().replace('_', ' ')}" },
                    it.receipt?.let { receipt -> "${receipt.actionTruth.name.lowercase()} · ${receipt.changeKind.replace('_', ' ')}" }).joinToString(" · ")
            }
            is AgentStep.ToolExecutionClaimed -> "${step.toolId}: action started"
            is AgentStep.ToolOutcomeRecorded -> "${step.toolId}: ${if (step.success) "completed" else "failed"} (${step.actionTruth.name.lowercase()})"
            is AgentStep.ResourceBlocked -> "${step.toolId}: waiting for ${step.resourceIds.joinToString { RuntimeUiFormatters.formatResource(
                ResourceId(it)
            ) }}"
            is AgentStep.ProviderTurnStarted -> "Provider turn started (${step.providerId})"
            is AgentStep.ProviderTurnCompleted -> step.assistantText.ifBlank { "Provider turn completed" }
            is AgentStep.InputRequested -> "Awaiting user input"
            is AgentStep.InputResolved -> "User input resolved (${step.resolution})"
            is AgentStep.ToolRejected -> "${step.toolId}: rejected (${step.reasonCode.name.lowercase()})"
            is AgentStep.ToolFailed -> "${step.toolId}: ${step.reason}"
            is AgentStep.ToolCompleted -> "${step.toolId}: ${step.resultSummary}"
            // MINING-P1-001: reason and bounded evidence, never a raw enum name or user content.
            is AgentStep.RunGuardTriggered -> buildString {
                append(step.summary)
                step.toolId?.let { append(" · ").append(it) }
                append(" · ").append(step.round).append(" rounds")
            }
            else -> step::class.simpleName ?: "Step"
        }
        ActivityStep(
            step.id.value,
            title,
            message,
            failed = step is AgentStep.ToolFailed || step is AgentStep.ToolRejected ||
                (step is AgentStep.ToolOutcomeRecorded && !step.success),
        )
    }

    /*
     * MINING-P2-001 Part K: a projected plan checklist for multi-step runs. Derived only from journal
     * evidence, and only when the run actually recorded several execution phases — a single tool call
     * does not deserve a checklist.
     */
    val evidencedPhases = steps.count { step ->
        step is AgentStep.PlanCreated || step is AgentStep.ToolExecutionClaimed ||
            step is AgentStep.ToolOutcomeRecorded || step is AgentStep.ToolCompleted ||
            step is AgentStep.ToolFailed || step is AgentStep.ToolRejected ||
            step is AgentStep.InputRequested
    }
    val planPresentation = if (evidencedPhases >= 2) {
        // Title is fixed copy: the run title already appears above, and the checklist must not restate
        // the user's request to itself.
        com.halo.runtime.cognition.interaction.PlanPresentationProjector.fromJournal(
            steps = steps,
            title = "Plan",
        )
    } else null
    val planProgress = if (evidencedPhases >= 2) {
        com.halo.runtime.cognition.interaction.PlanPresentationProjector.progressLine(steps)
    } else null

    return ActivityStory(
        id = "agent:${id.value}",
        correlationId = interactionId.value,
        kind = ActivityKind.HALO,
        title = request,
        summary = com.halo.runtime.cognition.ui.UiWorkflowProjector.summary(this) ?: summaryText,
        timestamp = createdAt,
        status = status,
        steps = activitySteps,
        planPresentation = planPresentation,
        planProgress = planProgress,
    )
}

private val activityTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun Instant.formatTime(): String = activityTimeFormatter.format(this)
