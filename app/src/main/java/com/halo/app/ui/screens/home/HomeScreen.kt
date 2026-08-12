package com.halo.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HALO", 
                style = HaloTypography.displayMedium,
                color = HaloGlassColors.TextPrimary
            )
            HaloGlassChip.Success(text = if (uiState.isDeviceConnected) "Connected" else "Standalone")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.systemStatus,
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary
        )

        // Primary Action: State Orb / Floating Mic
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            HaloFloatingMic(
                state = uiState.runtimeState,
                onClick = { 
                    if (uiState.runtimeState == com.halo.app.ui.glass.material.HaloRuntimeState.Idle) {
                        viewModel.startListening()
                    } else {
                        viewModel.stopListening()
                    }
                }
            )
        }

        // Quick Status Section
        HaloGlassSection(title = "Quick Status") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                HaloGlassMetric(
                    label = "Battery",
                    value = uiState.batteryPercent?.let { "$it%" } ?: "--",
                    modifier = Modifier.weight(1f)
                )
                HaloGlassDivider(modifier = Modifier.height(40.dp).width(1.dp))
                HaloGlassMetric(
                    label = "Provider",
                    value = uiState.activeProvider,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Recent Activity Section
        HaloGlassSection(title = "Recent Activity") {
            if (uiState.recentEvents.isEmpty()) {
                Text(
                    text = "No recent interactions.", 
                    color = HaloGlassColors.TextSecondary, 
                    style = HaloTypography.bodyMedium,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    uiState.recentEvents.take(3).forEach { event ->
                        TimelineEventCard(event)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEventCard(event: TimelinePreviewEvent) {
    HaloGlassCard(onClick = { }) {
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
                    fontWeight = FontWeight.Medium
                )
                HaloGlassChip.Info(text = event.status)
            }
        }
    }
}
