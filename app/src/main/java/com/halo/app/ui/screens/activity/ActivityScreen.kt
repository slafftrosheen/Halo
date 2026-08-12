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
import com.halo.app.ui.glass.material.HaloTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import com.halo.sdk.runtime.RuntimeSnapshotSource

@Composable
fun ActivityScreen(viewModel: ActivityViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Activity",
                style = HaloTypography.displayMedium,
                color = HaloGlassColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cognitive timeline and runtime events",
                style = HaloTypography.bodyMedium,
                color = HaloGlassColors.TextSecondary
            )
        }
        
        if (uiState.events.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No activity yet.",
                        color = HaloGlassColors.TextMuted,
                        style = HaloTypography.bodyLarge
                    )
                }
            }
        } else {
            items(uiState.events) { event ->
                ActivityEventItem(event)
            }
        }
        
        if (uiState.hasMore) {
            item {
                HaloGlassButton(
                    text = "Load More",
                    onClick = { viewModel.loadMore() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ActivityEventItem(event: ActivityEvent) {
    var expanded by remember { mutableStateOf(false) }

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
            
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HaloGlassDivider()
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "STATUS",
                    style = HaloTypography.labelSmall,
                    color = HaloGlassColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.status,
                    style = HaloTypography.bodyLarge,
                    color = if (event.status == "Success") HaloGlassColors.Success else HaloGlassColors.TextPrimary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("ID", style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(event.id.take(8), style = HaloTypography.monoSmall, color = HaloGlassColors.TextMuted)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.description,
                    color = HaloGlassColors.TextSecondary,
                    style = HaloTypography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}

data class ActivityEvent(
    val id: String,
    val command: String,
    val description: String,
    val timestamp: Long,
    val status: String
)

fun Long.format(): String {
    val secs = (this / 1000) % 60
    val mins = (this / 1000 / 60) % 60
    val hours = (this / 1000 / 60 / 60) % 24
    return String.format("%02d:%02d:%02d", hours, mins, secs)
}

data class ActivityUiState(
    val events: List<ActivityEvent> = emptyList(),
    val hasMore: Boolean = false
)

class ActivityViewModel(private val snapshotManager: RuntimeSnapshotSource) : ViewModel() {
    val uiState: StateFlow<ActivityUiState> = snapshotManager.snapshot
        .map { snapshot ->
            ActivityUiState(
                events = snapshot.timeline.map {
                    ActivityEvent(
                        id = it.id,
                        command = it.title,
                        description = it.description,
                        timestamp = it.timestamp.toEpochMilli(),
                        status = it.status
                    )
                }.sortedByDescending { it.timestamp },
                hasMore = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ActivityUiState()
        )
    
    fun loadMore() {
        // No-op for now, until pagination is supported in the timeline
    }
}
