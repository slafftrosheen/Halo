package com.halo.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    HaloGlassPage(title = "Settings") {
        HaloGlassSection(title = "HALO") {
            HaloGlassSettingRow(
                title = "Intelligence",
                trailingText = uiState.provider,
                onClick = { viewModel.navigateTo("ai_settings") }
            )
            HaloGlassDivider()
            HaloGlassSettingRow(
                title = "Devices",
                trailingText = uiState.deviceName,
                onClick = { viewModel.navigateTo("devices") }
            )
            HaloGlassDivider()
            HaloGlassSettingRow(
                title = "Permissions",
                trailingText = "Manage",
                onClick = { viewModel.navigateTo("permissions") }
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
                    onClick = { viewModel.navigateTo("protocol_lab") }
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Runtime Inspector",
                    trailingText = "Open",
                    onClick = { viewModel.navigateTo("inspector") }
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Runtime Console",
                    trailingText = "Open",
                    onClick = { viewModel.navigateTo("console") }
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

@Composable
fun PermissionsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val btGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    HaloGlassPage(title = "") {
        HaloGlassBackHeader(title = "Permissions", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "Permissions used by the active host",
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary
        )

        HaloGlassSection(title = "AUDIO") {
            PermissionItem(Icons.Default.Mic, "Microphone", "Voice capture", micGranted)
            HaloGlassDivider()
            PermissionItem(Icons.Default.VolumeUp, "Speaker", "Response audio", true)
        }

        HaloGlassSection(title = "DEVICE ACCESS") {
            PermissionItem(
                Icons.Default.Security,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) "Bluetooth LE" else "Bluetooth / Location",
                "Required for wearable discovery and control",
                btGranted
            )
        }

        HaloGlassSection(title = "HOST CAPABILITIES") {
            CapabilityLine("Speech recognition", true)
            HaloGlassDivider()
            CapabilityLine("Text-to-speech", true)
            HaloGlassDivider()
            CapabilityLine("Camera visual input", false)
        }
    }
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    granted: Boolean
) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = HaloGlassColors.Accent, modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
            Text(description, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }
        Text(
            if (granted) "Allowed" else "Not allowed",
            style = HaloTypography.labelSmall,
            color = if (granted) HaloGlassColors.Success else HaloGlassColors.Warning
        )
    }
}

@Composable
private fun CapabilityLine(name: String, supported: Boolean) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
        if (supported) {
            Text("Supported", style = HaloTypography.labelSmall, color = HaloGlassColors.Success)
        } else {
            HaloGlassChip.Danger("Unsupported")
        }
    }
}
