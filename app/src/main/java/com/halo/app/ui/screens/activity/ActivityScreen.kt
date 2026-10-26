package com.halo.app.ui.screens.activity

import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
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
    var showActivityDetails by rememberSaveable { mutableStateOf(false) }
    BindShellPageActions(ShellPageOwner("activity", sourceIdentity = "${filter.name}:$focusRunId"), listOf(
        ShellPageAction("page.activity.details", "Details", HaloGlyph.QUESTION) { showActivityDetails = true },
        ShellPageAction("page.activity.review", "Show requests", HaloGlyph.APPROVAL, filter != ActivityFilter.REVIEW) { selectedFilter = ActivityFilter.REVIEW },
        ShellPageAction("page.activity.history", "Show completed", HaloGlyph.ACTIVITY, filter != ActivityFilter.HISTORY) { selectedFilter = ActivityFilter.HISTORY },
    ))
    if (showActivityDetails) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showActivityDetails = false },
        title = { Text(uiText("Activity")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Everything halo did, in plain words."))
            Text(uiText("Approvals and answers wait here until you choose what happens next."))
        } },
        confirmButton = { TextButton(onClick = { showActivityDetails = false }) { Text(uiText("Done")) } },
    )

    val activityUtility: @Composable () -> Unit = {
        ActivitySegmentedPill(selected = filter,
            counts = mapOf(ActivityFilter.REVIEW to reviewCount, ActivityFilter.RUNNING to runningCount, ActivityFilter.HISTORY to doneCount),
            onSelect = { selectedFilter = it })
    }
    val hostedUtility = com.halo.app.ui.presence.BindShellUtilityBand(ShellPageOwner("activity"), activityUtility)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.halo.app.ui.theme.haloWorkspaceBackground())
            .padding(horizontal = 20.dp)
    ) {
        if (!hostedUtility) {
            HaloScreenHeader(uiText("Activity"), trailing = { ActivityOverflowMenu { showActivityDetails = true } })
            activityUtility()
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
                    ActivityFilter.HISTORY -> "Completed"
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
    var showEveryStep by remember(story.id) { mutableStateOf(false) }
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
                        story.suspended -> HaloGlyph.STOP
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
                story.speechProvenance?.let { Text(uiText("Speech recognition"), style = MaterialTheme.typography.bodyMedium) }
                // The journal may contain dozens of diagnostic steps. Present
                // the first five in a compact timeline; all evidence remains
                // available explicitly, without inventing an action plan.
                if (story.steps.isNotEmpty()) {
                    Text(uiText("Recorded steps"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val displayed = activityVisibleRecordedSteps(story.steps, showEveryStep)
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        displayed.forEachIndexed { index, step ->
                            if (index > 0) HorizontalDivider(
                                Modifier.padding(start = 24.dp),
                                color = MaterialTheme.colorScheme.outlineVariant)
                            ActivityRecordedStepRow(
                                step = step,
                                phoneTotal = story.phoneTotal,
                            )
                        }
                    }
                    if (story.steps.size > displayed.size) {
                        TextButton(
                            onClick = { showEveryStep = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(uiText("Show all %1\$d recorded steps", story.steps.size)) }
                    } else if (showEveryStep && story.steps.size > 5) {
                        TextButton(
                            onClick = { showEveryStep = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(uiText("Show fewer steps")) }
                    }
                }
                story.children.forEach { child -> ActivityListItem(child, onOpenObject, onReviewRun, onHome) }
                story.sourceRunId?.let { runId -> ContextSourcesView(runId, ManualDependencyGraph.contextSourceReceipts, onOpenObject) }
                story.objectId?.let { id -> HaloActionButton(onClick = { onOpenObject(id) },
                    style = HaloActionStyle.SECONDARY) { Text(uiText("Open in Workspace")) } }
            }
        }
    }
}

/** Journal step order is authoritative. Never number general runtime
 * events as a supposed approved multi-action plan.
 */
internal fun activityVisibleRecordedSteps(
    steps: List<ActivityStep>,
    showAll: Boolean,
): List<ActivityStep> = if (showAll) steps else steps.take(5)

@Composable
private fun ActivityRecordedStepRow(step: ActivityStep, phoneTotal: Int?) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            if (!step.failed && step.message.endsWith(": Confirmed"))
                com.halo.app.ui.fx.HaloPackageFx(com.halo.app.ui.fx.HaloPackageMoment.STEP_DONE, Modifier.size(20.dp), enabled = false)
            else HaloIcon(if (step.failed) HaloGlyph.ERROR else HaloGlyph.ACTIVITY,
                contentDescription = null, modifier = Modifier.size(17.dp),
                tint = if (step.failed) scheme.error else scheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val title = if (phoneTotal != null && step.phoneOrdinal != null) {
                stringResource(R.string.halo_phone_step, step.phoneOrdinal,
                    phoneTotal, stringResource(phoneKindResource(step.phoneKind)))
            } else step.title
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (phoneTotal != null && step.phoneForegroundVerified)
                    stringResource(R.string.halo_phone_foreground_verified)
                else if (phoneTotal != null)
                    stringResource(phoneOutcomeResource(step.phoneOutcome))
                else step.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (step.failed) scheme.error else scheme.onSurfaceVariant,
            )
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
    story.suspended -> "Paused"
    story.status == ActivityStatus.INTERRUPTED -> "Interrupted"
    story.status == ActivityStatus.CANCELLED -> "Stopped"
    story.status == ActivityStatus.FAILED -> "Couldn’t finish"
    story.status == ActivityStatus.PENDING -> "In progress"
    else -> "Completed"
}

