package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

/** System settings only; provider/model configuration lives in Intelligence. */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    HaloGlassPage(title = "Settings") {
        HaloGlassSection(title = "HALO") {
            HaloGlassSettingRow(
                title = "Intelligence",
                trailingText = uiState.provider,
                onClick = { viewModel.openIntelligence() }
            )
            HaloGlassDivider()
            HaloGlassSettingRow(
                title = "Devices",
                trailingText = uiState.deviceName,
                onClick = { viewModel.openDevices() }
            )
            HaloGlassDivider()
            HaloGlassSettingRow(
                title = "Permissions",
                trailingText = "Manage",
                onClick = { viewModel.openPermissions() }
            )
        }

        HaloGlassSection(title = "DEVELOPER") {
            HaloGlassSettingRow(
                title = "Developer Mode",
                trailingText = if (uiState.devMode) "On" else "Off",
                onClick = { viewModel.setDevMode(!uiState.devMode) }
            )
            if (uiState.devMode) {
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Protocol Lab",
                    trailingText = "Open",
                    onClick = { viewModel.openProtocolLab() }
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Runtime Inspector",
                    trailingText = "Open",
                    onClick = { viewModel.openInspector() }
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Runtime Console",
                    trailingText = "Open",
                    onClick = { viewModel.openConsole() }
                )
            }
        }

        HaloGlassSection(title = "ABOUT") {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text("Halo", style = HaloTypography.titleMedium, color = HaloGlassColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(uiState.version, style = HaloTypography.labelMedium, color = HaloGlassColors.TextMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Portable cognitive runtime for wearable devices. Runtime first; Android is a host.",
                    style = HaloTypography.bodySmall,
                    color = HaloGlassColors.TextSecondary
                )
            }
        }
    }
}
