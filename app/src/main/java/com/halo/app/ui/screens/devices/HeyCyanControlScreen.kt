package com.halo.app.ui.screens.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HeyCyanControlScreen(viewModel: DevicesViewModel, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    
    val device = uiState.devices.find { it.id == "heycyan_1" }
    val isConnected = device?.isConnected == true

    HaloGlassLazyPage(title = "") {
        item {
            HaloGlassBackHeader(
                title = "HeyCyan BW-AG1 Pro",
                onBack = onBack
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Connection",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Auto-connect", value = "ON")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Forget device", value = "")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Status", value = if (isConnected) "Connected" else "Offline")
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Camera",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Resolution", value = "1080p")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Video mode", value = "Standard")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Stabilisation", value = "Unsupported")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Photo/video behavior", value = "Auto")
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Controls",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "AI button", value = "Default")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Camera button", value = "Default")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Touch gestures", value = "Enabled")
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Audio",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Volume", value = "70%")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Microphone", value = "Active")
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Diagnostics",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Battery", value = device?.battery ?: "Unknown")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Firmware", value = device?.firmware ?: "Unknown")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Protocol", value = "HeyCyan SDK v1")
            }
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
