package com.halo.app.ui.screens.activity

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
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.TimelineEvent
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

@Composable
fun ActivityScreen(viewModel: ActivityViewModel, onOpenObject: (String) -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val filtered = uiState.events

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        HaloScreenHeader("Activity", "What Halo did")
        Spacer(Modifier.size(24.dp))

        if (filtered.isEmpty()) {
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
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                story.steps.forEach { step ->
                    Text("${step.title}: ${step.message}", style = MaterialTheme.typography.bodySmall,
                        color = if (step.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
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
enum class ActivityStatus { PENDING, SUCCESS, FAILED }

data class ActivityStory(
    val id: String,
    val correlationId: String?,
    val kind: ActivityKind,
    val title: String,
    val summary: String,
    val timestamp: Instant,
    val status: ActivityStatus,
    val steps: List<ActivityStep>,
    val objectId: String? = null
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
                            timestamp = event.timestamp,
                            status = statusFor(event),
                            steps = listOf(ActivityStep(event.presentationId, event.title, event.description, statusFor(event) == ActivityStatus.FAILED)),
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
                        timestamp = start.timestamp,
                        status = if (isError) ActivityStatus.FAILED else ActivityStatus.SUCCESS,
                        steps = sorted.map { ActivityStep(it.presentationId, it.title, it.description, statusFor(it) == ActivityStatus.FAILED) },
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
    private val mapper: ActivityUiMapper = ActivityUiMapper()
) : ViewModel() {
    val uiState: StateFlow<ActivityUiState> = snapshotManager.snapshot
        .map { snapshot -> mapper.map(snapshot.timeline.takeLast(500)) } // Process up to 500
        .map { state -> ActivityUiState(events = state.events.take(100)) } // Display recent 100
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityUiState()
        )
}

private val activityTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun Instant.formatTime(): String = activityTimeFormatter.format(this)