internal enum class ActivityFilter(val label: String) {
    ALL("All"), REVIEW("Needs you"), RUNNING("Running"), HISTORY("Completed")
}

/** Round 2 p.40: one grouped segmented surface, rather than three floating
 * Material filter chips. Counts come only from the durable Activity projection.
 * Large text may scroll instead of truncating a translated label.
 */
@Composable
internal fun ActivitySegmentedPill(
    selected: ActivityFilter,
    counts: Map<ActivityFilter, Int>,
    onSelect: (ActivityFilter) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surfaceContainerLow,
        shape = com.halo.app.ui.design.HaloShapes.pill,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().then(
                if (largeText) Modifier.horizontalScroll(rememberScrollState()) else Modifier
            ).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            listOf(ActivityFilter.REVIEW, ActivityFilter.RUNNING, ActivityFilter.HISTORY)
                .forEach { option ->
                    val selectedTab = option == selected
                    Surface(
                        onClick = { onSelect(option) },
                        modifier = (if (largeText) Modifier.widthIn(min = 118.dp)
                            else Modifier.weight(1f)).heightIn(min = 48.dp),
                        shape = com.halo.app.ui.design.HaloShapes.pill,
                        color = if (selectedTab) colors.primaryContainer.copy(alpha = 0.70f)
                            else androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center) {
                            Text(uiText(option.label),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTab) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab) colors.onPrimaryContainer
                                    else colors.onSurfaceVariant,
                                maxLines = 2)
                            val count = counts[option].orEmptyCount()
                            if (count > 0) {
                                Spacer(Modifier.width(4.dp))
                                Text(count.toString(), style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedTab) colors.onPrimaryContainer
                                    else colors.onSurfaceVariant)
                            }
                        }
                    }
                }
        }
    }
}

