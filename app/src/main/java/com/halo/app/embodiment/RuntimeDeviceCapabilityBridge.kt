package com.halo.app.embodiment

import com.halo.runtime.cognition.device.DeviceCapability
import com.halo.runtime.cognition.device.RuntimeDevice
import com.halo.runtime.cognition.device.RuntimeDeviceSource
import com.halo.runtime.cognition.device.kind
import com.halo.runtime.cognition.tools.CapabilityAvailability
import com.halo.runtime.cognition.tools.CapabilityCatalogue
import com.halo.runtime.cognition.tools.CapabilityId
import com.halo.runtime.cognition.tools.CapabilityInstance
import com.halo.runtime.cognition.tools.CapabilitySourceKind
import com.halo.runtime.cognition.tools.ToolCategory
import com.halo.sdk.runtime.embodiment.DeviceKind
import com.halo.sdk.runtime.embodiment.DeviceRegistry
import com.halo.sdk.runtime.embodiment.DeviceSessionManager
import com.halo.sdk.runtime.embodiment.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Adapts connected embodiment sessions into the generic Runtime capability
 * fabric. Vendor/protocol evidence remains in each driver; this bridge only
 * advertises abilities the current Halo host can actually address.
 */
class RuntimeDeviceCapabilityBridge(
    private val registry: DeviceRegistry,
    private val sessions: DeviceSessionManager,
    private val catalogue: CapabilityCatalogue,
    private val scope: CoroutineScope,
) : RuntimeDeviceSource {

    private val _devices = MutableStateFlow<List<RuntimeDevice>>(emptyList())
    override val devices: StateFlow<List<RuntimeDevice>> = _devices.asStateFlow()

    init {
        scope.launch {
            sessions.stateRevision.collect { updateDeviceCapabilities() }
        }
    }

    suspend fun updateDeviceCapabilities() {
        val saved = registry.getSavedDevices()
        val deviceList = mutableListOf<RuntimeDevice>()

        for (record in saved) {
            val deviceId = record.identity.deviceId
            val displayName = record.identity.displayName.ifBlank { deviceId }
            val sessionState = sessions.getSession(deviceId)?.state?.value
            val connected = sessionState in setOf(
                SessionState.READY,
                SessionState.CONNECTED,
                SessionState.HANDSHAKE,
                SessionState.CAPABILITY_SYNC,
                SessionState.STATE_SYNC,
            )

            val capabilities = if (connected) capabilitiesFor(record.descriptor.kind) else emptySet()
            deviceList += RuntimeDevice(
                id = deviceId,
                kind = record.descriptor.kind.name.lowercase(),
                displayName = displayName,
                connected = connected,
                capabilities = capabilities,
            )

            val scopeId = "device:$deviceId"
            if (!connected) {
                catalogue.removeScope(scopeId)
                continue
            }

            catalogue.replaceScope(
                scopeId,
                capabilities.map { capability ->
                    CapabilityInstance(
                        id = CapabilityId(capability.kind),
                        displayName = capabilityLabel(capability, displayName),
                        description = "Available from $displayName",
                        category = categoryForCapability(capability),
                        sourceKind = CapabilitySourceKind.DEVICE,
                        ownerId = deviceId,
                        endpointId = deviceId,
                        availability = CapabilityAvailability.AVAILABLE,
                    )
                },
            )
        }

        _devices.value = deviceList
    }

    private fun capabilitiesFor(kind: DeviceKind): Set<DeviceCapability> = when (kind) {
        DeviceKind.HEYCYAN_GLASSES -> setOf(
            DeviceCapability.AudioInput(),
            DeviceCapability.AudioOutput(),
            DeviceCapability.Camera(),
            DeviceCapability.MediaCapture(photoSupported = true, videoSupported = true),
            DeviceCapability.MediaTransfer(),
        )

        DeviceKind.BUDDYBOT -> setOf(
            // Keep this list tied to the current Halo BuddyBot host contract.
            // Camera/audio are not advertised until the host actually exposes them.
            DeviceCapability.Mobility(driveSupported = true),
            DeviceCapability.Manipulator(degreesOfFreedom = 3),
            DeviceCapability.Sensor("range"),
        )

        DeviceKind.UNKNOWN -> emptySet()
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
        is DeviceCapability.AudioInput,
        is DeviceCapability.Camera,
        is DeviceCapability.ButtonInput,
        is DeviceCapability.TouchInput,
        is DeviceCapability.Sensor -> ToolCategory.PERCEPTION
        is DeviceCapability.Mobility,
        is DeviceCapability.Manipulator -> ToolCategory.DEVICE_CONTROL
        is DeviceCapability.AudioOutput,
        is DeviceCapability.Display,
        is DeviceCapability.MediaCapture,
        is DeviceCapability.MediaTransfer -> ToolCategory.MEDIA
    }
}
