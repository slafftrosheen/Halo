package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.drivers.heycyan.protocol.AudioRecordingState
import com.halo.drivers.heycyan.protocol.VideoRecordingState

@Composable
fun HeyCyanControlScreen(
    deviceId: String,
    viewModel: DevicesViewModel,
    onBack: () -> Unit = {}
) {
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

        device?.healthWarning?.let { warning ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HaloGlassColors.Warning.copy(alpha = 0.2f))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = HaloGlassColors.Warning)
                    Spacer(Modifier.width(12.dp))
                    Text(warning, style = HaloTypography.bodyMedium, color = HaloGlassColors.Warning)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HaloGlassColors.GlassElevated)
                    .clickable {
                        if (isConnected) viewModel.disconnectDevice(deviceId)
                        else viewModel.connectDevice(deviceId)
                    }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Status", style = HaloTypography.labelMedium, color = HaloGlassColors.TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        device?.connectionStateText ?: "Unknown",
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

        item {
            HaloGlassSection(title = "QUICK ACTIONS") {
                Column(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val photoState = device?.photoState ?: PhotoActionState.READY
                        val capturing = photoState == PhotoActionState.CAPTURING
                        HaloGlassButton(
                            text = when (photoState) {
                                PhotoActionState.READY -> "Take Photo"
                                PhotoActionState.CAPTURING -> "Capturing..."
                                PhotoActionState.COMPLETED -> "Captured!"
                                PhotoActionState.FAILED -> "Failed"
                            },
                            onClick = { viewModel.takePhoto(deviceId) },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected && !capturing
                        )

                        val videoState = device?.videoState ?: VideoRecordingState.IDLE
                        val recording = videoState == VideoRecordingState.RECORDING
                        val transitioning = videoState == VideoRecordingState.STARTING || videoState == VideoRecordingState.STOPPING
                        HaloGlassButton(
                            text = when {
                                videoState == VideoRecordingState.STARTING -> "Starting..."
                                videoState == VideoRecordingState.STOPPING -> "Stopping..."
                                recording -> "Stop Video"
                                else -> "Start Video"
                            },
                            onClick = {
                                if (recording) viewModel.stopVideo(deviceId) else viewModel.startVideo(deviceId)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected && !transitioning
                        )
                    }

                    val audioRecording = device?.audioState == AudioRecordingState.RECORDING
                    HaloGlassButton(
                        text = if (audioRecording) "Stop Audio" else "Start Audio",
                        onClick = {
                            if (audioRecording) viewModel.stopAudioRecording(deviceId)
                            else viewModel.startAudioRecording(deviceId)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isConnected
                    )
                }
            }
        }

        item {
            HaloGlassSection(title = "DEVICE STATE") {
                HaloGlassSettingRow(title = "Connection", value = device?.connectionStateText ?: "Unknown")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Battery", value = "${device?.battery ?: "Unknown"}\n${device?.batteryAgeText ?: "No data"}")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Video Recording", value = device?.videoState?.name ?: "IDLE")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Audio Recording", value = device?.audioState?.name ?: "IDLE")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Wear State", value = device?.wearStateText ?: "UNKNOWN")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Volume", value = device?.volumeText ?: "Normal")
            }
        }

        item {
            HaloGlassSection(title = "HARDWARE INPUTS") {
                HaloGlassSettingRow(title = "AI Button", value = "Input event • voice routing")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Camera Button", value = "Input event • photo/video")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Touch Panel", value = "Input event • gesture source")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Microphone", value = "Observation source • live audio")
            }
        }

        item {
            HaloGlassSection(title = "CAPABILITIES") {
                HaloGlassSettingRow(title = "Battery", value = "Available")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Device info", value = "Available")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Media inventory", value = "Available")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Photo capture", value = "Available")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Video capture", value = "Available")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Audio recording", value = "Available")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Live audio input", value = "Structural validation")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "AI photo", value = "Blocked by protocol")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Wi-Fi transfer", value = "Available with limitation")
            }
        }

        item {
            HaloGlassSection(title = "PREFERENCES") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.toggleAutoConnect(deviceId, !(device?.autoConnect ?: false))
                        }
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = HaloGlassColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Auto-connect", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                    }
                    Text(
                        if (device?.autoConnect == true) "ON" else "OFF",
                        style = HaloTypography.bodyLarge,
                        color = HaloGlassColors.Accent
                    )
                }
                HaloGlassDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.forgetDevice(deviceId) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = HaloGlassColors.Danger)
                    Spacer(Modifier.width(12.dp))
                    Text("Forget device", style = HaloTypography.bodyLarge, color = HaloGlassColors.Danger)
                }
            }
        }

        item {
            HaloGlassSection(title = "DIAGNOSTICS") {
                HaloGlassSettingRow(title = "Device ID", value = deviceId)
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Firmware", value = device?.firmware ?: "Unknown")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Health", value = device?.healthStateText ?: "UNKNOWN")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Last seen", value = device?.lastSeen ?: "Never")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Protocol", value = "HeyCyan SDK v1")
            }
        }
    }
}
