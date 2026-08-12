package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun AiSettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AI Settings", style = HaloTypography.displayMedium, color = HaloGlassColors.TextPrimary)
        
        Text(
            "Configure how Halo interacts with AI services.",
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary
        )

        HaloGlassSection(title = "PRIMARY PROVIDER") {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Phone Assistant", color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
                    Text("System default", color = HaloGlassColors.TextMuted, style = HaloTypography.labelMedium)
                }
                HaloGlassChip.Success(text = "Active")
            }
        }
        
        HaloGlassSection(title = "CAPABILITIES") {
            CapabilityRow("Text Reasoning", true)
            HaloGlassDivider()
            CapabilityRow("Image Understanding", true)
            HaloGlassDivider()
            CapabilityRow("Audio Processing", true)
            HaloGlassDivider()
            CapabilityRow("Streaming", false)
        }

        HaloGlassSection(title = "PRIVACY") {
            Text(
                "When using Phone Assistant, requests are processed by your system's default assistant app. Privacy is managed according to your system settings.",
                style = HaloTypography.labelMedium,
                color = HaloGlassColors.TextMuted
            )
        }
    }
}

@Composable
private fun CapabilityRow(name: String, supported: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
        if (supported) {
            Text("Supported", color = HaloGlassColors.Success, style = HaloTypography.labelMedium)
        } else {
            Text("Unavailable", color = HaloGlassColors.TextMuted, style = HaloTypography.labelMedium)
        }
    }
}
