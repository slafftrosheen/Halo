package com.halo.app.ui.screens.activity

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.size(16.dp))
        Text(
            text = "Activity",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "What happened, in order.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))

        if (uiState.events.isEmpty()) {
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
                items(uiState.events, key = { it.id }) { event ->
                    ActivityListItem(event)
                }
            }
        }
    }
}

@Composable
private fun ActivityListItem(event: ActivityEvent) {
    val signalKind = when {
        event.failed -> HaloSignalKind.ERROR
        event.speaker == ActivitySpeaker.User -> HaloSignalKind.USER
        event.speaker == ActivitySpeaker.System -> HaloSignalKind.DEVICE
        else -> HaloSignalKind.HALO
    }
    val title = when {
        event.failed -> "Failed"
        event.speaker == ActivitySpeaker.User -> "You"
        event.speaker == ActivitySpeaker.System -> "System"
        else -> "Halo"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = event.timestamp.formatTime(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(48.dp)
                .padding(top = 8.dp)
        )
        HaloSignalGlyph(
            kind = signalKind,
            size = 36.dp
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (event.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.size(3.dp))
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4
            )
        }
    }
}

enum class ActivitySpeaker(val label: String) {
    User("You"),
    Halo("Halo"),
    System("System")
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
            .filter { event ->
                event.title == "You" ||
                    event.title == "Halo" ||
                    event.title.startsWith("System")
            }
            .mapNotNull { event ->
                val message = event.description.trim()
                if (message.isEmpty()) return@mapNotNull null
                ActivityEvent(
                    id = event.id,
                    speaker = when (event.title) {
                        "You" -> ActivitySpeaker.User
                        "Halo" -> ActivitySpeaker.Halo
                        else -> ActivitySpeaker.System
                    },
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
