package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloWarning
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceRow
import com.halo.app.ui.workspace.HaloWorkspaceSection
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.drivers.heycyan.protocol.AudioRecordingState
import com.halo.drivers.heycyan.protocol.VideoRecordingState

@Composable
fun HeyCyanControlScreen(
    deviceId: String,
    viewModel: DevicesViewModel,
    onBack: () -> Unit
) {
    val media by (viewModel.mediaCoordinator()?.state ?: kotlinx.coroutines.flow.MutableStateFlow(com.halo.app.capture.WearableCaptureStatus())).collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val device = uiState.devices.find { it.id == deviceId }
    val isConnected = device?.isConnected == true
    val connectionState = device?.connectionStateText ?: "Unknown"
    val isReady = connectionState == "Ready"
    val connectionBusy =
        connectionState == "Connecting" ||
            connectionState == "Connected" ||
            connectionState == "Handshaking" ||
            connectionState.startsWith("Syncing", ignoreCase = true) ||
            connectionState == "Disconnecting"
    val statusAccent = when {
        connectionState == "Ready" -> HaloPastelMint
        connectionState.contains("Connecting", ignoreCase = true) ||
            connectionState.contains("Syncing", ignoreCase = true) ||
            connectionState.contains("Handshaking", ignoreCase = true) -> HaloWarning
        connectionState == "Error" -> MaterialTheme.colorScheme.error
        else -> HaloPastelBlue
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        HaloWorkspaceHeader(
            title = device?.name ?: "HeyCyan BW-AG1 Pro",
            subtitle = "Smart glasses · device runtime",
            onBack = onBack
        )

        Spacer(Modifier.height(18.dp))

        device?.healthWarning?.let { warning ->
            HaloWorkspaceCard(accent = HaloWarning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = HaloWarning
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        HaloWorkspaceCard(accent = statusAccent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = connectionState,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            connectionState == "Ready" -> "Device is ready for runtime actions."
                            connectionState == "Error" -> "Reconnect to retry the session."
                            isConnected -> "Device session is active."
                            else -> "No active device session."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HaloWorkspaceButton(
                    text = when {
                        connectionBusy -> "Working…"
                        isConnected -> "Disconnect"
                        else -> "Connect"
                    },
                    onClick = {
                        if (isConnected) viewModel.disconnectDevice(deviceId)
                        else viewModel.connectDevice(deviceId)
                    },
                    enabled = !connectionBusy,
                    primary = !isConnected
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Quick actions") {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val photoState = device?.photoState ?: PhotoActionState.READY
                    val capturing = photoState == PhotoActionState.CAPTURING
                    HaloWorkspaceButton(
                        text = when (photoState) {
                            PhotoActionState.READY -> "Take photo"
                            PhotoActionState.CAPTURING -> "Capturing…"
                            PhotoActionState.COMPLETED -> "Take another"
                            PhotoActionState.FAILED -> "Retry photo"
                        },
                        onClick = { viewModel.takePhoto(deviceId) },
                        modifier = Modifier.weight(1f),
                        enabled = isReady && !capturing && !media.busy
                    )

                    val videoState = device?.videoState ?: VideoRecordingState.IDLE
                    val recording = videoState == VideoRecordingState.RECORDING
                    val transitioning =
                        videoState == VideoRecordingState.STARTING ||
                            videoState == VideoRecordingState.STOPPING
                    HaloWorkspaceButton(
                        text = when (videoState) {
                            VideoRecordingState.IDLE -> "Start video"
                            VideoRecordingState.STARTING -> "Starting…"
                            VideoRecordingState.RECORDING -> "Stop video"
                            VideoRecordingState.STOPPING -> "Stopping…"
                            VideoRecordingState.ERROR -> "Retry video"
                            VideoRecordingState.UNKNOWN -> "Unknown"
                        },
                        onClick = {
                            if (recording) viewModel.stopVideo(deviceId)
                            else viewModel.startVideo(deviceId)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = isReady && !transitioning,
                        primary = !recording
                    )
                }

                val audioState = device?.audioState ?: AudioRecordingState.IDLE
                val audioRecording = audioState == AudioRecordingState.RECORDING
                HaloWorkspaceButton(
                    text = when (audioState) {
                        AudioRecordingState.IDLE -> "Start audio"
                        AudioRecordingState.RECORDING -> "Stop audio"
                        AudioRecordingState.ERROR -> "Retry audio"
                    },
                    onClick = {
                        if (audioRecording) viewModel.stopAudioRecording(deviceId)
                        else viewModel.startAudioRecording(deviceId)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isReady,
                    primary = !audioRecording
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Device state") {
            DeviceRow("Connection", connectionState)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Battery", device?.battery ?: "Unknown", device?.batteryAgeText)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Video recording", device?.videoState?.name ?: "IDLE")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Audio recording", device?.audioState?.name ?: "IDLE")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Wear state", device?.wearStateText ?: "UNKNOWN")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Volume", device?.volumeText ?: "Normal")
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Halo speech") {
            DeviceRow("Input", "Physical glasses activation automatically uses Halo")
            DeviceRow("Output", "Phone � Android speaker")
            DeviceRow("Input policy", "An active turn owns input; activation during playback is ignored")
        }
        Spacer(Modifier.height(20.dp))
        HaloWorkspaceSection(title = "Media") {
            Text(media.message, style = MaterialTheme.typography.bodyMedium)
            media.lastSync?.let { DeviceRow("Last received", java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it))) }
            HaloWorkspaceButton(text = "Refresh inventory", onClick = viewModel::refreshMedia, enabled = isReady && !media.busy)
            HaloWorkspaceButton(text = "Sync media", onClick = viewModel::syncMedia, enabled = isReady && !media.busy)
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Hardware inputs") {
            DeviceRow("AI button", "Input event · voice routing")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Camera button", "Input event · photo/video")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Touch panel", "Input event · gesture source")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Microphone", "Observation source · live audio")
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Capabilities") {
            DeviceRow("Battery", "Available")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Device info", "Available")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Media inventory", "Available")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Photo capture", "Available")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Video capture", "Available")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Audio recording", "Available")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Speaker output", "Available with Bluetooth routing")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Live audio input", "Shared local Whisper pipeline")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("AI photo", "Blocked by protocol")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Wi-Fi transfer", "Available with limitation")
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Preferences") {
            HaloWorkspaceToggleRow(
                title = "Auto-connect",
                subtitle = "Reconnect when this saved device becomes available.",
                checked = device?.autoConnect == true,
                onCheckedChange = { enabled ->
                    viewModel.toggleAutoConnect(deviceId, enabled)
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.forgetDevice(deviceId) },
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "Forget device",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Diagnostics") {
            DeviceRow("Device ID", deviceId)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Firmware", device?.firmware ?: "Unknown")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Health", device?.healthStateText ?: "UNKNOWN")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Last seen", device?.lastSeen ?: "Never")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DeviceRow("Protocol", "HeyCyan SDK v1")
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DeviceRow(
    title: String,
    value: String,
    subtitle: String? = null
) {
    HaloWorkspaceRow(
        title = title,
        subtitle = subtitle,
        trailingText = value
    )
}
