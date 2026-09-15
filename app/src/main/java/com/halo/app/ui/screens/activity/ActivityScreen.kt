package com.halo.app.ui.screens.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import com.halo.sdk.runtime.RuntimeSnapshotSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(viewModel: ActivityViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    HaloGlassLazyPage(title = "Activity") {
        // Intro / context copy
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = "Cognitive timeline and runtime events",
                    style = HaloTypography.bodyMedium,
                    color = HaloGlassColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Intents, routines and system operations executed by Halo.",
                    style = HaloTypography.bodySmall,
                    color = HaloGlassColors.TextMuted
                )
            }
        }

        // Summary metrics bar
        item {
            ActivitySummaryBar(uiState = uiState)
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Filter chips
        item {
            ActivityFilterBar(
                filter = uiState.filter,
                hasFailures = uiState.failedCount > 0,
                onFilterSelected = viewModel::selectFilter
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.events.isEmpty()) {
            // Empty state
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (uiState.totalCount == 0) "No activity yet." else "Nothing matches this filter.",
                            color = HaloGlassColors.TextMuted,
                            style = HaloTypography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.totalCount == 0) {
                                "Your cognitive timeline will appear once Halo runs intents and routines."
                            } else {
                                "${uiState.totalCount} intents recorded — switch the filter to see them."
                            },
                            color = HaloGlassColors.TextSecondary,
                            style = HaloTypography.bodySmall
                        )
                    }
                }
            }
        } else {
            // Timeline spine + cards
            items(uiState.events) { event ->
                ActivityTimelineItem(event = event)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ActivitySummaryBar(uiState: ActivityUiState) {
    HaloGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Timeline overview",
                    style = HaloTypography.labelSmall,
                    color = HaloGlassColors.TextMuted
                )
                Text(
                    text = "${uiState.events.size} of ${uiState.totalCount} intents shown",
                    style = HaloTypography.bodyMedium,
                    color = HaloGlassColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SummaryMetricRow(
                    label = "Successful",
                    value = uiState.successCount.toString(),
                    color = HaloGlassColors.Success
                )
                SummaryMetricRow(
                    label = "Failed",
                    value = uiState.failedCount.toString(),
                    color = if (uiState.failedCount > 0) HaloGlassColors.Danger else HaloGlassColors.TextMuted
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = HaloTypography.bodySmall,
            color = HaloGlassColors.TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, HaloGlassTokens.circleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                style = HaloTypography.bodyMedium,
                color = HaloGlassColors.TextPrimary
            )
        }
    }
}

