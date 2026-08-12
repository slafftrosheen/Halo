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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext

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
        
        // HALO Section
        HaloGlassSection(title = "HALO") {
            SettingItem("Assistant", "Native Engine", clickable = true)
            HaloGlassDivider()
            SettingItem("Voice", "System Default", clickable = true)
            HaloGlassDivider()
            SettingItem("Glasses", "Halo Glass Prototype", clickable = true)
            HaloGlassDivider()
            SettingItem("Privacy", "Review", clickable = true)
            HaloGlassDivider()
            SettingItem("Appearance", "Dark", clickable = true)
        }
        
        // DEVELOPER Section
        HaloGlassSection(title = "DEVELOPER") {
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
                    SettingItem("Runtime Inspector", "Debug", clickable = true) {
                        viewModel.navigateTo("inspector")
                    }
                    HaloGlassDivider()
                    SettingItem("Console", "Inspect", clickable = true) {
                        viewModel.navigateTo("console")
                    }
                    HaloGlassDivider()
                    SettingItem("Audio Diagnostics", "View", clickable = true) {
                        // TODO: Implement Audio diagnostics route
                    }
                }
            }
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
    val context = LocalContext.current
    
    val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val btConnectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val btScanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    val locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
            "Current device permissions status",
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary
        )
        
        HaloGlassSection(title = "Audio Permissions") {
            PermissionItem(
                icon = Icons.Default.Mic,
                label = "Microphone",
                description = "Required for voice commands and listening mode",
                granted = micGranted
            )
            HaloGlassDivider()
            PermissionItem(
                icon = Icons.Default.VolumeUp,
                label = "Speaker Output",
                description = "Required for audio playback and responses",
                granted = true // Always granted on Android
            )
        }
        
        HaloGlassSection(title = "System Permissions") {
            PermissionItem(
                icon = Icons.Default.Security,
                label = "BLE Communication",
                description = "Required for device pairing and data transfer",
                granted = btConnectGranted && btScanGranted
            )
            HaloGlassDivider()
            PermissionItem(
                icon = Icons.Default.Settings,
                label = "Location Services",
                description = "Required for proximity detection",
                granted = locationGranted
            )
        }
        
        HaloGlassSection(title = "Device Capabilities") {
            CapabilityRow(
                name = "Speech Recognition",
                status = CapabilityStatus.Supported,
                supported = true
            )
            HaloGlassDivider()
            CapabilityRow(
                name = "Text-to-Speech",
                status = CapabilityStatus.Supported,
                supported = true
            )
            HaloGlassDivider()
            CapabilityRow(
                name = "Visual Recognition",
                status = CapabilityStatus.Unsupported,
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
    granted: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = HaloGlassColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
                Text(description, color = HaloGlassColors.TextMuted, style = HaloTypography.labelMedium)
            }
        }
        Text(
            text = if (granted) "Allowed" else "Not allowed",
            style = HaloTypography.labelMedium,
            color = if (granted) HaloGlassColors.Success else HaloGlassColors.Warning
        )
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



enum class CapabilityStatus {
    Supported, PartiallySupported, Unsupported
}