private fun Int?.orEmptyCount(): Int = this?.coerceAtLeast(0) ?: 0

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
    /** Durable AgentRunState.SUSPENDED, not a guessed paused timestamp. */
    val suspended: Boolean = false,
    val sourceRunId: String? = null,
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
                            title = userTimelineTitle(event),
                            summary = userTimelineDescription(event),
                            speechProvenance = event.details["stt"],
                            timestamp = event.timestamp,
                            status = statusFor(event),
                            steps = listOf(ActivityStep(event.presentationId, userTimelineTitle(event),
                                userTimelineDescription(event), statusFor(event) == ActivityStatus.FAILED)),
                            objectId = event.objectId
                        )
                    )
                }
            } else {
                val sorted = events.sortedBy { it.timestamp }
                val start = sorted.first()
                val isError = sorted.any { statusFor(it) == ActivityStatus.FAILED }
                val unconfirmed = sorted.filter { it.details.containsKey("actionTruth") || it.status == "Returned" }
                    .groupBy { it.intentId ?: it.presentationId }.values
                    .any { statusFor(it.last()) == ActivityStatus.UNKNOWN }
                val lastMeaningful = sorted.lastOrNull { statusFor(it) != ActivityStatus.PENDING } ?: sorted.last()
                stories.add(
                    ActivityStory(
                        id = correlationId,
                        correlationId = correlationId,
                        kind = kindFor(start),
                        title = userTimelineTitle(start),
                        summary = userTimelineDescription(lastMeaningful),
                        speechProvenance = sorted.firstNotNullOfOrNull { it.details["stt"] },
                        timestamp = start.timestamp,
                        // A grouped run with only Started/Requested evidence is still running.
                        // A real error is sticky; an unobserved completion is never invented.
                        status = if (isError) ActivityStatus.FAILED else if (unconfirmed) ActivityStatus.UNKNOWN else statusFor(sorted.last()),
                        steps = sorted.map { ActivityStep(it.presentationId, userTimelineTitle(it),
                            userTimelineDescription(it), statusFor(it) == ActivityStatus.FAILED) },
                        objectId = sorted.firstNotNullOfOrNull { it.objectId }
                    )
                )
            }
        }
        val sortedStories = stories.sortedByDescending { it.timestamp }
        return ActivityUiState(events = sortedStories)
    }

    /**
     * The timeline is also diagnostic evidence. Activity is not a log viewer: raw details,
     * provider/model names, execution IDs and backend errors stay out of the user surface.
     * Keep the journal intact for an explicit Advanced technical report.
     */
    private fun userTimelineTitle(event: TimelineEvent): String = when {
        event.title.startsWith("halo_") -> "App action"
        event.title.equals("You", ignoreCase = true) -> "You"
        event.title.startsWith("Device", ignoreCase = true) -> "Device update"
        event.title.contains("Permission", ignoreCase = true) -> "Permission update"
        event.title.contains("Speech", ignoreCase = true) -> "Voice activity"
        event.title.contains("Provider", ignoreCase = true) ||
            event.title.contains("Model", ignoreCase = true) ||
            event.title.contains("Backend", ignoreCase = true) ||
            event.title.contains("Runtime", ignoreCase = true) ||
            event.title.contains("Diagnostic", ignoreCase = true) ||
            event.title.equals("State Changed", ignoreCase = true) -> "halo activity"
        else -> event.title
    }

    private fun userTimelineDescription(event: TimelineEvent): String {
        if (statusFor(event) == ActivityStatus.UNKNOWN) return "Delivery not confirmed"
        if (event.details["actionTruth"] == "CONFIRMED") return "Confirmed"
        val original = event.description
        val hasTechnicalDetails = event.details.isNotEmpty() ||
            Regex("(?i)\\b(provider|backend|endpoint|model id|token|quantization|runtime|diagnostic)\\b")
                .containsMatchIn(event.title + " " + original) ||
            Regex("\\b[0-9a-fA-F]{24,}\\b").containsMatchIn(original)
        if (!hasTechnicalDetails) return original
        return when (statusFor(event)) {
            ActivityStatus.PENDING -> "In progress"
            ActivityStatus.FAILED -> "Could not finish this step"
            else -> "Activity recorded"
        }
    }

    private fun kindFor(event: TimelineEvent): ActivityKind = when {
        statusFor(event) == ActivityStatus.FAILED -> ActivityKind.ERROR
        event.title == "You" -> ActivityKind.USER
        event.title.startsWith("Device") || event.title.startsWith("Capabilities") || event.title == "State Changed" -> ActivityKind.SYSTEM
        else -> ActivityKind.HALO
    }

    private fun statusFor(event: TimelineEvent): ActivityStatus = when {
        event.status.equals("Failed", ignoreCase = true) || event.status.equals("Error", ignoreCase = true) || event.status.equals("Rejected", ignoreCase = true) -> ActivityStatus.FAILED
        event.details["actionTruth"] == "CONFIRMED" -> ActivityStatus.SUCCESS
        event.details["actionTruth"] in setOf("DISPATCHED", "UNKNOWN") || event.status == "Returned" -> ActivityStatus.UNKNOWN
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
                    summary = "${RuntimeUiFormatters.formatRunState(child.state)} · " +
                        "${child.steps.filterIsInstance<AgentStep.ToolOutcomeRecorded>().size} actions recorded · " +
                        "${child.steps.filterIsInstance<AgentStep.DelegationResultRecorded>().lastOrNull()?.evidence?.size ?: 0} sources")
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

