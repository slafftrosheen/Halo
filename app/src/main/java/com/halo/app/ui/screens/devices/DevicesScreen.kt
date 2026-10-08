package com.halo.app.ui.screens.devices

import android.content.IntentSender
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.ChevronRight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.embodiment.BuddyBotHost
import com.halo.app.embodiment.BuddyBotTelemetry
import com.halo.app.embodiment.DeviceAutoConnectCoordinator
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.state.toDisplayText
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceState
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.embodiment.DeviceKind
import kotlinx.coroutines.Dispatchers
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
    val kind: com.halo.sdk.runtime.embodiment.DeviceKind,
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
    val volumeText: String = "Normal",
    val capabilityLabels: List<String> = emptyList(),
    val activeResourceLabels: List<String> = emptyList(),
)

data class DevicesUiState(
    val devices: List<GenericDeviceUiState> = emptyList(),
    val hostCapabilities: List<String> = emptyList(),
    val hostActiveResources: List<String> = emptyList(),
)

class DevicesViewModel(
    val deviceRegistry: com.halo.sdk.runtime.embodiment.DeviceRegistry,
    val sessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager,
    private val eventBus: com.halo.sdk.runtime.EventBus,
    private val snapshotSource: com.halo.sdk.runtime.RuntimeSnapshotSource,
    private val heyCyanDriverProvider: () -> RealHeyCyanDriver?,
    val buddyBotHost: BuddyBotHost? = null,
    private val autoConnectCoordinator: DeviceAutoConnectCoordinator? = null,
    private val captures: () -> com.halo.app.capture.HeyCyanCaptureCoordinator? = { null },
    private val audioPairingCoordinator: com.halo.app.audio.HeyCyanAudioPairingCoordinator? = null,
    private val capabilityCatalogue: com.halo.runtime.cognition.tools.CapabilityCatalogueSource? = null,
    private val resourceLeaseManager: com.halo.sdk.runtime.resource.ResourceLeaseManager? = null,
    private val storage: Storage? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()
    private val _photoStates = MutableStateFlow<Map<String, PhotoActionState>>(emptyMap())
    private val activePhotoIntents = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val _registryRevision = MutableStateFlow(0L)
    private val _navigationEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val navigationEvents = _navigationEvents.asSharedFlow()
    val audioPairingState: StateFlow<com.halo.app.audio.HeyCyanAudioPairingState> =
        audioPairingCoordinator?.state ?: MutableStateFlow(com.halo.app.audio.HeyCyanAudioPairingState.Unknown)
    val audioConfirmationRequests = audioPairingCoordinator?.confirmationRequests
        ?: MutableSharedFlow<IntentSender>()

    private val _thumbnailQuality = MutableStateFlow(storage?.getString("heycyan_ai_thumbnail_quality")?.toIntOrNull()?.coerceIn(0, 5) ?: 3)
    val thumbnailQuality: StateFlow<Int> = _thumbnailQuality.asStateFlow()

    fun setThumbnailQuality(quality: Int) {
        val valid = quality.coerceIn(0, 5)
        _thumbnailQuality.value = valid
        viewModelScope.launch(Dispatchers.IO) {
            storage?.putString("heycyan_ai_thumbnail_quality", valid.toString())
        }
    }

    fun resetAudioAssociation(deviceId: String) {
        audioPairingCoordinator?.resetAssociation(deviceId)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            deviceRegistry.getSavedDevices()
                .firstOrNull { it.descriptor.kind == com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES }
                ?.let { audioPairingCoordinator?.restore(it.identity.deviceId) }
            val driver = heyCyanDriverProvider()
            val heyCyanStateFlow = driver?.deviceState ?: MutableStateFlow(HeyCyanDeviceState())
            val buddyBotTelemetryFlow = buddyBotHost?.telemetry ?: MutableStateFlow(
                BuddyBotTelemetry()
            )
            val deviceDriversFlow = combine(heyCyanStateFlow, buddyBotTelemetryFlow) { heyCyan, buddy ->
                Pair(heyCyan, buddy)
            }

            val fabricFlow = combine(
                capabilityCatalogue?.snapshot ?: MutableStateFlow(
                    com.halo.runtime.cognition.tools.CapabilityCatalogueSnapshot(emptyList(), 0L)
                ),
                resourceLeaseManager?.snapshotState ?: MutableStateFlow(
                    com.halo.sdk.runtime.resource.ResourceLeaseSnapshot(emptyList(), emptyList())
                ),
            ) { capabilities, resources -> capabilities to resources }

            val uiInputs = combine(
                _photoStates,
                _registryRevision,
                fabricFlow,
            ) { photoStates, revision, fabric ->
                Triple(photoStates, revision, fabric)
            }

            combine(
                sessionManager.activeSessions,
                sessionManager.stateRevision,
                deviceDriversFlow,
                uiInputs,
            ) { activeSessions, _, (driverState, buddyBotTelemetry), (photoStates, _, fabric) ->
                val (capabilitySnapshot, resourceSnapshot) = fabric
                val now = System.currentTimeMillis()
                val devices = deviceRegistry.getSavedDevices().map { record ->
                    val isRobot = record.descriptor.kind == com.halo.sdk.runtime.embodiment.DeviceKind.BUDDYBOT
                    val session = activeSessions.find { it.identity.deviceId == record.identity.deviceId }
                    val sessionState = session?.state?.value
                    val isConnected = sessionState in setOf(
                        com.halo.sdk.runtime.embodiment.SessionState.CONNECTED,
                        com.halo.sdk.runtime.embodiment.SessionState.HANDSHAKE,
                        com.halo.sdk.runtime.embodiment.SessionState.CAPABILITY_SYNC,
                        com.halo.sdk.runtime.embodiment.SessionState.STATE_SYNC,
                        com.halo.sdk.runtime.embodiment.SessionState.READY
                    )
                    val stateText = sessionState?.toDisplayText() ?: "Saved • no session"
                    val battery = driverState.battery
                    val batteryText = if (isRobot) {
                        buddyBotTelemetry.batteryPercent?.let { "$it%" } ?: "Unknown"
                    } else {
                        battery?.let { "${it.level}%${if (it.charging) " • Charging" else ""}" } ?: "Unknown"
                    }
                    val batteryAgeMs = if (isRobot) {
                        if (buddyBotTelemetry.lastUpdateMs > 0L) now - buddyBotTelemetry.lastUpdateMs else Long.MAX_VALUE
                    } else {
                        battery?.let { now - it.timestampMs } ?: Long.MAX_VALUE
                    }
                    val healthWarning = if (isRobot) {
                        if (!buddyBotTelemetry.safetyFault.isNullOrEmpty() && buddyBotTelemetry.safetyFault != "none") {
                            "Fault: ${buddyBotTelemetry.safetyFault}"
                        } else if (buddyBotTelemetry.forwardMotionBlocked) {
                            "Obstacle Blocked"
                        } else null
                    } else {
                        when (driverState.healthState) {
                            com.halo.drivers.heycyan.protocol.DeviceHealthState.WARNING_MEMORY_INSUFFICIENT -> "Device memory insufficient"
                            com.halo.drivers.heycyan.protocol.DeviceHealthState.UNBOUND -> "Device is unbound"
                            com.halo.drivers.heycyan.protocol.DeviceHealthState.ERROR -> "Device error detected"
                            else -> null
                        }
                    }
                    val healthStateText = if (isRobot) {
                        buddyBotTelemetry.safetyState ?: "Healthy"
                    } else {
                        driverState.healthState.name
                    }
                    GenericDeviceUiState(
                        id = record.identity.deviceId,
                        name = record.identity.displayName,
                        kind = record.descriptor.kind,
                        isConnected = isConnected,
                        connectionStateText = stateText,
                        autoConnect = record.autoConnect,
                        battery = batteryText,
                        batteryAgeText = if (isRobot) {
                            if (buddyBotTelemetry.lastUpdateMs > 0L) "Updated ${formatLastSeen(buddyBotTelemetry.lastUpdateMs)}" else "No data"
                        } else {
                            if (battery != null) "Updated ${formatLastSeen(battery.timestampMs)}" else "No data"
                        },
                        isBatteryStale = batteryAgeMs > STALE_BATTERY_MS,
                        firmware = if (isRobot) {
                            record.firmwareVersion ?: "Unknown"
                        } else {
                            driverState.version?.firmware ?: record.firmwareVersion ?: "Unknown"
                        },
                        lastSeen = if (isConnected) "Now" else if (record.lastSeenMs > 0L) "Last seen ${formatLastSeen(record.lastSeenMs)}" else "Never",
                        photoState = photoStates[record.identity.deviceId] ?: PhotoActionState.READY,
                        videoState = driverState.videoRecordingState,
                        audioState = driverState.audioRecordingState,
                        healthStateText = healthStateText,
                        healthWarning = healthWarning,
                        wearStateText = if (isRobot) "Active" else driverState.wearState.name,
                        volumeText = driverState.volumeState?.let { "Music ${it.musicCurrent}, Call ${it.callCurrent}" } ?: "Normal",
                        capabilityLabels = capabilitySnapshot.capabilities
                            .filter {
                                it.ownerId == record.identity.deviceId &&
                                    it.availability == com.halo.runtime.cognition.tools.CapabilityAvailability.AVAILABLE
                            }
                            .map { com.halo.app.ui.state.RuntimeUiFormatters.formatCapability(it.id) }
                            .distinct()
                            .sorted(),
                        activeResourceLabels = resourceSnapshot.active
                            .flatMap { lease -> lease.claims }
                            .filter { it.key.endpointId.value == record.identity.deviceId }
                            .map { com.halo.app.ui.state.RuntimeUiFormatters.formatResource(it.key.resourceId) }
                            .distinct()
                            .sorted(),
                    )
                }

                val hostCapabilities = capabilitySnapshot.capabilities
                    .filter {
                        it.ownerId == "host:primary" &&
                            it.availability == com.halo.runtime.cognition.tools.CapabilityAvailability.AVAILABLE
                    }
                    .map { com.halo.app.ui.state.RuntimeUiFormatters.formatCapability(it.id) }
                    .distinct()
                    .sorted()
                val hostResources = resourceSnapshot.active
                    .flatMap { lease -> lease.claims }
                    .filter { it.key.endpointId.value == "host:primary" }
                    .map { com.halo.app.ui.state.RuntimeUiFormatters.formatResource(it.key.resourceId) }
                    .distinct()
                    .sorted()

                DevicesUiState(
                    devices = devices,
                    hostCapabilities = hostCapabilities,
                    hostActiveResources = hostResources,
                )
            }.collect { state ->
                _uiState.value = state
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

    fun mediaCoordinator() = captures()
    fun restoreAudioAssociation(deviceId: String) = audioPairingCoordinator?.restore(deviceId)
    fun connectAudio(deviceId: String) {
        viewModelScope.launch {
            val driver = heyCyanDriverProvider()
            val enabled = driver?.capabilityController
                ?.execute(com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation.EnableClassicBluetooth)
                ?.isSuccess == true
            if (enabled) audioPairingCoordinator?.requestAssociation(deviceId)
            else audioPairingCoordinator?.fail("Could not enable the glasses audio radio")
        }
    }
    fun onAudioAssociationResult(resultCode: Int, data: android.content.Intent?) =
        audioPairingCoordinator?.onConfirmationResult(resultCode, data)
    fun syncMedia() { viewModelScope.launch(Dispatchers.IO) { captures()?.sync() } }
    fun refreshMedia() { viewModelScope.launch { heyCyanDriverProvider()?.capabilityController?.execute(com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation.GetMediaInventory) } }
    fun captureAiThumbnail() {
        viewModelScope.launch(Dispatchers.IO) { captures()?.captureAiThumbnail() }
    }
    fun takePhoto(deviceId: String) {
        captures()?.let { capture -> viewModelScope.launch(Dispatchers.IO) { capture.capture() }; return }

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
                if (enabled) {
                    autoConnectCoordinator?.retryDevice(deviceId)
                }
            }
        }
    }
}

