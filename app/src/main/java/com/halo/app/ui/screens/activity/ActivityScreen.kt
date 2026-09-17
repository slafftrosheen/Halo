package com.halo.app.ui.screens.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloMarkdownText
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.runtime.RuntimeSnapshotSource
import com.halo.sdk.runtime.TimelineEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Composable
fun ActivityScreen(viewModel: ActivityViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    HaloGlassLazyPage(title = "Activity") {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    text = "Recent conversations and actions",
                    style = HaloTypography.bodyLarge,
                    color = HaloGlassColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "What you asked and what Halo returned.",
                    style = HaloTypography.bodySmall,
                    color = HaloGlassColors.TextMuted
                )
            }
        }

        if (uiState.events.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nothing here yet",
                            style = HaloTypography.bodyLarge,
                            color = HaloGlassColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Talk to Halo or type a request and it will appear here.",
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.TextMuted
                        )
                    }
                }
            }
        } else {
            items(
                items = uiState.events,
                key = { it.id }
            ) { event ->
                ActivityTurnCard(event)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ActivityTurnCard(event: ActivityEvent) {
    val speakerColor = when {
        event.failed -> HaloGlassColors.Danger
        event.speaker == ActivitySpeaker.Halo -> HaloGlassColors.Accent
        else -> HaloGlassColors.TextSecondary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = event.speaker.label,
                style = HaloTypography.labelMedium,
                color = speakerColor
            )
            Text(
                text = event.timestamp.formatTime(),
                style = HaloTypography.labelSmall,
                color = HaloGlassColors.TextMuted
            )
        }

        HaloGlassCard {
            if (event.speaker == ActivitySpeaker.Halo && !event.failed) {
                HaloMarkdownText(
                    markdown = event.message,
                    textColor = HaloGlassColors.TextPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = event.message,
                    style = HaloTypography.bodyMedium,
                    color = if (event.failed) HaloGlassColors.Danger else HaloGlassColors.TextPrimary
                )
            }
        }
    }
}

enum class ActivitySpeaker(val label: String) {
    User("You"),
    Halo("Halo")
}

data class ActivityEvent(
    val id: String,
    val speaker: ActivitySpeaker,
    val message: String,
    val timestamp: Long,
    val failed: Boolean = false
)

data class ActivityUiState(
    val events: List<ActivityEvent> = emptyList()
)

class ActivityUiMapper {
    fun map(timeline: List<TimelineEvent>): ActivityUiState {
        val events = timeline.asSequence()
            .filter { event -> event.title == "You" || event.title == "Halo" }
            .mapNotNull { event ->
                val message = event.description.trim()
                if (message.isEmpty()) return@mapNotNull null

                ActivityEvent(
                    id = event.id,
                    speaker = if (event.title == "You") ActivitySpeaker.User else ActivitySpeaker.Halo,
                    message = message,
                    timestamp = event.timestamp.toEpochMilli(),
                    failed = event.status.equals("Failed", ignoreCase = true) ||
                        event.status.equals("Error", ignoreCase = true)
                )
            }
            .sortedByDescending { it.timestamp }
            .toList()

        return ActivityUiState(events = events)
    }
}

class ActivityViewModel(
    snapshotManager: RuntimeSnapshotSource,
    private val mapper: ActivityUiMapper = ActivityUiMapper()
) : ViewModel() {
    val uiState: StateFlow<ActivityUiState> = snapshotManager.snapshot
        .map { snapshot -> mapper.map(snapshot.timeline) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ActivityUiState()
        )
}

private fun Long.formatTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