internal fun activityHasUnconfirmedReceipts(steps: List<AgentStep>): Boolean {
    val receipts = mutableMapOf<String, ActionTruth>()
    steps.forEach { step -> when (step) {
        is AgentStep.ToolOutcomeRecorded -> receipts[step.toolCallId] = step.actionTruth
        is AgentStep.ToolCompleted -> receipts[step.toolCallId] = step.actionTruth
        is AgentStep.ToolFailed -> receipts[step.toolCallId] = ActionTruth.FAILED
        is AgentStep.ToolRejected -> receipts[step.toolCallId] = ActionTruth.FAILED
        else -> Unit
    } }
    return receipts.values.any { it == ActionTruth.DISPATCHED || it == ActionTruth.UNKNOWN }
}

/** A stop belongs to the latest execution attempt, not every later recovery of the same run.
 * A response/result recorded after the stop is not by itself proof that execution resumed. */
internal fun activityCurrentGuardStop(steps: List<AgentStep>): AgentStep.RunGuardTriggered? {
    val index = steps.indexOfLast { it is AgentStep.RunGuardTriggered }
    if (index < 0) return null
    val resumed = steps.drop(index + 1).any { it is AgentStep.RecoveryClaimed ||
        it is AgentStep.ProviderTurnStarted || it is AgentStep.ToolExecutionClaimed ||
        it is AgentStep.ToolDispatched || it is AgentStep.PlanCreated }
    return if (resumed) null else steps[index] as AgentStep.RunGuardTriggered
}

