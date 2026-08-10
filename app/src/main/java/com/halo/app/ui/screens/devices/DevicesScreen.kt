package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Devices",
                style = HaloTypography.displayMedium,
                color = HaloGlassColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connected Halos and capability status",
                style = HaloTypography.bodyMedium,
                color = HaloGlassColors.TextSecondary
            )
        }
        
        item {
            HaloGlassSection(
                title = "Connected Devices",
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.devices.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "No devices connected",
                            color = HaloGlassColors.TextSecondary,
                            style = HaloTypography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Wake Halo devices to connect",
                            color = HaloGlassColors.TextMuted,
                            style = HaloTypography.labelLarge
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        uiState.devices.forEach { device ->
                            DeviceCard(device)
                        }
                    }
                }
            }
        }
        
        if (uiState.showCapabilities) {
            item {
                HaloGlassSection(
                    title = "Capabilities",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DeviceCapabilitiesList(uiState.capabilities)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo) {
    HaloGlassCard(
        onClick = { /* TODO: Handle device click */ }
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = device.name,
                        color = HaloGlassColors.TextPrimary,
                        style = HaloTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = device.id,
                        color = HaloGlassColors.TextSecondary,
                        style = HaloTypography.labelMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .background(device.status.color, androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = device.status.label,
                        color = HaloGlassColors.TextPrimary,
                        style = HaloTypography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = device.description,
                color = HaloGlassColors.TextMuted,
                style = HaloTypography.bodySmall
            )
        }
    }
}

@Composable
private fun DeviceCapabilitiesList(capabilities: List<String>) {
    Text(
        text = "Device supports:",
        color = HaloGlassColors.TextSecondary,
        style = HaloTypography.bodySmall
    )
    Spacer(modifier = Modifier.height(8.dp))
    // Capability tags would go here
}

enum class DeviceStatus(val label: String, val color: Color) {
    Connected("Connected", HaloGlassColors.Accent),
    Pairing("Pairing", HaloGlassColors.Warning),
    Disconnected("Offline", HaloGlassColors.Danger)
}

data class DeviceInfo(
    val id: String,
    val name: String,
    val description: String,
    val status: DeviceStatus,
    val capabilities: DeviceCapabilities? = null
)

data class DeviceCapabilities(
    val speech: CapabilityStatus = CapabilityStatus.Unsupported,
    val vision: CapabilityStatus = CapabilityStatus.Unsupported,
    val movement: CapabilityStatus = CapabilityStatus.Unsupported,
    val communication: CapabilityStatus = CapabilityStatus.Unsupported
)

enum class CapabilityStatus {
    Supported, Partial, Unsupported
}

data class DevicesUiState(
    val devices: List<DeviceInfo> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val showCapabilities: Boolean = true
)

class DevicesViewModel {
    val uiState = mutableStateOf(DevicesUiState())
}

// Device Capability Registry - Authoritative source for device capabilities
object DeviceCapabilityRegistry {
    val knownCapabilities = setOf(
        "Speech Recognition",
        "Wake Word Detection",
        "Camera Preview",
        "Motion Detection",
        "BLE Communication",
        "Audio Playback",
        "Temperature Sensor",
        "Accelerometer"
    )
    
    fun getSupportedCapabilities(device: DeviceInfo?): List<String> {
        return device?.capabilities?.let { caps ->
            buildList {
                if (caps.speech == CapabilityStatus.Supported) add("Speech Recognition")
                if (caps.vision == CapabilityStatus.Supported) add("Camera Preview")
                // Add more as implemented
            }
        } ?: emptyList()
    }
    
    fun isCapabilitySupported(caps: String): Boolean = knownCapabilities.contains(caps)
}