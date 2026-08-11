package com.halo.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.HaloStateOrb
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "HALO", color = HaloGlassColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            HaloGlassChip.Success(text = "Ready")
        }

        // State Orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            HaloStateOrb(state = uiState.runtimeState, size = 120.dp)
        }

        // Recent Events Section
        HaloGlassSection(
            title = "Recent Events",
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.recentEvents.isEmpty()) {
                Text("No recent activity.", color = HaloGlassColors.TextSecondary, style = HaloTypography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.recentEvents.forEach { event ->
                        TimelineEventCard(event)
                    }
                }
            }
        }

        // Runtime State
        HaloGlassSection(
            title = "Runtime",
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("State: ${uiState.runtimeState.name}", color = HaloGlassColors.TextPrimary)
        }
    }
}

@Composable
private fun TimelineEventCard(event: TimelinePreviewEvent) {
    HaloGlassCard(onClick = { }) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(event.command, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
                Text(event.status, color = HaloGlassColors.TextSecondary, style = HaloTypography.labelMedium)
            }
        }
    }
}
