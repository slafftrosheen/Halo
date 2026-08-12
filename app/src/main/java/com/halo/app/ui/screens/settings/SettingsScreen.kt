package com.halo.app.ui.screens.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
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
        Text("Settings", style = HaloTypography.displayMedium, color = HaloGlassColors.TextPrimary)
        
        // Runtime Section
        HaloGlassSection(title = "Runtime") {
            SettingItem("Version", uiState.version, clickable = false)
            HaloGlassDivider()
            SettingItem("Provider", uiState.provider, clickable = false)
        }
        
        // Speech Section
        HaloGlassSection(title = "Speech") {
            SettingItem("Wake Word", uiState.wakeWord, clickable = true)
            HaloGlassDivider()
            SettingItem("Sensitivity", uiState.sensitivity, clickable = true)
        }
        
        // Developer Section
        HaloGlassSection(title = "Developer") {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Developer Mode", color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
                    Switch(
                        checked = uiState.devMode,
                        onCheckedChange = { viewModel.setDevMode(it) }
                    )
                }
                
                if (uiState.devMode) {
                    HaloGlassDivider()
                    SettingItem("Protocol Lab", "Open", clickable = true) {
                        viewModel.navigateTo("protocol_lab")
                    }
                    HaloGlassDivider()
                    SettingItem("Console", "Inspect", clickable = true) {
                        viewModel.navigateTo("console")
                    }
                    HaloGlassDivider()
                    SettingItem("Runtime Inspector", "Debug", clickable = true) {
                        viewModel.navigateTo("inspector")
                    }
                }
            }
        }
        
        // System Section
        HaloGlassSection(title = "System") {
            SettingItem("Permissions", "Review", clickable = true)
            HaloGlassDivider()
            SettingItem("Logging", uiState.logLevel, clickable = true)
        }
    }
}

@Composable
private fun SettingItem(
    label: String, 
    value: String, 
    clickable: Boolean,
    onClick: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = clickable) { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = HaloGlassColors.TextSecondary, style = HaloTypography.bodyMedium)
            if (clickable) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HaloGlassColors.TextMuted)
            }
        }
    }
}

@Composable
fun PermissionsScreen() {
    val uiState by remember { mutableStateOf(PermissionsUiState()) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Permissions",
            style = HaloTypography.displayMedium,
            color = HaloGlassColors.TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Manage device permissions for Halo UI",
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary
        )
        
        HaloGlassSection(title = "Audio Permissions") {
            PermissionItem(
                icon = Icons.Default.Mic,
                label = "Microphone",
                description = "Required for voice commands and listening mode",
                granted = uiState.microphoneGranted,
                onChange = { /* Handle permission change */ }
            )
            HaloGlassDivider()
            PermissionItem(
                icon = Icons.Default.VolumeUp,
                label = "Speaker Output",
                description = "Required for audio playback and responses",
                granted = uiState.speakerGranted,
                onChange = { /* Handle permission change */ }
            )
        }
        
        HaloGlassSection(title = "Camera Permissions") {
            PermissionItem(
                icon = Icons.Default.Camera,
                label = "Camera",
                description = "Required for visual recognition and scene understanding",
                granted = uiState.cameraGranted,
                onChange = { /* Handle permission change */ }
            )
        }
        
        HaloGlassSection(title = "System Permissions") {
            PermissionItem(
                icon = Icons.Default.Security,
                label = "BLE Communication",
                description = "Required for device pairing and data transfer",
                granted = uiState.bleGranted,
                onChange = { /* Handle permission change */ }
            )
            HaloGlassDivider()
            PermissionItem(
                icon = Icons.Default.Settings,
                label = "Location Services",
                description = "Required for proximity detection",
                granted = uiState.locationGranted,
                onChange = { /* Handle permission change */ }
            )
        }
        
        // Explicit "Supported vs Unsupported" visualization
        HaloGlassSection(title = "Device Capabilities") {
            CapabilityRow(
                name = "Speech Recognition",
                status = uiState.speechSupport,
                supported = true
            )
            HaloGlassDivider()
            CapabilityRow(
                name = "Text-to-Speech",
                status = uiState.ttsSupport,
                supported = true
            )
            HaloGlassDivider()
            CapabilityRow(
                name = "Visual Recognition",
                status = uiState.visionSupport,
                supported = false
            )
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    label: String,
    description: String,
    granted: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!granted) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = HaloGlassColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
                Text(description, color = HaloGlassColors.TextMuted, style = HaloTypography.labelMedium)
            }
        }
        Switch(checked = granted, onCheckedChange = onChange)
    }
}

@Composable
private fun CapabilityRow(
    name: String,
    status: CapabilityStatus,
    supported: Boolean
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (supported) {
                Text(
                    text = "Supported",
                    color = HaloGlassColors.Success,
                    style = HaloTypography.labelMedium
                )
            } else {
                HaloGlassChip.Danger(text = "Unsupported")
            }
        }
    }
}

data class PermissionsUiState(
    val microphoneGranted: Boolean = false,
    val speakerGranted: Boolean = true,
    val cameraGranted: Boolean = false,
    val bleGranted: Boolean = false,
    val locationGranted: Boolean = false,
    val speechSupport: CapabilityStatus = CapabilityStatus.Supported,
    val ttsSupport: CapabilityStatus = CapabilityStatus.Supported,
    val visionSupport: CapabilityStatus = CapabilityStatus.Unsupported
)

enum class CapabilityStatus {
    Supported, PartiallySupported, Unsupported
}