@Composable
private fun ActivityFilterBar(
    filter: ActivityFilter,
    hasFailures: Boolean,
    onFilterSelected: (ActivityFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            label = "All",
            selected = filter == ActivityFilter.All,
            tint = HaloGlassColors.Accent,
            onClick = { onFilterSelected(ActivityFilter.All) }
        )
        FilterChip(
            label = "Success",
            selected = filter == ActivityFilter.Success,
            tint = HaloGlassColors.Success,
            onClick = { onFilterSelected(ActivityFilter.Success) }
        )
        if (hasFailures) {
            FilterChip(
                label = "Failed",
                selected = filter == ActivityFilter.Failed,
                tint = HaloGlassColors.Danger,
                onClick = { onFilterSelected(ActivityFilter.Failed) }
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    HaloGlassChip(
        modifier = Modifier.clickable(onClick = onClick),
        text = label,
        material = HaloGlassMaterials.Interactive.copy(
            containerColor = if (selected) tint.copy(alpha = 0.20f) else HaloGlassColors.Glass,
            contentColor = if (selected) tint else HaloGlassColors.TextSecondary
        )
    )
}

@Composable
private fun ActivityTimelineItem(event: ActivityEvent) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timeline spine + node
        Column(
            modifier = Modifier
                .width(36.dp)
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Node
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = statusColor(event.status),
                        shape = HaloGlassTokens.circleShape
                    )
            )
            // Spine segment beneath node
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(80.dp)
                    .background(HaloGlassColors.Glass)
            )
        }

        // Event card
        HaloGlassCard(
            onClick = { expanded = !expanded }
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = event.command,
                        color = HaloGlassColors.TextPrimary,
                        style = HaloTypography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = event.timestamp.format(),
                        color = HaloGlassColors.TextSecondary,
                        style = HaloTypography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (!expanded) {
                    Text(
                        text = event.description,
                        color = HaloGlassColors.TextSecondary,
                        style = HaloTypography.bodySmall,
                        maxLines = 1
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    HaloGlassDivider()
                    Spacer(modifier = Modifier.height(12.dp))

                    if (event.subEvents.isNotEmpty()) {
                        Text(
                            text = "TIMELINE",
                            style = HaloTypography.labelSmall,
                            color = HaloGlassColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        event.subEvents.forEach { subEvent ->
                            SubEventRow(subEvent)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Text(
                            text = "STATUS",
                            style = HaloTypography.labelSmall,
                            color = HaloGlassColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = event.status,
                            style = HaloTypography.bodyLarge,
                            color = if (isSuccess(event.status))
                                HaloGlassColors.Success
                            else HaloGlassColors.TextPrimary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "DESCRIPTION",
                            style = HaloTypography.labelSmall,
                            color = HaloGlassColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = event.description,
                            color = HaloGlassColors.TextPrimary,
                            style = HaloTypography.bodyMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.status.uppercase(),
                            style = HaloTypography.labelSmall,
                            color = statusColor(event.status)
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "ID",
                                style = HaloTypography.labelSmall,
                                color = HaloGlassColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                event.id.take(8),
                                style = HaloTypography.monoSmall,
                                color = HaloGlassColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubEventRow(subEvent: ActivityEvent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subEvent.command,
                color = HaloGlassColors.TextPrimary,
                style = HaloTypography.bodySmall
            )
            Text(
                text = subEvent.timestamp.format(),
                color = HaloGlassColors.TextSecondary,
                style = HaloTypography.labelSmall
            )
        }
        if (subEvent.description.isNotBlank()) {
            Text(
                text = subEvent.description,
                color = HaloGlassColors.TextSecondary,
                style = HaloTypography.labelSmall,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

data class ActivityEvent(
    val id: String,
    val command: String,
    val description: String,
    val timestamp: Long,
    val status: String,
    val subEvents: List<ActivityEvent> = emptyList()
)

/** Formats an epoch-millis timestamp in the device's local time zone. */
private fun Long.format(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(this))

private fun isSuccess(status: String): Boolean = status.equals("Success", ignoreCase = true)

private fun isFailure(status: String): Boolean =
    status.equals("Failed", ignoreCase = true) || status.equals("Error", ignoreCase = true)

private fun statusColor(status: String): Color = when {
    isSuccess(status) -> HaloGlassColors.Success
    isFailure(status) -> HaloGlassColors.Danger
    else -> HaloGlassColors.Info
}

enum class ActivityFilter {
    All,
    Success,
    Failed;

    fun matches(status: String): Boolean = when (this) {
        All -> true
        Success -> isSuccess(status)
        Failed -> isFailure(status)
    }
}

data class ActivityUiState(
    val events: List<ActivityEvent> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.All,
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failedCount: Int = 0
)

class ActivityViewModel(private val snapshotManager: RuntimeSnapshotSource) : ViewModel() {

    private val _filter = MutableStateFlow(ActivityFilter.All)

    val uiState: StateFlow<ActivityUiState> = combine(
        snapshotManager.snapshot,
        _filter
    ) { snapshot, filter ->
        // Group timeline events by intentId, falling back to id for ungrouped events
        val groupedEvents = snapshot.timeline
            .groupBy { it.intentId ?: it.id }
            .map { (groupId, events) ->
                // Sort events in this group chronologically
                val sortedEvents = events.sortedBy { it.timestamp }
                val firstEvent = sortedEvents.first()
                val lastEvent = sortedEvents.last()

                val title = if (events.size > 1 && sortedEvents.any { it.title == "Intent Created" }) {
                    "Intent: " + sortedEvents.find { it.title == "Intent Created" }?.description?.substringBefore(" [")
                } else {
                    firstEvent.title
                }

                val description = if (events.size > 1) {
                    "${events.size} operations • Final state: ${lastEvent.status}"
                } else {
                    firstEvent.description
                }

                ActivityEvent(
                    id = groupId,
                    command = title,
                    description = description,
                    timestamp = lastEvent.timestamp.toEpochMilli(),
                    status = lastEvent.status,
                    subEvents = sortedEvents.map {
                        ActivityEvent(
                            id = it.id,
                            command = it.title,
                            description = it.description,
                            timestamp = it.timestamp.toEpochMilli(),
                            status = it.status,
                            subEvents = emptyList()
                        )
                    }
                )
            }.sortedByDescending { it.timestamp }

        ActivityUiState(
            events = groupedEvents.filter { filter.matches(it.status) },
            filter = filter,
            totalCount = groupedEvents.size,
            successCount = groupedEvents.count { isSuccess(it.status) },
            failedCount = groupedEvents.count { isFailure(it.status) }
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ActivityUiState()
        )

    fun selectFilter(filter: ActivityFilter) {
        _filter.value = filter
    }
}
