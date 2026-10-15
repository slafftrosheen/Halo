package com.halo.app.ui.screens.activity

import com.halo.app.language.uiText
import com.halo.app.R
import androidx.compose.ui.res.stringResource
import com.halo.runtime.cognition.native.NativeActionOutcome
import com.halo.runtime.cognition.native.NativeWorkflowRecoveryGuide
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloEmptyState
import com.halo.app.ui.design.HaloHeaderAction
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import androidx.compose.ui.text.style.TextAlign
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.app.ui.state.RuntimeUiFormatters
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.TimelineEvent
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
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
@OptIn(ExperimentalLayoutApi::class)
fun ActivityScreen(viewModel: ActivityViewModel, focusRunId: String? = null, onOpenObject: (String) -> Unit = {}, onReviewRun: (String) -> Unit = {}, onHome: () -> Unit = {}) {
    val reflectionStates by ManualDependencyGraph.personalLearning.reflections.collectAsStateWithLifecycle(emptyList())
    val learningSuggestions by ManualDependencyGraph.personalLearning.pending.collectAsStateWithLifecycle(emptyList())
    val pendingUpdates by ManualDependencyGraph.attentionQueue.pending.collectAsStateWithLifecycle()
    val deliveryReceipts by ManualDependencyGraph.attentionQueue.receipts.collectAsStateWithLifecycle()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Round 2: exactly three user-facing views, while ALL remains an internal ledger filter.
    // The default follows the current journal; manually chosen tabs remain sticky.
    var selectedFilter by rememberSaveable(focusRunId) { mutableStateOf<ActivityFilter?>(null) }
    val focusedStory = uiState.events.firstOrNull {
        focusRunId != null && (it.id == "agent:$focusRunId" || focusRunId in it.relatedRunIds)
    }
    val filter = selectedFilter ?: when {
        focusedStory?.reviewRunId != null -> ActivityFilter.REVIEW
        focusedStory?.status == ActivityStatus.PENDING -> ActivityFilter.RUNNING
        focusedStory != null -> ActivityFilter.HISTORY
        uiState.events.any { it.reviewRunId != null } ||
            pendingUpdates.isNotEmpty() || learningSuggestions.isNotEmpty() -> ActivityFilter.REVIEW
        uiState.events.any { it.status == ActivityStatus.PENDING } -> ActivityFilter.RUNNING
        else -> ActivityFilter.HISTORY
    }
    val filtered = filterActivityStories(uiState.events, filter)
    val reviewCount = uiState.events.count { it.reviewRunId != null } +
        pendingUpdates.size + learningSuggestions.size
    val runningCount = uiState.events.count {
        it.status == ActivityStatus.PENDING && it.reviewRunId == null
    }
    val doneCount = uiState.events.count {
        it.status != ActivityStatus.PENDING && it.reviewRunId == null
    }
    val hasSupplementaryReview = filter == ActivityFilter.REVIEW &&
        (pendingUpdates.isNotEmpty() || learningSuggestions.isNotEmpty())
    val nextAvailableFilter = listOf(ActivityFilter.REVIEW, ActivityFilter.RUNNING,
        ActivityFilter.HISTORY).firstOrNull { other ->
        other != filter && filterActivityStories(uiState.events, other).isNotEmpty()
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    var focusScrolled by remember(focusRunId) { mutableStateOf(false) }
    LaunchedEffect(focusRunId, filtered, filter) {
        val index = filtered.indexOfFirst { it.id == "agent:$focusRunId" ||
            focusRunId != null && focusRunId in it.relatedRunIds }
        if (!focusScrolled && focusRunId != null && index >= 0) {
            listState.scrollToItem(index + 1)
            focusScrolled = true
        }
    }
    var showProcessingDetails by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        HaloScreenHeader(uiText("Activity"), uiText("See what needs you, what's running and what finished."),
            glyph = HaloGlyph.ACTIVITY)
        Spacer(Modifier.size(4.dp))
        // Each tab has a non-colour selected indicator, a count and a 48dp touch target.
        // Horizontal scroll prevents clipped labels at larger accessibility font scales.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ActivityFilter.REVIEW, ActivityFilter.RUNNING, ActivityFilter.HISTORY).forEach { option ->
                val count = when (option) {
                    ActivityFilter.REVIEW -> reviewCount
                    ActivityFilter.RUNNING -> runningCount
                    ActivityFilter.HISTORY -> doneCount
                    ActivityFilter.ALL -> 0
                }
                FilterChip(
                    selected = filter == option,
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = { selectedFilter = option },
                    label = { Text(uiText(option.label) + if (count > 0) " ($count)" else "") },
                )
            }
        }

        if (filtered.isEmpty() && !hasSupplementaryReview) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                HaloEmptyState(
                    uiText(if (uiState.events.isEmpty()) "No activity yet" else "Nothing in this view"),
                    uiText(if (uiState.events.isEmpty()) "Ask halo something on Home to get started."
                        else "There is nothing here right now. You can check another tab."),
                    HaloGlyph.ACTIVITY,
                    action = if (uiState.events.isEmpty()) HaloHeaderAction(uiText("Open Home"), onHome)
                        else nextAvailableFilter?.let { alternate ->
                            HaloHeaderAction(when (alternate) {
                                ActivityFilter.REVIEW -> uiText("Show requests")
                                ActivityFilter.RUNNING -> uiText("Show running")
                                ActivityFilter.HISTORY -> uiText("Show completed")
                                ActivityFilter.ALL -> uiText("Show all")
                            }) { selectedFilter = alternate }
                        }
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                val sectionTitle = when (filter) {
                    ActivityFilter.REVIEW -> "Needs you"
                    ActivityFilter.RUNNING -> "Running"
                    ActivityFilter.HISTORY -> "Done"
                    ActivityFilter.ALL -> "Recent activity"
                }
                Text(uiText(sectionTitle), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.size(8.dp))
                if (filter == ActivityFilter.REVIEW) {
                    Text(uiText("Approvals and answers wait here until you choose what happens next."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pendingUpdates.isNotEmpty()) {
                        Spacer(Modifier.size(8.dp))
                        Text(uiText("%1\$d updates waiting", pendingUpdates.size),
                            style = MaterialTheme.typography.labelLarge)
                        pendingUpdates.take(5).forEach { queued ->
                            val content = (queued.request.deliveryIntent as?
                                com.halo.runtime.cognition.attention.DeliveryIntent.Inform)?.content
                            content?.let {
                                Text(it.title.take(120), style = MaterialTheme.typography.titleSmall)
                                Text(it.body.take(240), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (learningSuggestions.isNotEmpty())
                        Text(uiText("%1\$d suggestions to review in Memory", learningSuggestions.size))
                }
                // Reflection and transport receipts are technical evidence, not user-facing tasks.
                if (filter == ActivityFilter.HISTORY &&
                    (reflectionStates.isNotEmpty() || deliveryReceipts.isNotEmpty())) {
                    TextButton(onClick = { showProcessingDetails = !showProcessingDetails }) {
                        Text(uiText(if (showProcessingDetails) "Hide technical details" else "Technical details"))
                    }
                    if (showProcessingDetails) {
                        reflectionStates.take(5).forEach { state ->
                            Text(uiText("Reflection") + " · " + when (state.status) {
                                "NOT_REQUIRED" -> "No durable learning needed"
                                "PENDING" -> "Pending"
                                "FAILED" -> "Learning step failed; task result preserved"
                                "SKIPPED" -> "Model reflection skipped"
                                else -> "Completed"
                            })
                        }
                        deliveryReceipts.take(5).forEach { receipt ->
                            Text("Update delivery · ${receipt.outcome.name.lowercase().replace('_', ' ')}")
                        }
                    }
                }
            }

                items(filtered, key = { it.id }) { event ->
                    ActivityListItem(event, onOpenObject, onReviewRun, onHome,
                        focused = event.id == "agent:$focusRunId" || focusRunId != null && focusRunId in event.relatedRunIds)
                }
            }
        }
    }
}

@Composable
private fun ActivityListItem(story: ActivityStory, onOpenObject: (String) -> Unit,
    onReviewRun: (String) -> Unit, onHome: () -> Unit, focused: Boolean = false) {
    var expanded by remember(story.id, focused) { mutableStateOf(focused) }
    val needsYou = story.reviewRunId != null
    // Apricot means the user is needed, never an active/completed step.
    val attentionInk = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
        Color(0xFFFF9F6B) else Color(0xFFA84B12)
    val statusInk = when {
        story.status == ActivityStatus.FAILED -> MaterialTheme.colorScheme.error
        needsYou -> attentionInk
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, when {
            focused -> MaterialTheme.colorScheme.primary
            needsYou -> attentionInk
            else -> MaterialTheme.colorScheme.outlineVariant
        })) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    HaloIcon(when {
                        needsYou -> HaloGlyph.APPROVAL
                        story.status == ActivityStatus.FAILED -> HaloGlyph.ERROR
                        story.status == ActivityStatus.PENDING -> HaloGlyph.ACTIVITY
                        story.status == ActivityStatus.SUCCESS -> HaloGlyph.SUCCESS
                        story.status == ActivityStatus.UNKNOWN -> HaloGlyph.WARNING
                        else -> HaloGlyph.STOP
                    }, contentDescription = null, modifier = Modifier.size(18.dp), tint = statusInk)
                    Text(uiText(activityStatusLabel(story)), style = MaterialTheme.typography.labelLarge,
                        color = statusInk)
                }
                val appLocale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
                Text(story.timestamp.activityDisplayTime(appLocale),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (story.phoneTotal != null) stringResource(R.string.halo_phone_workflow_title) else story.title,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (story.phoneTotal != null) stringResource(phoneSummaryResource(story)) else uiText(story.summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            story.planProgress?.let { progress ->
                Text(if (story.phoneTotal != null) stringResource(R.string.halo_phone_progress,
                    story.phoneConfirmed ?: 0, story.phoneTotal) else progress,
                    style = MaterialTheme.typography.bodyMedium)
            }
            story.phoneGuidance?.let { guide ->
                val nextOrdinal = guide.nextOrdinal
                if (nextOrdinal != null && guide.action !in setOf(
                        NativeWorkflowRecoveryGuide.Action.COMPLETE,
                        NativeWorkflowRecoveryGuide.Action.RUNNING,
                        NativeWorkflowRecoveryGuide.Action.STOPPED
                    )) {
                    Text(stringResource(R.string.halo_phone_first_unresolved,
                        nextOrdinal, guide.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(phoneRecoveryActionResource(guide.action)),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            story.reviewRunId?.let { runId ->
                Text(uiText("Review to answer or approve. Closing the review leaves it waiting."),
                    style = MaterialTheme.typography.bodyMedium)
                HaloActionButton(onClick = { onReviewRun(runId) }) { Text(uiText("Review steps")) }
            }
            if (story.phoneTotal != null && story.reviewRunId == null &&
                story.status in setOf(ActivityStatus.INTERRUPTED, ActivityStatus.FAILED, ActivityStatus.UNKNOWN)) {
                Text(stringResource(R.string.halo_phone_fresh_instruction_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HaloActionButton(onClick = onHome, style = HaloActionStyle.SECONDARY) {
                    Text(stringResource(R.string.halo_phone_new_instruction))
                }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(uiText(if (expanded) "Hide details" else "Details"))
            }
            if (expanded) {
                story.planPresentation?.let { com.halo.app.interaction.StructuredPresentationRenderer(it, Modifier.fillMaxWidth()) }
                story.speechProvenance?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                story.steps.forEach { step ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (story.phoneTotal != null && step.phoneOrdinal != null) {
                            stringResource(R.string.halo_phone_step,
                                step.phoneOrdinal, story.phoneTotal,
                                stringResource(phoneKindResource(step.phoneKind)))
                        } else step.title, style = MaterialTheme.typography.titleSmall)
                        Text(if (story.phoneTotal != null && step.phoneForegroundVerified)
                            stringResource(R.string.halo_phone_foreground_verified)
                            else if (story.phoneTotal != null) stringResource(phoneOutcomeResource(step.phoneOutcome))
                            else step.message, style = MaterialTheme.typography.bodyMedium,
                            color = if (step.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                story.children.forEach { child -> ActivityListItem(child, onOpenObject, onReviewRun, onHome) }
                story.objectId?.let { id -> HaloActionButton(onClick = { onOpenObject(id) },
                    style = HaloActionStyle.SECONDARY) { Text(uiText("Open in Workspace")) } }
            }
        }
    }
}

private fun phoneSummaryResource(story: ActivityStory): Int = when (story.status) {
    ActivityStatus.SUCCESS -> R.string.halo_phone_success
    ActivityStatus.UNKNOWN -> R.string.halo_phone_unknown
    ActivityStatus.FAILED -> R.string.halo_phone_failed
    ActivityStatus.CANCELLED -> R.string.halo_phone_stopped
    ActivityStatus.PENDING -> if (story.reviewRunId != null)
        R.string.halo_phone_review else R.string.halo_phone_wait
    ActivityStatus.INTERRUPTED -> R.string.halo_phone_incomplete
}

private fun phoneRecoveryActionResource(action: NativeWorkflowRecoveryGuide.Action): Int =
    when (action) {
        NativeWorkflowRecoveryGuide.Action.COMPLETE -> R.string.halo_phone_success
        NativeWorkflowRecoveryGuide.Action.REVIEW_INPUT -> R.string.halo_phone_recovery_approval
        NativeWorkflowRecoveryGuide.Action.WAIT_FOR_DEVICE -> R.string.halo_phone_recovery_device
        NativeWorkflowRecoveryGuide.Action.INSPECT_UNCERTAIN_EFFECT -> R.string.halo_phone_recovery_inspect
        NativeWorkflowRecoveryGuide.Action.REQUEST_FRESH_INSTRUCTION -> R.string.halo_phone_recovery_fresh
        NativeWorkflowRecoveryGuide.Action.RUNNING -> R.string.halo_phone_recovery_running
        NativeWorkflowRecoveryGuide.Action.STOPPED -> R.string.halo_phone_stopped
    }

private fun phoneKindResource(kind: String?): Int = when (kind) {
    "OPEN_APP" -> R.string.halo_phone_open
    "FIND" -> R.string.halo_phone_find
    "TAP" -> R.string.halo_phone_tap
    "ENTER_TEXT" -> R.string.halo_phone_type
    "SCROLL_FORWARD" -> R.string.halo_phone_scroll_down
    "SCROLL_BACKWARD" -> R.string.halo_phone_scroll_up
    "BACK" -> R.string.halo_phone_back
    else -> R.string.halo_phone_action
}

private fun phoneOutcomeResource(outcome: NativeActionOutcome?): Int = when (outcome) {
    NativeActionOutcome.CONFIRMED -> R.string.halo_phone_verified
    NativeActionOutcome.DISPATCHED -> R.string.halo_phone_dispatched
    NativeActionOutcome.UNKNOWN -> R.string.halo_phone_unconfirmed
    NativeActionOutcome.REJECTED -> R.string.halo_phone_rejected
    NativeActionOutcome.FAILED -> R.string.halo_phone_error
    NativeActionOutcome.CANCELLED -> R.string.halo_phone_stopped_short
    NativeActionOutcome.WAITING_FOR_INPUT -> R.string.halo_phone_input
    NativeActionOutcome.WAITING_FOR_CAPABILITY -> R.string.halo_phone_permission
    NativeActionOutcome.WAITING_FOR_RESOURCE -> R.string.halo_phone_resource
    null -> R.string.halo_phone_not_started
}

internal fun activityStatusLabel(story: ActivityStory): String = when {
    story.reviewRunId != null -> "Needs your review"
    story.status == ActivityStatus.UNKNOWN -> "Result not confirmed"
    story.status == ActivityStatus.INTERRUPTED -> "Interrupted"
    story.status == ActivityStatus.CANCELLED -> "Stopped"
    story.status == ActivityStatus.FAILED -> "Couldn’t finish"
    story.status == ActivityStatus.PENDING -> "In progress"
    else -> "Completed"
}

internal enum class ActivityFilter(val label: String) {
    ALL("All"), REVIEW("Needs you"), RUNNING("Running"), HISTORY("Done")
}

internal fun filterActivityStories(stories: List<ActivityStory>, filter: ActivityFilter): List<ActivityStory> =
    stories.filter { story -> when (filter) {
        ActivityFilter.ALL -> true
        ActivityFilter.REVIEW -> story.reviewRunId != null
        ActivityFilter.RUNNING -> story.status == ActivityStatus.PENDING && story.reviewRunId == null
        ActivityFilter.HISTORY -> story.status != ActivityStatus.PENDING && story.reviewRunId == null
    } }.sortedWith(compareByDescending<ActivityStory> { it.reviewRunId != null }.thenByDescending { it.timestamp })



data class ActivityStep(
    val presentationId: String,
    val title: String,
    val message: String,
    val failed: Boolean = false,
    val phoneOrdinal: Int? = null,
    val phoneKind: String? = null,
    val phoneOutcome: NativeActionOutcome? = null,
    val phoneForegroundVerified: Boolean = false,
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
    val reviewRunId: String? = null,
    /** Step runs grouped under one durable phone-workflow card. */
    val relatedRunIds: Set<String> = emptySet(),
    val phoneTotal: Int? = null,
    val phoneConfirmed: Int? = null,
    val phoneGuidance: NativeWorkflowRecoveryGuide.Guidance? = null,
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
                        // A grouped run with only Started/Requested evidence is still running.
                        // A real error is sticky; an unobserved completion is never invented.
                        status = if (isError) ActivityStatus.FAILED else statusFor(sorted.last()),
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
        val roots = runs.filter { it.parentRunId == null &&
            com.halo.runtime.cognition.native.NativeWorkflowJournal.marker(it) == null &&
            com.halo.runtime.cognition.native.NativeWorkflowJournal.parentMarker(it) == null
        }.map { parent ->
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
        val workflows = NativePhoneWorkflowActivity.project(runs)
        ActivityUiState((history + roots + workflows).sortedByDescending { it.timestamp }.take(100))
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

    val semanticLabels = steps.filterIsInstance<AgentStep.SemanticAppOperationRecorded>().associate {
        it.receipt.operationId to it.receipt.semanticAction.value.replace('_', ' ')
    }
    fun actionLabel(name: String, callId: String): String = semanticLabels[callId]
        ?: if (name.startsWith("halo_appfn_")) "Current app action" else name
    val activitySteps = steps.map { step ->
        val title = RuntimeUiFormatters.formatStepTitle(step)
        val message = when (step) {
            is AgentStep.SemanticAppOperationRecorded -> step.receipt.let {
                "${it.targetPackage ?: "Current app"} · ${it.label()}\nRoute · ${it.route}\nDispatch · ${it.dispatchTruth}\nEffect · ${it.effectTruth}"
            }
            is AgentStep.ContextUsed -> "${step.objectIds.size} workspace reference(s) · ${step.memoryIds.size} memory reference(s)"
            is AgentStep.UiTransactionRecorded -> step.transaction.let {
                listOfNotNull(it.preview?.description, it.phase.name.lowercase().replace('_', ' '),
                    it.searchStatus?.let { status -> "Search ${status.name.lowercase().replace('_', ' ')}" },
                    it.receipt?.let { receipt -> "${receipt.actionTruth.name.lowercase()} · ${receipt.changeKind.replace('_', ' ')}" }).joinToString(" · ")
            }
            is AgentStep.ToolExecutionClaimed -> "${actionLabel(step.toolId, step.toolCallId)}: action started"
            is AgentStep.ToolOutcomeRecorded -> "${actionLabel(step.toolId, step.toolCallId)}: ${if (step.success) "completed" else "failed"} (${step.actionTruth.name.lowercase()})"
            is AgentStep.ResourceBlocked -> "${actionLabel(step.toolId, step.invocationId.value)}: waiting for ${step.resourceIds.joinToString { RuntimeUiFormatters.formatResource(
                ResourceId(it)
            ) }}"
            is AgentStep.ProviderTurnStarted -> "Provider turn started (${step.providerId})"
            is AgentStep.ProviderTurnCompleted -> step.assistantText.ifBlank { "Provider turn completed" }
            is AgentStep.InputRequested -> "Awaiting user input"
            is AgentStep.InputResolved -> "User input resolved (${step.resolution})"
            is AgentStep.ToolRejected -> "${actionLabel(step.toolId, step.toolCallId)}: rejected (${step.reasonCode.name.lowercase()})"
            is AgentStep.ToolFailed -> "${actionLabel(step.toolId, step.toolCallId)}: ${step.reason}"
            is AgentStep.ToolCompleted -> "${actionLabel(step.toolId, step.toolCallId)}: ${step.resultSummary}"
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
        reviewRunId = id.value.takeIf { pendingInput != null && state in setOf(AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION) },
    )
}

/** Follows AppLanguageProvider's Compose locale; the device's 12/24h locale is not authoritative. */
internal fun Instant.activityDisplayTime(locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(this)
