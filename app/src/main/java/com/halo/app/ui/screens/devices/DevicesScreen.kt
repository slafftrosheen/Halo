package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STALE_BATTERY_MS = 5 * 60 * 1000L

enum class PhotoActionState { READY, CAPTURING, COMPLETED, FAILED }

data class GenericDeviceUiState(
    val id: String,
    val name: String,
    val type: String,
    val isConnected: Boolean,
    val connectionStateText: String,
    val autoConnect: Boolean,
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

data class DevicesUiState(val devices: List<GenericDeviceUiState> = emptyList())

class DevicesViewModel(
    val deviceRegistry: com.halo.sdk.runtime.embodiment.DeviceRegistry,
    val sessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager,
    private val eventBus: com.halo.sdk.runtime.EventBus,
    private val snapshotSource: com.halo.sdk.runtime.RuntimeSnapshotSource,
    private val heyCyanDriver: com.halo.drivers.heycyan.RealHeyCyanDriver? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()
    private val _photoStates = MutableStateFlow<Map<String, PhotoActionState>>(emptyMap())
    private val activePhotoIntents = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _registryRevision = MutableStateFlow(0L)
    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                sessionManager.activeSessions,
                sessionManager.stateRevision,
                heyCyanDriver?.deviceState
                    ?: MutableStateFlow(com.halo.drivers.heycyan.protocol.HeyCyanDeviceState()),
                _photoStates,
                _registryRevision
            ) { activeSessions, _, driverState, photoStates, _ ->
                val now = System.currentTimeMillis()
                deviceRegistry.getSavedDevices().map { record ->
                    val session = activeSessions.find { it.identity.deviceId == record.identity.deviceId }
                    val sessionState = session?.state?.value
                    val isConnected = sessionState in setOf(
                        com.halo.sdk.runtime.embodiment.SessionState.CONNECTED,
                        com.halo.sdk.runtime.embodiment.SessionState.HANDSHAKE,
                        com.halo.sdk.runtime.embodiment.SessionState.CAPABILITY_SYNC,
                        com.halo.sdk.runtime.embodiment.SessionState.STATE_SYNC,
                        com.halo.sdk.runtime.embodiment.SessionState.READY
                    )
                    val stateText = when (sessionState) {
                        com.halo.sdk.runtime.embodiment.SessionState.SAVED -> "Saved"
                        com.halo.sdk.runtime.embodiment.SessionState.DISCOVERED -> "Discovered"
                        com.halo.sdk.runtime.embodiment.SessionState.CONNECTING -> "Connecting"
                        com.halo.sdk.runtime.embodiment.SessionState.CONNECTED -> "Connected"
                        com.halo.sdk.runtime.embodiment.SessionState.HANDSHAKE -> "Handshaking"
                        com.halo.sdk.runtime.embodiment.SessionState.CAPABILITY_SYNC -> "Syncing capabilities"
                        com.halo.sdk.runtime.embodiment.SessionState.STATE_SYNC -> "Syncing state"
                        com.halo.sdk.runtime.embodiment.SessionState.READY -> "Ready"
                        com.halo.sdk.runtime.embodiment.SessionState.DISCONNECTING -> "Disconnecting"
                        com.halo.sdk.runtime.embodiment.SessionState.DISCONNECTED -> "Disconnected"
                        com.halo.sdk.runtime.embodiment.SessionState.ERROR -> "Error"
                        null -> "Saved • no session"
                    }
                    val battery = driverState.battery
                    val batteryText = battery?.let {
                        "${it.level}%${if (it.charging) " • Charging" else ""}"
                    } ?: "Unknown"
                    val batteryAgeMs = battery?.let { now - it.timestampMs } ?: Long.MAX_VALUE
                    val healthWarning = when (driverState.healthState) {
                        com.halo.drivers.heycyan.protocol.DeviceHealthState.WARNING_MEMORY_INSUFFICIENT -> "Device memory insufficient"
                        com.halo.drivers.heycyan.protocol.DeviceHealthState.UNBOUND -> "Device is unbound"
                        com.halo.drivers.heycyan.protocol.DeviceHealthState.ERROR -> "Device error detected"
                        else -> null
                    }
                    GenericDeviceUiState(
                        id = record.identity.deviceId,
                        name = record.identity.displayName,
                        type = record.identity.deviceType,
                        isConnected = isConnected,
                        connectionStateText = stateText,
                        autoConnect = record.autoConnect,
                        battery = batteryText,
                        batteryAgeText = if (battery != null) "Updated ${formatLastSeen(battery.timestampMs)}" else "No data",
                        isBatteryStale = batteryAgeMs > STALE_BATTERY_MS,
                        firmware = driverState.version?.firmware ?: record.firmwareVersion ?: "Unknown",
                        lastSeen = if (isConnected) "Now" else if (record.lastSeenMs > 0L) "Last seen ${formatLastSeen(record.lastSeenMs)}" else "Never",
                        photoState = photoStates[record.identity.deviceId] ?: PhotoActionState.READY,
                        videoState = driverState.videoRecordingState,
                        audioState = driverState.audioRecordingState,
                        healthStateText = driverState.healthState.name,
                        healthWarning = healthWarning,
                        wearStateText = driverState.wearState.name,
                        volumeText = driverState.volumeState?.let { "Music ${it.musicCurrent}, Call ${it.callCurrent}" } ?: "Normal"
                    )
                }
            }.collect { devices ->
                _uiState.value = DevicesUiState(devices)
            }
        }

        viewModelScope.launch {
            eventBus.observe(com.halo.sdk.runtime.EmbodimentExecutionEvent::class.java).collect { event ->
                val deviceId = event.device.deviceId
                val expectedIntentId = activePhotoIntents[deviceId]
                if (expectedIntentId == null || event.result.intentId != expectedIntentId) return@collect
                when (event.result.status) {
                    com.halo.sdk.runtime.embodiment.ExecutionStatus.SUCCEEDED -> {
                        activePhotoIntents.remove(deviceId)
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.COMPLETED)
                        kotlinx.coroutines.delay(2000L)
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.READY)
                    }
                    com.halo.sdk.runtime.embodiment.ExecutionStatus.FAILED,
                    com.halo.sdk.runtime.embodiment.ExecutionStatus.REJECTED -> {
                        activePhotoIntents.remove(deviceId)
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.FAILED)
                        kotlinx.coroutines.delay(3000L)
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.READY)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun formatLastSeen(timestampMs: Long): String {
        val diff = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
        if (diff < 60_000) return "just now"
        val minutes = diff / 60_000
        if (minutes < 60) return "$minutes min ago"
        val hours = minutes / 60
        if (hours < 24) return "$hours h ago"
        return "${hours / 24} d ago"
    }

    fun navigateTo(route: String) = viewModelScope.launch { _navigationEvents.emit(route) }

    private fun newIntent(
        deviceId: String,
        kind: com.halo.sdk.runtime.embodiment.IntentKind
    ) = com.halo.sdk.runtime.embodiment.EmbodimentIntent(
        intentId = java.util.UUID.randomUUID().toString(),
        targetDeviceId = deviceId,
        kind = kind
    )

    fun takePhoto(deviceId: String) {
        if ((_photoStates.value[deviceId] ?: PhotoActionState.READY) == PhotoActionState.CAPTURING) return
        val intent = newIntent(deviceId, com.halo.sdk.runtime.embodiment.IntentKind.CAPTURE_PHOTO)
        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.CAPTURING)
        activePhotoIntents[deviceId] = intent.intentId
        dispatchEmbodimentIntent(deviceId, intent)
    }

    fun startVideo(deviceId: String) = dispatchEmbodimentIntent(deviceId, newIntent(deviceId, com.halo.sdk.runtime.embodiment.IntentKind.START_VIDEO))
    fun stopVideo(deviceId: String) = dispatchEmbodimentIntent(deviceId, newIntent(deviceId, com.halo.sdk.runtime.embodiment.IntentKind.STOP_VIDEO))
    fun startAudioRecording(deviceId: String) = dispatchEmbodimentIntent(deviceId, newIntent(deviceId, com.halo.sdk.runtime.embodiment.IntentKind.START_AUDIO_RECORDING))
    fun stopAudioRecording(deviceId: String) = dispatchEmbodimentIntent(deviceId, newIntent(deviceId, com.halo.sdk.runtime.embodiment.IntentKind.STOP_AUDIO_RECORDING))

    fun dispatchEmbodimentIntent(deviceId: String, intent: com.halo.sdk.runtime.embodiment.EmbodimentIntent) {
        viewModelScope.launch {
            eventBus.publish(com.halo.sdk.runtime.IntentCreated(intentId = intent.intentId, intentKind = intent.kind.name))
            val session = sessionManager.getSession(deviceId)
            if (session != null) {
                try {
                    session.dispatch(intent)
                } catch (e: Exception) {
                    eventBus.publish(com.halo.sdk.runtime.EmbodimentExecutionEvent(
                        device = com.halo.sdk.runtime.DeviceIdentity(deviceId, "Unknown", "Unknown"),
                        result = com.halo.sdk.runtime.embodiment.ExecutionResult(
                            intent.intentId,
                            com.halo.sdk.runtime.embodiment.ExecutionStatus.FAILED,
                            "Dispatch error: ${e.message}",
                            System.currentTimeMillis(),
                            deviceId
                        )
                    ))
                }
            } else {
                eventBus.publish(com.halo.sdk.runtime.EmbodimentExecutionEvent(
                    device = com.halo.sdk.runtime.DeviceIdentity(deviceId, "Unknown", "Unknown"),
                    result = com.halo.sdk.runtime.embodiment.ExecutionResult(
                        intent.intentId,
                        com.halo.sdk.runtime.embodiment.ExecutionStatus.FAILED,
                        "No active session",
                        System.currentTimeMillis(),
                        deviceId
                    )
                ))
            }
        }
    }

    fun connectDevice(deviceId: String) = viewModelScope.launch { sessionManager.getSession(deviceId)?.connect() }
    fun disconnectDevice(deviceId: String) = viewModelScope.launch { sessionManager.getSession(deviceId)?.disconnect() }

    fun forgetDevice(deviceId: String) {
        viewModelScope.launch {
            sessionManager.getSession(deviceId)?.disconnect()
            deviceRegistry.removeDevice(deviceId)
            _registryRevision.update { it + 1 }
        }
    }

    fun toggleAutoConnect(deviceId: String, enabled: Boolean) {
        viewModelScope.launch {
            deviceRegistry.getDevice(deviceId)?.let { current ->
                deviceRegistry.saveDevice(current.copy(autoConnect = enabled))
                _registryRevision.update { it + 1 }
            }
        }
    }
}