internal fun AgentRun.toStory(): ActivityStory {
    val guardStop = activityCurrentGuardStop(steps)
    val unconfirmed = hasUnknownAction || activityHasUnconfirmedReceipts(steps)
    val status = when {
        unconfirmed || steps.filterIsInstance<AgentStep.UiTransactionRecorded>().any {
            it.transaction.receipt?.actionTruth in setOf(ActionTruth.DISPATCHED, ActionTruth.UNKNOWN)
        } -> ActivityStatus.UNKNOWN
        state == AgentRunState.INTERRUPTED -> ActivityStatus.INTERRUPTED
        state == AgentRunState.FAILED -> ActivityStatus.FAILED
        state == AgentRunState.CANCELLED -> ActivityStatus.CANCELLED
        state == AgentRunState.COMPLETED && guardStop != null -> ActivityStatus.CANCELLED
        state == AgentRunState.COMPLETED -> ActivityStatus.SUCCESS
        else -> ActivityStatus.PENDING
    }
    val pendingInput = steps.filterIsInstance<AgentStep.InputRequested>()
        .lastOrNull { requested -> steps.none { it is AgentStep.InputResolved && it.requestId == requested.requestId } }?.input
    val waitingLabel = if (state.isTerminal) null else when (pendingInput) {
        is com.halo.runtime.cognition.agent.AgentInputRequest.Approval -> "Waiting for your approval"
        is com.halo.runtime.cognition.agent.AgentInputRequest.Clarification -> "Waiting for your answer: " + pendingInput.question
        null -> null
    }
    val formattedState = RuntimeUiFormatters.formatRunState(state)
    /*
     * MINING-P1-001: a deliberately stopped run reads as "Task stopped · <reason>", not as a runtime
     * failure. The summary is content-free by construction (counts, digests, tool ids only).
     */
    val summaryText = listOfNotNull(
        "Stopped safely".takeIf { guardStop != null } ?: (waitingLabel ?: formattedState),
        "Delivery not confirmed".takeIf { unconfirmed },
        "Something went wrong".takeIf { failureReason != null },
    )
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    val semanticLabels = steps.filterIsInstance<AgentStep.SemanticAppOperationRecorded>().associate {
        it.receipt.operationId to it.receipt.semanticAction.value.replace('_', ' ')
    }
    fun actionLabel(name: String, callId: String): String = semanticLabels[callId]
        ?: when {
            name.startsWith("halo_appfn_") -> "Current app action"
            name.contains("calendar") -> "Calendar"
            name.contains("message") || name.contains("send") -> "Messages"
            name.contains("search") -> "Search"
            name.contains("timer") || name.contains("alarm") -> "Clock"
            name.contains("note") -> "Workspace"
            name.startsWith("halo_ui_") -> "Phone action"
            else -> "App action"
        }
    val activitySteps = steps.map { step ->
        val title = userStepTitle(step)
        val message = when (step) {
            is AgentStep.SemanticAppOperationRecorded -> "Used an action in the current app"
            is AgentStep.ContextUsed -> "Used your selected sources and approved memories"
            is AgentStep.UiTransactionRecorded -> "Worked with the current screen"
            is AgentStep.ToolExecutionClaimed -> "${actionLabel(step.toolId, step.toolCallId)}: started"
            is AgentStep.ToolOutcomeRecorded -> "${actionLabel(step.toolId, step.toolCallId)}: " + when {
                !step.success || step.actionTruth == ActionTruth.FAILED -> "Could not finish"
                step.actionTruth == ActionTruth.CONFIRMED -> "Confirmed"
                else -> "Delivery not confirmed"
            }
            is AgentStep.ResourceBlocked -> "Waiting for another task to finish"
            is AgentStep.ProviderTurnStarted -> "Preparing your answer"
            is AgentStep.ProviderTurnCompleted -> "Your answer is ready"
            is AgentStep.InputRequested -> "Waiting for you"
            is AgentStep.InputResolved -> "Your choice was received"
            is AgentStep.ToolRejected -> "This action was not allowed. Nothing was changed."
            is AgentStep.ToolFailed -> "This action could not finish. Review it before trying again."
            is AgentStep.ToolCompleted -> "${actionLabel(step.toolId, step.toolCallId)}: " + when {
                !step.success || step.actionTruth == ActionTruth.FAILED -> "Could not finish"
                step.actionTruth == ActionTruth.CONFIRMED -> "Confirmed"
                else -> "Delivery not confirmed"
            }
            is AgentStep.RunGuardTriggered -> "Stopped to keep your request within its limits"
            else -> userStepTitle(step)
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
        com.halo.runtime.cognition.interaction.PlanPresentationProjector.progressLine(steps)?.let { progress ->
            // Historical pending input is not an actionable approval after a canonical terminal state.
            if (state.isTerminal) progress.substringBefore(" · Waiting for approval") else progress
        }
    } else null

    return ActivityStory(
        id = "agent:${id.value}",
        correlationId = interactionId.value,
        kind = ActivityKind.HALO,
        title = request,
        summary = com.halo.runtime.cognition.ui.UiWorkflowProjector.summary(this) ?: summaryText,
        timestamp = createdAt,
        status = status,
        suspended = state == AgentRunState.SUSPENDED,
        steps = activitySteps,
        sourceRunId = id.value,
        planPresentation = planPresentation,
        planProgress = planProgress,
        reviewRunId = id.value.takeIf { pendingInput != null && state in setOf(AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION) },
    )
}

/** Follows AppLanguageProvider's Compose locale; the device's 12/24h locale is not authoritative. */
internal fun Instant.activityDisplayTime(locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(this)

/** Fixed user copy; journal types, IDs and provider metadata remain in Advanced. */
private fun userStepTitle(step: AgentStep): String = when (step) {
    is AgentStep.ContextUsed -> "Looked at your sources"
    is AgentStep.PlanCreated -> "Made a plan"
    is AgentStep.ToolRequested, is AgentStep.ToolInvocationPrepared -> "Prepared an action"
    is AgentStep.ToolExecutionClaimed, is AgentStep.ToolDispatched -> "Started an action"
    is AgentStep.ToolOutcomeRecorded, is AgentStep.ToolCompleted -> "Action result"
    is AgentStep.ToolFailed, is AgentStep.ToolRejected -> "Could not complete an action"
    is AgentStep.InputRequested, is AgentStep.ApprovalRequested -> "Asked you first"
    is AgentStep.InputResolved -> "Received your choice"
    is AgentStep.ProviderTurnStarted -> "Thought about your request"
    is AgentStep.ProviderTurnCompleted, is AgentStep.ResponseProduced -> "Prepared an answer"
    is AgentStep.ResponseCommitted -> "Saved your answer"
    is AgentStep.ResourceBlocked -> "Waiting for another task"
    is AgentStep.RunGuardTriggered -> "Stopped safely"
    is AgentStep.UiTransactionRecorded -> "Worked with your screen"
    is AgentStep.SemanticAppOperationRecorded -> "Used an app action"
    else -> "Updated your task"
}
