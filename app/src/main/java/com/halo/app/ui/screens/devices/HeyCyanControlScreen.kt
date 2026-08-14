package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HeyCyanControlScreen(deviceId: String, viewModel: DevicesViewModel, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    
    val device = uiState.devices.find { it.id == deviceId }
    val isConnected = device?.isConnected == true

    HaloGlassLazyPage(title = "") {
        item {
            HaloGlassBackHeader(
                title = device?.name ?: "HeyCyan BW-AG1 Pro",
                onBack = onBack
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Health Warning Banner (Memory Fault / Unbind)
        if (device?.healthWarning != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HaloGlassColors.Warning.copy(alpha = 0.2f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = HaloGlassColors.Warning,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = device.healthWarning,
                        style = HaloTypography.bodyMedium,
                        color = HaloGlassColors.Warning
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Status Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HaloGlassColors.GlassElevated)
                    .clickable {
                        if (isConnected) {
                            viewModel.disconnectDevice(deviceId)
                        } else {
                            viewModel.connectDevice(deviceId)
                        }
                    }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Status",
                        style = HaloTypography.labelMedium,
                        color = HaloGlassColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isConnected) "Connected" else "Offline",
                        style = HaloTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isConnected) HaloGlassColors.Success else HaloGlassColors.TextPrimary
                    )
                }
                
                HaloGlassChip(
                    text = if (isConnected) "Disconnect" else "Connect",
                    modifier = Modifier.clickable {
                        if (isConnected) viewModel.disconnectDevice(deviceId)
                        else viewModel.connectDevice(deviceId)
                    }
                )
            }
        }
        
        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 1. QUICK ACTIONS
        item {
            HaloGlassSection(
                title = "QUICK ACTIONS",
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Photo Button with States & Double-Click Lock
                    val photoState = device?.photoState ?: PhotoActionState.READY
                    val isPhotoCapturing = photoState == PhotoActionState.CAPTURING
                    val photoText = when (photoState) {
                        PhotoActionState.READY -> "Take Photo"
                        PhotoActionState.CAPTURING -> "Capturing..."
                        PhotoActionState.COMPLETED -> "Captured!"
                        PhotoActionState.FAILED -> "Failed"
                    }
                    
                    HaloGlassButton(
                        text = photoText,
                        onClick = {
                            if (isConnected && !isPhotoCapturing) {
                                viewModel.takePhoto(deviceId)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isConnected && !isPhotoCapturing
                    )

                    // Video Toggle Button
                    val isVideoRecording = device?.videoState == com.halo.drivers.heycyan.protocol.VideoRecordingState.RECORDING
                    val isVideoStarting = device?.videoState == com.halo.drivers.heycyan.protocol.VideoRecordingState.STARTING
                    val videoText = when {
                        isVideoStarting -> "Starting Video..."
                        isVideoRecording -> "Stop Video"
                        else -> "Start Video"
                    }

                    HaloGlassButton(
                        text = videoText,
                        onClick = {
                            if (isConnected && !isVideoStarting) {
                                if (isVideoRecording) {
                                    viewModel.stopVideo(deviceId)
                                } else {
                                    viewModel.startVideo(deviceId)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isConnected && !isVideoStarting
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 2. DEVICE STATE & TELEMETRY
        item {
            HaloGlassSection(
                title = "DEVICE STATE",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(
                    title = "Battery",
                    value = "${device?.battery ?: "Unknown"}\n${device?.batteryAgeText ?: ""}"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Video Recording State",
                    value = device?.videoState?.name ?: "IDLE"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Audio Recording State",
                    value = device?.audioState?.name ?: "IDLE"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Wear State",
                    value = device?.wearStateText ?: "UNKNOWN"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Volume (Read-only)",
                    value = device?.volumeText ?: "Normal"
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 3. HARDWARE CONTROLS
        item {
            HaloGlassSection(
                title = "HARDWARE CONTROLS",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "AI Button", value = "Mapped to Voice Assistant")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Camera Button", value = "Mapped to Photo Capture")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Touch Panel", value = "Tap / Swipe Gestures Active")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Microphone", value = "Active Perception Source")
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 4. PREFERENCES
        item {
            HaloGlassSection(
                title = "PREFERENCES",
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleAutoConnect(deviceId, false) }
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = HaloGlassColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Auto-connect", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                    }
                    Text("ON", style = HaloTypography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = HaloGlassColors.Accent)
                }
                HaloGlassDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.forgetDevice(deviceId) }
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = HaloGlassColors.Danger,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Forget device", style = HaloTypography.bodyLarge, color = HaloGlassColors.Danger)
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 5. DIAGNOSTICS & HARDWARE INFO
        item {
            HaloGlassSection(
                title = "DIAGNOSTICS & HARDWARE",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Device ID", value = deviceId)
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Firmware", value = device?.firmware ?: "Unknown")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Health Status", value = device?.healthStateText ?: "HEALTHY")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Protocol", value = "HeyCyan SDK v1 (Verified)")
            }
        }
        
        item { Spacer(modifier = Modifier.height(48.dp)) }
    }
}
