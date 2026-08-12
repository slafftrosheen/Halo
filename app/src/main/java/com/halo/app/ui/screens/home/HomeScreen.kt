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
            .padding(24.dp)
            .padding(bottom = 80.dp) // padding for navigation pill overlay
    ) {
        // TOP Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HALO", 
                style = HaloTypography.titleLarge,
                color = HaloGlassColors.TextPrimary,
                letterSpacing = 4.sp
            )
            
            Text(
                text = uiState.connectionState,
                style = HaloTypography.labelMedium,
                color = if (uiState.connectionState == "Connected") HaloGlassColors.Accent else HaloGlassColors.TextMuted
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        // CENTER Action: State Orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
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

        Spacer(modifier = Modifier.height(24.dp))
        
        // Contextual Text
        Text(
            text = uiState.contextualText,
            style = HaloTypography.bodyLarge,
            color = HaloGlassColors.TextPrimary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(48.dp))
        
        // Capabilities Row (Compact)
        val filteredCapabilities = uiState.capabilities.filter { it != "Not reported" }
        if (filteredCapabilities.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                filteredCapabilities.forEach { cap ->
                    HaloGlassChip(
                        text = cap,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // AI Status
        HaloGlassSection(title = "Intelligence") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("AI Provider", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                    Text(uiState.activeProvider, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                }
                HaloGlassChip(
                    text = uiState.activeProviderStatus,
                    material = when (uiState.activeProviderStatus) {
                        "READY" -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Success.copy(alpha = 0.2f),
                            contentColor = HaloGlassColors.Success
                        )
                        "HANDOFF" -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Accent.copy(alpha = 0.2f),
                            contentColor = HaloGlassColors.Accent
                        )
                        else -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Danger.copy(alpha = 0.2f),
                            contentColor = HaloGlassColors.Danger
                        )
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Device Status
        HaloGlassSection(title = "Device Status") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Connection", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                Text(uiState.connectionState, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
            }
            if (uiState.connectionState == "Connected") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Battery", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                    val batteryText = uiState.batteryPercent?.let { "$it%" } ?: "Unknown"
                    val chargeText = if (uiState.isCharging) " (Charging)" else ""
                    Text("$batteryText$chargeText", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Recent Activity
        HaloGlassSection(title = "Activity") {
            if (uiState.recentEvents.isEmpty()) {
                Text(
                    text = "No activity yet.",
                    style = HaloTypography.bodyMedium,
                    color = HaloGlassColors.TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                uiState.recentEvents.take(3).forEach { event ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(event.command, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                        Text(event.status, style = HaloTypography.labelMedium, color = HaloGlassColors.TextMuted)
                    }
                }
            }
        }
    }
}