@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onNavigate: (String) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    HaloGlassLazyPage(title = "") {
        item { HaloGlassBackHeader(title = "Devices", onBack = onBack) }
        item { Spacer(Modifier.height(16.dp)) }

        if (uiState.devices.isEmpty()) {
            item {
                HaloGlassSection(title = "DEVICES") {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("No saved devices", style = HaloTypography.titleMedium, color = HaloGlassColors.TextPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Pair a wearable or robot to make it available to the runtime.",
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.TextSecondary
                        )
                    }
                }
            }
        } else {
            items(uiState.devices, key = { it.id }) { device ->
                DeviceRow(device) { viewModel.navigateTo("device/${device.id}") }
                Spacer(Modifier.height(12.dp))
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
        item {
            HaloGlassButton(
                text = "Add device",
                onClick = { viewModel.navigateTo("discovery") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun DeviceRow(device: GenericDeviceUiState, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HaloGlassColors.GlassElevated)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.name, style = HaloTypography.titleMedium, color = HaloGlassColors.TextPrimary)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                if (device.isConnected) HaloGlassColors.Success else HaloGlassColors.TextMuted,
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(device.connectionStateText, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                }
            }
            Text("›", style = HaloTypography.titleLarge, color = HaloGlassColors.TextMuted)
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Battery: ${device.battery}", style = HaloTypography.labelSmall, color = HaloGlassColors.TextPrimary)
            Text(if (device.autoConnect) "Auto-connect" else "Manual", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }

        if (device.healthWarning != null) {
            Spacer(Modifier.height(8.dp))
            Text(device.healthWarning, style = HaloTypography.labelSmall, color = HaloGlassColors.Warning)
        }
        if (device.isBatteryStale || !device.isConnected) {
            Spacer(Modifier.height(4.dp))
            Text(device.batteryAgeText, style = HaloTypography.labelSmall, color = HaloGlassColors.Warning)
        }
    }
}