@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val accessories by com.halo.app.di.ManualDependencyGraph.audioAccessoryRegistry.accessories.collectAsStateWithLifecycle()
    val visibleAccessories = accessories.filterNot { com.halo.app.di.ManualDependencyGraph.heyCyanAudioPairingCoordinator.matchesAudioFacet(it) }
    val wear by com.halo.app.di.ManualDependencyGraph.wearEndpointBridge.endpoints.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Devices",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Connected hardware and saved sessions.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Surface(
                shape = CircleShape,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp),
                onClick = { viewModel.navigateTo("discovery") }
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add device",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(11.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(18.dp),
            color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "This host",
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    "Android phone · " +
                        (uiState.hostCapabilities.take(6).joinToString(" · ").ifBlank { "Capabilities unavailable" }),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (uiState.hostActiveResources.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "In use · ${uiState.hostActiveResources.joinToString(" · ")}",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (visibleAccessories.isNotEmpty()) {
            Text("System audio", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            visibleAccessories.forEach { accessory ->
                Text(accessory.label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                Text("Connected · " + listOfNotNull("Output".takeIf { accessory.canOutput }, "Microphone".takeIf { accessory.canInput },
                    "Communication route".takeIf { accessory.communicationCapable }).joinToString(" · "),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Text("Controls · Assistant managed by Android · Media and volume: System default",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                if (accessory.persistence == com.halo.runtime.cognition.continuity.EndpointPersistence.SESSION_ONLY)
                    Text("Identity available for this connection only", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (wear.isNotEmpty()) {
            Text("Wear OS", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            wear.forEach { Text(it.label + " · Screen + controls") }
            Spacer(Modifier.height(12.dp))
        }
        if (uiState.devices.isNotEmpty()) Text("Halo devices", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        if (uiState.devices.isEmpty() && visibleAccessories.isEmpty() && wear.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No saved devices",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Pair supported hardware to make it available to Halo.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    androidx.compose.material3.Surface(
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = { viewModel.navigateTo("discovery") },
                        shape = RoundedCornerShape(14.dp),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Add device",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(true to "Connected", false to "Saved").forEach { (connected, title) ->
                    val group = uiState.devices.filter { it.isConnected == connected }
                    if (group.isNotEmpty()) item { Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium) }
                    items(group, key = { it.id }) { device ->
                        DeviceCard(device = device, onClick = { viewModel.navigateTo("device/${device.id}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: GenericDeviceUiState,
    onClick: () -> Unit
) {
    val statusColor = when {
        device.healthWarning != null -> com.halo.app.ui.theme.HaloWarning
        device.connectionStateText.equals("Error", ignoreCase = true) ->
            androidx.compose.material3.MaterialTheme.colorScheme.error
        device.isConnected -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }

    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(48.dp)
                ) {
                    val glyph = when (device.kind) {
                        DeviceKind.HEYCYAN_GLASSES -> HaloGlyph.GLASSES
                        DeviceKind.BUDDYBOT -> HaloGlyph.ROBOT
                        else -> HaloGlyph.DEVICE
                    }
                    HaloIcon(
                        glyph = glyph,
                        contentDescription = device.name,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = device.connectionStateText,
                            fontSize = 12.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open device",
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DeviceMeta("Battery", device.battery)
                DeviceMeta("Seen", device.lastSeen)
                DeviceMeta("Auto", if (device.autoConnect) "On" else "Off")
            }

            if (device.capabilityLabels.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = device.capabilityLabels.take(6).joinToString(" · "),
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (device.activeResourceLabels.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "In use · ${device.activeResourceLabels.joinToString(" · ")}",
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                )
            }

            if (device.healthWarning != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = device.healthWarning,
                    fontSize = 12.sp,
                    color = com.halo.app.ui.theme.HaloWarning
                )
            } else if (device.isBatteryStale) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Battery data may be stale · ${device.batteryAgeText}",
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeviceMeta(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}
