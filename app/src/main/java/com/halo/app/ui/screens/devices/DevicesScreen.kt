package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.runtime.RuntimeSnapshotSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PhotoActionState {
    READY,
    CAPTURING,
    COMPLETED,
    FAILED
}

data class GenericDeviceUiState(
    val id: String,
    val name: String,
    val type: String,
    val isConnected: Boolean,
    val battery: String,
    val batteryAgeText: String = "Live",
    val isBatteryStale: Boolean = false,
    val firmware: String,
    val lastSeen: String,
    val photoState: PhotoActionState = PhotoActionState.READY,
    val videoState: com.halo.drivers.heycyan.protocol.VideoRecordingState = com.halo.drivers.heycyan.protocol.VideoRecordingState.IDLE,
    val audioState: com.halo.drivers.heycyan.protocol.AudioRecordingState = com.halo.drivers.heycyan.protocol.AudioRecordingState.IDLE,
    val healthStateText: String = "Healthy",
    val healthWarning: String? = null,
    val wearStateText: String = "Unknown",
    val volumeText: String = "Normal"
)

data class DevicesUiState(
    val devices: List<GenericDeviceUiState> = emptyList()
)

class DevicesViewModel(
    val deviceRegistry: com.halo.sdk.runtime.embodiment.DeviceRegistry,
    val sessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager,
    private val eventBus: com.halo.sdk.runtime.EventBus,
    private val snapshotSource: com.halo.sdk.runtime.RuntimeSnapshotSource,
    private val heyCyanDriver: com.halo.drivers.heycyan.RealHeyCyanDriver? = null
) : ViewModel() {
    
    private fun formatLastSeen(lastSeenMs: Long): String {
        val diff = System.currentTimeMillis() - lastSeenMs
        if (diff < 60_000) return "Just now"
        val minutes = diff / 60_000
        if (minutes < 60) return "$minutes min ago"
        val hours = minutes / 60
        if (hours < 24) return "$hours hours ago"
        val days = hours / 24
        return "$days days ago"
    }
    
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private val _photoStates = MutableStateFlow<Map<String, PhotoActionState>>(emptyMap())
    private val activePhotoIntents = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        viewModelScope.launch {
            // Combine saved devices, active sessions, and live driver state
            kotlinx.coroutines.flow.combine(
                sessionManager.activeSessions,
                heyCyanDriver?.deviceState ?: MutableStateFlow(com.halo.drivers.heycyan.protocol.HeyCyanDeviceState()),
                _photoStates
            ) { activeSessions, driverState, photoStates ->
                val savedDevices = deviceRegistry.getSavedDevices()
                savedDevices.map { record ->
                    val session = activeSessions.find { it.identity.deviceId == record.identity.deviceId }
                    val isConnected = session?.state?.value == com.halo.sdk.runtime.embodiment.SessionState.READY
                    val batteryState = driverState.battery
                    val batteryText = if (batteryState != null) "${batteryState.level}%${if (batteryState.charging) " (Charging)" else ""}" else "Unknown"
                    val batteryDiffMs = if (batteryState != null) System.currentTimeMillis() - batteryState.timestampMs else Long.MAX_VALUE
                    val isStale = batteryDiffMs > 300_000L
                    val batteryAgeText = when {
                        !isConnected && batteryState != null -> "Last known battery (${formatLastSeen(batteryState.timestampMs)})"
                        batteryState == null -> "No data"
                        isStale -> "Stale (last reported ${formatLastSeen(batteryState.timestampMs)})"
                        else -> "Updated ${formatLastSeen(batteryState.timestampMs)}"
                    }
                    val healthWarn = when (driverState.healthState) {
                        com.halo.drivers.heycyan.protocol.DeviceHealthState.WARNING_MEMORY_INSUFFICIENT -> "Warning: Device memory insufficient"
                        com.halo.drivers.heycyan.protocol.DeviceHealthState.UNBOUND -> "Warning: Device unbound from app"
                        com.halo.drivers.heycyan.protocol.DeviceHealthState.ERROR -> "Device error detected"
                        else -> null
                    }
                    GenericDeviceUiState(
                        id = record.identity.deviceId,
                        name = record.identity.displayName,
                        type = record.identity.deviceType,
                        isConnected = isConnected,
                        battery = batteryText,
                        batteryAgeText = batteryAgeText,
                        isBatteryStale = isStale,
                        firmware = driverState.version?.firmware ?: record.firmwareVersion ?: "Unknown",
                        lastSeen = if (isConnected) "Now" else "Last seen ${formatLastSeen(record.lastSeenMs)}",
                        photoState = photoStates[record.identity.deviceId] ?: PhotoActionState.READY,
                        videoState = driverState.videoRecordingState,
                        audioState = driverState.audioRecordingState,
                        healthStateText = driverState.healthState.name,
                        healthWarning = healthWarn,
                        wearStateText = driverState.wearState.name,
                        volumeText = driverState.volumeState?.let { "Music ${it.musicCurrent}, Call ${it.callCurrent}" } ?: "Normal"
                    )
                }
            }.collect { updatedDevices ->
                _uiState.value = DevicesUiState(devices = updatedDevices)
            }
        }

        viewModelScope.launch {
            eventBus.observe(com.halo.sdk.runtime.EmbodimentExecutionEvent::class.java).collect { event ->
                val deviceId = event.device.deviceId
                val expectedIntentId = activePhotoIntents[deviceId]
                if (expectedIntentId != null && event.result.intentId == expectedIntentId) {
                    if (event.result.status == com.halo.sdk.runtime.embodiment.ExecutionStatus.SUCCEEDED) {
                        activePhotoIntents.remove(deviceId)
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.COMPLETED)
                        kotlinx.coroutines.delay(2000L)
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.READY)
                    } else if (event.result.status == com.halo.sdk.runtime.embodiment.ExecutionStatus.FAILED) {
                        activePhotoIntents.remove(deviceId)
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.FAILED)
                        kotlinx.coroutines.delay(3000L)
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.READY)
                    }
                }
            }
        }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateTo(route: String) {
        viewModelScope.launch { _navigationEvents.emit(route) }
    }

    fun takePhoto(deviceId: String) {
        val currentPhotoState = _photoStates.value[deviceId] ?: PhotoActionState.READY
        if (currentPhotoState == PhotoActionState.CAPTURING) return
        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.CAPTURING)
        
        val intent = com.halo.sdk.runtime.embodiment.EmbodimentIntent(
            intentId = java.util.UUID.randomUUID().toString(), targetDeviceId = deviceId,
            kind = com.halo.sdk.runtime.embodiment.IntentKind.CAPTURE_PHOTO
        )
        activePhotoIntents[deviceId] = intent.intentId
        dispatchEmbodimentIntent(deviceId, intent)
    }

    fun startVideo(deviceId: String) {
        dispatchEmbodimentIntent(deviceId, com.halo.sdk.runtime.embodiment.EmbodimentIntent(
            intentId = java.util.UUID.randomUUID().toString(), targetDeviceId = deviceId,
            kind = com.halo.sdk.runtime.embodiment.IntentKind.START_VIDEO
        ))
    }

    fun stopVideo(deviceId: String) {
        dispatchEmbodimentIntent(deviceId, com.halo.sdk.runtime.embodiment.EmbodimentIntent(
            intentId = java.util.UUID.randomUUID().toString(), targetDeviceId = deviceId,
            kind = com.halo.sdk.runtime.embodiment.IntentKind.STOP_VIDEO
        ))
    }

    fun startAudioRecording(deviceId: String) {
        dispatchEmbodimentIntent(deviceId, com.halo.sdk.runtime.embodiment.EmbodimentIntent(
            intentId = java.util.UUID.randomUUID().toString(), targetDeviceId = deviceId,
            kind = com.halo.sdk.runtime.embodiment.IntentKind.START_AUDIO_RECORDING
        ))
    }

    fun stopAudioRecording(deviceId: String) {
        dispatchEmbodimentIntent(deviceId, com.halo.sdk.runtime.embodiment.EmbodimentIntent(
            intentId = java.util.UUID.randomUUID().toString(), targetDeviceId = deviceId,
            kind = com.halo.sdk.runtime.embodiment.IntentKind.STOP_AUDIO_RECORDING
        ))
    }

    fun dispatchEmbodimentIntent(deviceId: String, intent: com.halo.sdk.runtime.embodiment.EmbodimentIntent) {
        viewModelScope.launch {
            eventBus.publish(com.halo.sdk.runtime.IntentCreated(intentId = intent.intentId, intentKind = intent.kind.name))
            val session = sessionManager.activeSessions.value.find { it.identity.deviceId == deviceId }
            if (session != null && session.state.value == com.halo.sdk.runtime.embodiment.SessionState.READY) {
                try {
                    session.dispatch(intent)
                } catch (e: Exception) {
                    eventBus.publish(com.halo.sdk.runtime.EmbodimentExecutionEvent(
                        device = com.halo.sdk.runtime.DeviceIdentity(deviceId, "Unknown", "Unknown"),
                        result = com.halo.sdk.runtime.embodiment.ExecutionResult(
                            intentId = intent.intentId, status = com.halo.sdk.runtime.embodiment.ExecutionStatus.FAILED,
                            reason = "Dispatch error: ${e.message}", timestamp = System.currentTimeMillis(), embodimentId = deviceId
                        )
                    ))
                }
            } else {
                eventBus.publish(com.halo.sdk.runtime.EmbodimentExecutionEvent(
                    device = com.halo.sdk.runtime.DeviceIdentity(deviceId, "Unknown", "Unknown"),
                    result = com.halo.sdk.runtime.embodiment.ExecutionResult(
                        intentId = intent.intentId, status = com.halo.sdk.runtime.embodiment.ExecutionStatus.FAILED,
                        reason = "No active session", timestamp = System.currentTimeMillis(), embodimentId = deviceId
                    )
                ))
            }
        }
    }

    fun connectDevice(deviceId: String) { viewModelScope.launch { sessionManager.getSession(deviceId)?.connect() } }
    fun disconnectDevice(deviceId: String) { viewModelScope.launch { sessionManager.getSession(deviceId)?.disconnect() } }
    fun forgetDevice(deviceId: String) {
        viewModelScope.launch {
            sessionManager.getSession(deviceId)?.disconnect()
            deviceRegistry.removeDevice(deviceId)
            _navigationEvents.emit("devices")
        }
    }

    fun toggleAutoConnect(deviceId: String, enabled: Boolean) {
        viewModelScope.launch {
            deviceRegistry.getDevice(deviceId)?.let { current ->
                deviceRegistry.saveDevice(current.copy(autoConnect = enabled))
            }
        }
    }
}

