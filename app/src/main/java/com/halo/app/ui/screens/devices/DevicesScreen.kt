package com.halo.app.ui.screens.devices

import com.halo.app.language.uiText
import com.halo.app.R
import androidx.compose.ui.res.stringResource
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloEmptyState
import com.halo.app.ui.design.HaloHeaderAction
import com.halo.app.ui.design.HaloFocusAction
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

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
import androidx.compose.material3.MaterialTheme
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
import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.sdk.runtime.embodiment.DeviceRecord
import com.halo.sdk.runtime.embodiment.SessionState
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

enum class PhotoActionState { READY, CAPTURING, REQUESTED, FAILED }

/** This tracks only the explicit reconnect request. READY still comes solely
 * from the device-session owner; REQUESTED never means connected.
 */
internal enum class DeviceReconnectState { IDLE, TRYING, REQUESTED, UNAVAILABLE }

internal fun deviceCanRetryFromList(
    device: GenericDeviceUiState,
): Boolean = deviceListGroup(device.sessionState) == DeviceListGroup.SAVED &&
    device.autoConnect &&
    device.kind in setOf(DeviceKind.HEYCYAN_GLASSES, DeviceKind.BUDDYBOT)

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
    val sessionState: com.halo.sdk.runtime.embodiment.SessionState? = null,
    val mediaPhotoCount: Int? = null,
    val hasLiveGlasses: Boolean = false,
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
    private val mutableReconnectState = MutableStateFlow<Map<String, DeviceReconnectState>>(emptyMap())
    internal val reconnectState: StateFlow<Map<String, DeviceReconnectState>> = mutableReconnectState.asStateFlow()
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
                    // Physical transport connected does not mean safe/ready for camera,
                    // microphone, motor or button commands. Never grant UI actions
                    // until the capability and state handshake reaches READY.
                    val isConnected = deviceReadyForCommands(sessionState)
                    val liveHeyCyan = deviceHasMatchedHeyCyanTelemetry(record, sessionState, driverState)
                    // BuddyBot also has one active telemetry owner. Its state
                    // must never be displayed for a different saved robot.
                    val liveRobot = isRobot && isConnected &&
                        buddyBotHost?.deviceId == record.identity.deviceId
                    val stateText = sessionState?.toDisplayText() ?: "Saved • no session"
                    // The HeyCyan driver is one global stream. Never attribute it
                    // to a different saved pair or to a disconnected session.
                    val battery = driverState.battery.takeIf { liveHeyCyan }
                    val batteryText = if (isRobot) {
                        buddyBotTelemetry.batteryPercent?.takeIf { liveRobot }?.let { "$it%" } ?: "Unknown"
                    } else {
                        battery?.let { "${it.level}%${if (it.charging) " • Charging" else ""}" } ?: "Unknown"
                    }
                    val batteryAgeMs = if (isRobot) {
                        if (liveRobot && buddyBotTelemetry.lastUpdateMs > 0L)
                            now - buddyBotTelemetry.lastUpdateMs else Long.MAX_VALUE
                    } else {
                        battery?.let { now - it.timestampMs } ?: Long.MAX_VALUE
                    }
                    val healthWarning = if (isRobot && liveRobot) {
                        if (!buddyBotTelemetry.safetyFault.isNullOrEmpty() && buddyBotTelemetry.safetyFault != "none") {
                            "Fault: ${buddyBotTelemetry.safetyFault}"
                        } else if (buddyBotTelemetry.forwardMotionBlocked) {
                            "Obstacle Blocked"
                        } else null
                    } else if (liveHeyCyan) {
                        when (driverState.healthState) {
                            com.halo.drivers.heycyan.protocol.DeviceHealthState.WARNING_MEMORY_INSUFFICIENT -> "Device memory insufficient"
                            com.halo.drivers.heycyan.protocol.DeviceHealthState.UNBOUND -> "Device is unbound"
                            com.halo.drivers.heycyan.protocol.DeviceHealthState.ERROR -> "Device error detected"
                            else -> null
                        }
                    } else null
                    val healthStateText = when {
                        liveRobot -> buddyBotTelemetry.safetyState ?: "Unknown"
                        liveHeyCyan -> driverState.healthState.name
                        else -> "Unknown"
                    }
                    GenericDeviceUiState(
                        id = record.identity.deviceId,
                        name = record.identity.displayName,
                        kind = record.descriptor.kind,
                        isConnected = isConnected,
                        sessionState = sessionState,
                        mediaPhotoCount = driverState.mediaCounts?.photos?.takeIf { liveHeyCyan },
                        hasLiveGlasses = liveHeyCyan,
                        connectionStateText = stateText,
                        autoConnect = record.autoConnect,
                        battery = batteryText,
                        batteryAgeText = if (isRobot) {
                            if (buddyBotTelemetry.lastUpdateMs > 0L) "Updated ${formatLastSeen(buddyBotTelemetry.lastUpdateMs)}" else "No data"
                        } else {
                            if (battery != null) "Updated ${formatLastSeen(battery.timestampMs)}" else "No data"
                        },
                        isBatteryStale = (liveRobot || liveHeyCyan) && batteryAgeMs > STALE_BATTERY_MS,
                        firmware = if (liveHeyCyan) {
                            driverState.version?.firmware ?: record.firmwareVersion ?: "Unknown"
                        } else record.firmwareVersion ?: "Unknown",
                        lastSeen = if (isConnected) "Now" else if (record.lastSeenMs > 0L) "Last seen ${formatLastSeen(record.lastSeenMs)}" else "Never",
                        photoState = photoStates[record.identity.deviceId] ?: PhotoActionState.READY,
                        videoState = if (liveHeyCyan) driverState.videoRecordingState
                            else com.halo.drivers.heycyan.protocol.VideoRecordingState.UNKNOWN,
                        audioState = if (liveHeyCyan) driverState.audioRecordingState
                            else com.halo.drivers.heycyan.protocol.AudioRecordingState.IDLE,
                        healthStateText = healthStateText,
                        healthWarning = healthWarning,
                        wearStateText = when {
                            liveRobot -> "Active"
                            liveHeyCyan -> driverState.wearState.name
                            else -> "Unknown"
                        },
                        volumeText = if (liveHeyCyan)
                            driverState.volumeState?.let { "Music ${it.musicCurrent}, Call ${it.callCurrent}" } ?: "Unknown"
                            else "Unknown",
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
                        // A command acknowledgement does not establish media receipt.
                        _photoStates.value = _photoStates.value + (deviceId to PhotoActionState.REQUESTED)
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
            if (!ownsReadyGlasses(deviceId)) return@launch
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
    private fun ownsReadyGlasses(deviceId: String): Boolean {
        val driver = heyCyanDriverProvider()
        return glassesOwnsReadyCapture(deviceId, driver?.activeDeviceId,
            sessionManager.getSession(deviceId)?.state?.value) &&
            driver?.deviceState?.value?.connection == HeyCyanConnectionState.READY
    }

    fun syncMedia(deviceId: String) { viewModelScope.launch(Dispatchers.IO) {
        if (ownsReadyGlasses(deviceId)) captures()?.sync()
    } }
    fun refreshMedia(deviceId: String) { viewModelScope.launch {
        if (ownsReadyGlasses(deviceId)) heyCyanDriverProvider()?.capabilityController
            ?.execute(com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation.GetMediaInventory)
    } }
    suspend fun capturePhotoForReview(deviceId: String): Result<Unit> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (!ownsReadyGlasses(deviceId)) return@withContext Result.failure(IllegalStateException("Glasses unavailable"))
        try {
            captures()?.captureAiThumbnail(quality = thumbnailQuality.value, publishForInference = false, review = true)
                ?.map { Unit } ?: Result.failure(IllegalStateException("Capture unavailable"))
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
    fun takePhoto(deviceId: String) {
        if (!ownsReadyGlasses(deviceId)) return
        captures()?.let { capture -> viewModelScope.launch(Dispatchers.IO) {
            if (ownsReadyGlasses(deviceId)) capture.capture()
        }; return }

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

    fun retrySavedDevice(deviceId: String) {
        if (mutableReconnectState.value[deviceId] == DeviceReconnectState.TRYING) return
        val record = _uiState.value.devices.firstOrNull { it.id == deviceId }
        if (record == null || !deviceCanRetryFromList(record)) return
        viewModelScope.launch {
            mutableReconnectState.update { it + (deviceId to DeviceReconnectState.TRYING) }
            val accepted = try {
                autoConnectCoordinator?.retryDevice(deviceId) ?: false
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) { false }
            mutableReconnectState.update { it + (deviceId to
                if (accepted) DeviceReconnectState.REQUESTED else DeviceReconnectState.UNAVAILABLE) }
            if (accepted) {
                // Connection result is solely the session's READY state. Clear
                // the temporary request hint rather than claiming success.
                kotlinx.coroutines.delay(5_000L)
                mutableReconnectState.update { current ->
                    if (current[deviceId] == DeviceReconnectState.REQUESTED)
                        current + (deviceId to DeviceReconnectState.IDLE)
                    else current
                }
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
@OptIn(ExperimentalLayoutApi::class)
fun DevicesScreen(viewModel: DevicesViewModel, onTestAudio: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val reconnectStates by viewModel.reconnectState.collectAsStateWithLifecycle()
    val accessories by com.halo.app.di.ManualDependencyGraph.audioAccessoryRegistry.accessories.collectAsStateWithLifecycle()
    val visibleAccessories = accessories.filterNot { com.halo.app.di.ManualDependencyGraph.heyCyanAudioPairingCoordinator.matchesAudioFacet(it) }
    val wear by com.halo.app.di.ManualDependencyGraph.wearEndpointBridge.endpoints.collectAsStateWithLifecycle()
    var phoneDetails by rememberSaveable { mutableStateOf(false) }
    var technicalDetails by rememberSaveable { mutableStateOf(false) }
    val phoneAbilities = groupPhoneCapabilities(uiState.hostCapabilities)
    val reportedPhoneAbilities = phoneAbilities.count { it.hasAvailableCapability }
    val noDevices = uiState.devices.isEmpty() && visibleAccessories.isEmpty() && wear.isEmpty()
    LazyColumn(Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            HaloScreenHeader(uiText("Devices"), trailing = {
                if (!noDevices) {
                    androidx.compose.material3.IconButton(
                        modifier = Modifier.size(48.dp),
                        onClick = { viewModel.navigateTo("discovery") },
                    ) {
                        HaloIcon(HaloGlyph.ADD_DEVICE,
                            contentDescription = uiText("Add device"),
                            modifier = Modifier.size(24.dp))
                    }
                }
            })
        }
        if (noDevices) item {
            HaloEmptyState(uiText("No saved devices"), uiText("halo works on this phone. Add supported glasses when you want their controls. Headphones are connected through Android Bluetooth settings."),
                HaloGlyph.ADD_DEVICE, action = HaloHeaderAction(uiText("Add device")) { viewModel.navigateTo("discovery") })
        }
        listOf(DeviceListGroup.READY, DeviceListGroup.CONNECTING, DeviceListGroup.SAVED).forEach { section ->
            val group = uiState.devices.filter { deviceListGroup(it.sessionState) == section }
            if (group.isNotEmpty()) item {
                Text(uiText(section.label), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }
            items(group, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    onClick = { viewModel.navigateTo("device/${device.id}") },
                    reconnect = reconnectStates[device.id] ?: DeviceReconnectState.IDLE,
                    onRetry = { viewModel.retrySavedDevice(device.id) },
                )
            }
        }
        item(key = "phone_capabilities") {
            com.halo.app.ui.workspace.HaloWorkspaceCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HaloIcon(HaloGlyph.PHONE, contentDescription = null, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(uiText("This phone"),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        Text(uiText("This phone manages your conversation and device connections."),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("$reportedPhoneAbilities / ${phoneAbilities.size} " + stringResource(R.string.brand_phone_abilities),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.TextButton(onClick = { phoneDetails = !phoneDetails },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText(if (phoneDetails) "Hide details" else "Details"))
                }
                if (phoneDetails) {
                    // The runtime reports which capabilities exist, NOT effective permission
                    // policy. This screen must never claim an action is allowed without consent.
                    phoneAbilities.forEach { ability ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            HaloIcon(ability.group.icon, contentDescription = null,
                                modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(ability.group.title),
                                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                                Text(stringResource(ability.group.summary),
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(if (ability.hasAvailableCapability)
                                        R.string.brand_phone_available else R.string.brand_phone_not_reported),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                    color = if (ability.hasAvailableCapability)
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text(stringResource(R.string.brand_phone_access_note),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)

                }
            }
        }
        if (visibleAccessories.isNotEmpty()) {
            item { Text(uiText("System audio"), style = androidx.compose.material3.MaterialTheme.typography.titleMedium) }
            items(visibleAccessories) { accessory ->
                com.halo.app.ui.workspace.HaloWorkspaceCard {
                    Text(accessory.label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(uiText("Connected") + " · " + listOfNotNull(
                        uiText("Output").takeIf { accessory.canOutput }, uiText("Microphone").takeIf { accessory.canInput },
                        uiText("Calls").takeIf { accessory.communicationCapable }).joinToString(" · "))
                    HaloActionButton(onClick = onTestAudio, style = HaloActionStyle.SECONDARY) { Text(uiText("Test audio output")) }
                    Text(uiText("Your phone manages the buttons and volume."))
                    if (accessory.persistence == com.halo.runtime.cognition.continuity.EndpointPersistence.SESSION_ONLY)
                        Text(uiText("Available while connected"))
                }
            }
        }
        if (wear.isNotEmpty()) {
            item { Text(uiText("Wear OS"), style = androidx.compose.material3.MaterialTheme.typography.titleMedium) }
            items(wear) { endpoint -> com.halo.app.ui.workspace.HaloWorkspaceCard {
                Text(endpoint.label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(uiText("Screen and controls"))
            } }
        }

    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DeviceCard(
    device: GenericDeviceUiState,
    onClick: () -> Unit,
    reconnect: DeviceReconnectState = DeviceReconnectState.IDLE,
    onRetry: () -> Unit = {},
) {
    val statusLabel = deviceConnectionLabel(
        deviceListGroup(device.sessionState),
        needsAttention = device.healthWarning != null,
        error = device.connectionStateText.equals("Error", ignoreCase = true),
    )
    val preparing = device.sessionState in setOf(SessionState.CONNECTED, SessionState.HANDSHAKE, SessionState.CAPABILITY_SYNC, SessionState.STATE_SYNC)
    val displayedStatus = if (preparing && device.healthWarning == null) "Connected · getting ready" else statusLabel
    val statusColor = when {
        device.healthWarning != null -> com.halo.app.ui.theme.HaloWarning
        device.connectionStateText.equals("Error", ignoreCase = true) ->
            androidx.compose.material3.MaterialTheme.colorScheme.error
        device.isConnected -> androidx.compose.material3.MaterialTheme.colorScheme.primary
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    }

    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (device.isConnected)
            androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
            else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (device.isConnected) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            else androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = if (device.isConnected) androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
                        else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(if (device.isConnected) 56.dp else 48.dp)
                ) {
                    val glyph = when (device.kind) {
                        DeviceKind.HEYCYAN_GLASSES -> HaloGlyph.GLASSES
                        DeviceKind.BUDDYBOT -> HaloGlyph.ROBOT
                        else -> HaloGlyph.DEVICE
                    }
                    HaloIcon(
                        glyph = glyph,
                        contentDescription = device.name,
                        modifier = Modifier.padding(if (device.isConnected) 14.dp else 10.dp)
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
                            text = uiText(displayedStatus),
                            fontSize = 14.sp,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = uiText("Open device"),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // The list is for identity and connection, not a second
            // diagnostics page. Detail owns firmware, all capabilities,
            // resource leases and the saved auto-connect switch.
            val facts = deviceListSecondaryFacts(device)
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    facts.forEach { (label, value) ->
                        Text(uiText(label) + ": " + value,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (deviceCanRetryFromList(device)) {
                Spacer(Modifier.height(8.dp))
                when (reconnect) {
                    DeviceReconnectState.REQUESTED -> Text(uiText("Connection requested"),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        androidx.compose.material3.TextButton(
                            onClick = onRetry,
                            enabled = reconnect != DeviceReconnectState.TRYING,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(uiText(if (reconnect == DeviceReconnectState.TRYING)
                                "Connecting…" else "Try reconnecting"))
                        }
                        if (reconnect == DeviceReconnectState.UNAVAILABLE) {
                            Text(uiText("Could not start the connection. Check Bluetooth and try again."),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Live capabilities and active leases remain on the real device
            // detail screen, where their connection status can be reviewed.

            if (device.healthWarning != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = device.healthWarning,
                    fontSize = 14.sp,
                    color = com.halo.app.ui.theme.HaloWarning
                )
            } else if (device.isConnected && device.isBatteryStale) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = uiText("Battery data may be stale: %1\$s", device.batteryAgeText),
                    fontSize = 14.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Saved-but-disconnected hardware does not have live battery information.
 * Keep the Devices hub at the compact Round 2 hierarchy, while the matching
 * device detail page continues to expose diagnostics and preferences.
 */
internal fun deviceListSecondaryFacts(device: GenericDeviceUiState): List<Pair<String,String>> =
    if (device.isConnected) {
        listOfNotNull(
            device.battery.takeUnless { it == "Unknown" }?.let { "Battery" to it },
            device.lastSeen.takeUnless { it == "Now" || it == "Never" }?.let { "Seen" to it },
        )
    } else {
        listOfNotNull(
            device.lastSeen.takeUnless { it == "Never" }?.let { "Seen" to it },
        )
    }

@Composable
private fun DeviceMeta(label: String, value: String) {
    Column {
        Text(
            text = label,
            fontSize = 14.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

/** A saved identity is not evidence that this driver's runtime telemetry
 * came from that device. Both target BLE identity and READY handshake must
 * match before battery / health / recording labels can be attributed.
 */
internal fun deviceHasMatchedHeyCyanTelemetry(
    record: DeviceRecord,
    sessionState: SessionState?,
    driver: HeyCyanDeviceState,
): Boolean = record.descriptor.kind == DeviceKind.HEYCYAN_GLASSES &&
    sessionState == SessionState.READY &&
    driver.connection == HeyCyanConnectionState.READY &&
    driver.transportIdentity?.controlBleAddress
        ?.equals(record.identity.deviceId, ignoreCase = true) == true

/** A BLE link is not necessarily a device ready for commands. */
internal enum class DeviceListGroup(val label: String) {
    READY("Ready to use"), CONNECTING("Connecting"), SAVED("Saved")
}

internal fun deviceListGroup(state: com.halo.sdk.runtime.embodiment.SessionState?): DeviceListGroup = when (state) {
    com.halo.sdk.runtime.embodiment.SessionState.READY -> DeviceListGroup.READY
    com.halo.sdk.runtime.embodiment.SessionState.CONNECTING,
    com.halo.sdk.runtime.embodiment.SessionState.CONNECTED,
    com.halo.sdk.runtime.embodiment.SessionState.HANDSHAKE,
    com.halo.sdk.runtime.embodiment.SessionState.CAPABILITY_SYNC,
    com.halo.sdk.runtime.embodiment.SessionState.STATE_SYNC,
    com.halo.sdk.runtime.embodiment.SessionState.DISCONNECTING -> DeviceListGroup.CONNECTING
    else -> DeviceListGroup.SAVED
}

/** Human-facing state always comes from the authenticated session group, not raw
 * transport diagnostics. The original detail stays accessible on the device page. */
internal fun deviceConnectionLabel(
    group: DeviceListGroup,
    needsAttention: Boolean,
    error: Boolean,
): String = when {
    needsAttention -> "Needs attention"
    error -> "Connection failed"
    else -> group.label
}

/** Command permissions require semantic readiness, not just a BLE transport. */
internal fun deviceReadyForCommands(state: com.halo.sdk.runtime.embodiment.SessionState?): Boolean =
    state == com.halo.sdk.runtime.embodiment.SessionState.READY
