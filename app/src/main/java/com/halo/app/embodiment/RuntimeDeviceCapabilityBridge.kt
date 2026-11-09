package com.halo.app.embodiment

import com.halo.runtime.cognition.device.DeviceCapability
import com.halo.runtime.cognition.device.RuntimeDevice
import com.halo.runtime.cognition.device.RuntimeDeviceSource
import com.halo.runtime.cognition.device.kind
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.runtime.embodiment.*
import com.halo.sdk.runtime.wearables.NativeStage
import com.halo.sdk.runtime.wearables.NativeWearableSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Live session capabilities, never capabilities inferred solely from a brand name. */
class RuntimeDeviceCapabilityBridge(
    private val registry: DeviceRegistry,
    private val sessions: DeviceSessionManager,
    private val catalogue: CapabilityCatalogue,
    private val scope: CoroutineScope,
) : RuntimeDeviceSource {
    private val mutableDevices = MutableStateFlow<List<RuntimeDevice>>(emptyList())
    override val devices: StateFlow<List<RuntimeDevice>> = mutableDevices.asStateFlow()
    private val projection = Mutex()
    private var publishedDeviceIds = emptySet<String>()

    init {
        scope.launch { sessions.stateRevision.collect { updateDeviceCapabilities() } }
        scope.launch {
            sessions.activeSessions.collectLatest { active ->
                val native = active.filterIsInstance<NativeWearableSession>()
                if (native.isNotEmpty()) {
                    combine(native.map { session -> session.driver.state.map {
                        Triple(it.stage, it.cameraAvailable, it.aiPhotoAvailable)
                    }.distinctUntilChanged() }) { Unit }.collect { updateDeviceCapabilities() }
                }
            }
        }
    }

    suspend fun updateDeviceCapabilities() = projection.withLock {
        val saved = registry.getSavedDevices()
        val liveIds = saved.map { it.identity.deviceId }.toSet()
        (publishedDeviceIds - liveIds).forEach { catalogue.removeScope("device:$it") }
        val deviceList = saved.map { record ->
            val id = record.identity.deviceId
            val label = record.identity.displayName.ifBlank { id }
            val session = sessions.getSession(id)
            val state = session?.state?.value
            val connected = state in setOf(SessionState.READY, SessionState.CONNECTED, SessionState.HANDSHAKE,
                SessionState.CAPABILITY_SYNC, SessionState.STATE_SYNC)
            val ready = state == SessionState.READY
            val capabilities = if (ready) capabilitiesFor(record.descriptor.kind, session) else emptySet()
            if (!ready || capabilities.isEmpty()) catalogue.removeScope("device:$id")
            else catalogue.replaceScope("device:$id", capabilities.map { capability ->
                CapabilityInstance(id = CapabilityId(capability.kind), displayName = capabilityLabel(capability, label),
                    description = "Available from $label", category = categoryForCapability(capability),
                    sourceKind = CapabilitySourceKind.DEVICE, ownerId = id, endpointId = id,
                    availability = CapabilityAvailability.AVAILABLE)
            })
            RuntimeDevice(id = id, kind = record.descriptor.kind.name.lowercase(), displayName = label,
                connected = connected, capabilities = capabilities)
        }
        publishedDeviceIds = liveIds
        mutableDevices.value = deviceList
    }

    private fun capabilitiesFor(kind: DeviceKind, session: EmbodimentSession?): Set<DeviceCapability> {
        if (session is NativeWearableSession) {
            val snapshot = session.driver.state.value
            if (snapshot.stage != NativeStage.READY) return emptySet()
            return buildSet {
                if (snapshot.aiPhotoAvailable) add(DeviceCapability.Camera())
                if (snapshot.cameraAvailable == true) add(DeviceCapability.MediaCapture(photoSupported = true, videoSupported = true))
                // Classic audio and Wi-Fi transfer are separate capabilities. They
                // are not advertised until their actual routing/transfer owner exists.
            }
        }
        return when (kind) {
            DeviceKind.HEYCYAN_GLASSES -> setOf(DeviceCapability.AudioInput(), DeviceCapability.AudioOutput(),
                DeviceCapability.Camera(), DeviceCapability.MediaCapture(photoSupported = true, videoSupported = true),
                DeviceCapability.MediaTransfer())
            DeviceKind.BUDDYBOT -> setOf(DeviceCapability.Mobility(driveSupported = true),
                DeviceCapability.Manipulator(degreesOfFreedom = 3), DeviceCapability.Sensor("range"))
            else -> emptySet()
        }
    }

    private fun capabilityLabel(capability: DeviceCapability, deviceName: String): String = when (capability) {
        is DeviceCapability.AudioInput -> "Microphone · $deviceName"
        is DeviceCapability.AudioOutput -> "Audio output · $deviceName"
        is DeviceCapability.Camera -> "Still camera · $deviceName"
        is DeviceCapability.Display -> "Display · $deviceName"
        is DeviceCapability.ButtonInput -> "Buttons · $deviceName"
        is DeviceCapability.TouchInput -> "Touch input · $deviceName"
        is DeviceCapability.MediaCapture -> "Media capture · $deviceName"
        is DeviceCapability.MediaTransfer -> "Media transfer · $deviceName"
        is DeviceCapability.Mobility -> "Mobility · $deviceName"
        is DeviceCapability.Manipulator -> "Manipulator · $deviceName"
        is DeviceCapability.Sensor -> "${capability.kind.replaceFirstChar { it.uppercase() }} sensor · $deviceName"
    }

    private fun categoryForCapability(capability: DeviceCapability): ToolCategory = when (capability) {
        is DeviceCapability.AudioInput, is DeviceCapability.Camera, is DeviceCapability.ButtonInput,
        is DeviceCapability.TouchInput, is DeviceCapability.Sensor -> ToolCategory.PERCEPTION
        is DeviceCapability.Mobility, is DeviceCapability.Manipulator -> ToolCategory.DEVICE_CONTROL
        is DeviceCapability.AudioOutput, is DeviceCapability.Display, is DeviceCapability.MediaCapture,
        is DeviceCapability.MediaTransfer -> ToolCategory.MEDIA
    }
}
