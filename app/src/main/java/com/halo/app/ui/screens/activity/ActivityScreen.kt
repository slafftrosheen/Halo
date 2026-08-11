package com.halo.app.ui.screens.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun ActivityScreen(viewModel: ActivityViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Activity",
                style = HaloTypography.displayMedium,
                color = HaloGlassColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cognitive timeline and runtime events",
                style = HaloTypography.bodyMedium,
                color = HaloGlassColors.TextSecondary
            )
        }
        
        item {
            HaloGlassSection(
                title = "Recent Events",
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.events.isEmpty()) {
                    Text(
                        text = "No recent activity",
                        color = HaloGlassColors.TextSecondary,
                        style = HaloTypography.bodyMedium
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.events.forEach { event ->
                            ActivityEventItem(event)
                        }
                    }
                }
            }
        }
        
        if (uiState.hasMore) {
            item {
                HaloGlassCard(
                    onClick = { viewModel.loadMore() }
                ) {
                    Text(
                        text = "Load More",
                        color = HaloGlassColors.Accent,
                        style = HaloTypography.labelLarge,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityEventItem(event: ActivityEvent) {
    HaloGlassCard(
        onClick = { /* TODO: Handle event click */ }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = event.command,
                    color = HaloGlassColors.TextPrimary,
                    style = HaloTypography.bodyMedium
                )
                Text(
                    text = event.timestamp.format(),
                    color = HaloGlassColors.TextSecondary,
                    style = HaloTypography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = event.description,
                color = HaloGlassColors.TextSecondary,
                style = HaloTypography.bodySmall
            )
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

class ActivityViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()
    fun loadMore() {}
}