@Composable
fun DevicesScreen(viewModel: DevicesViewModel, onNavigate: (String) -> Unit = {}, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    HaloGlassLazyPage(title = "") {
        item {
            HaloGlassBackHeader(title = "Devices", onBack = onBack)
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        items(uiState.devices) { device ->
            DeviceRow(device = device, onClick = { viewModel.navigateTo("device/${device.id}") })
            Spacer(modifier = Modifier.height(12.dp))
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
        item {
            HaloGlassButton(text = "Add device", onClick = { viewModel.navigateTo("discovery") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DeviceRow(device: GenericDeviceUiState, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HaloGlassColors.GlassElevated)
            .clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = HaloTypography.titleMedium, color = HaloGlassColors.TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(if (device.isConnected) HaloGlassColors.Accent else HaloGlassColors.Danger, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (device.isConnected) "Connected" else "Offline • ${device.lastSeen}", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                }
            }
            Text(text = ">", style = HaloTypography.titleLarge, color = HaloGlassColors.TextMuted)
        }
        if (device.healthWarning != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(HaloGlassColors.Warning.copy(alpha = 0.2f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = device.healthWarning, style = HaloTypography.labelSmall, color = HaloGlassColors.Warning)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Battery: ${device.battery}", style = HaloTypography.labelSmall, color = HaloGlassColors.TextPrimary)
            if (device.isBatteryStale || !device.isConnected) {
                Text(text = device.batteryAgeText, style = HaloTypography.labelSmall, color = HaloGlassColors.Warning)
            }
        }
    }
}
