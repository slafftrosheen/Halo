package com.halo.app.embodiment

import com.halo.runtime.cognition.device.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.runtime.embodiment.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
            sessions.stateRevision.collect {
                updateDeviceCapabilities()
            }
        }
    }

    suspend fun updateDeviceCapabilities() {
        val saved = registry.getSavedDevices()
        val deviceList = mutableListOf<RuntimeDevice>()

        for (record in saved) {
            val deviceId = record.identity.deviceId
            val deviceType = record.identity.deviceType
            val displayName = record.identity.displayName.ifBlank { deviceId }
            val session = sessions.getSession(deviceId)

            val isConnected = session?.state?.value in setOf(
                SessionState.READY,
                SessionState.CONNECTED,
                SessionState.HANDSHAKE,
                SessionState.CAPABILITY_SYNC,
                SessionState.STATE_SYNC
            )

            val isGlasses = deviceType.contains("heycyan", ignoreCase = true) || deviceType.contains("glasses", ignoreCase = true)
            val isBuddyBot = deviceType.contains("buddybot", ignoreCase = true) || deviceType.contains("robot", ignoreCase = true)

            val caps = mutableSetOf<DeviceCapability>()
            if (isConnected) {
                caps.add(DeviceCapability.AudioInput())
                caps.add(DeviceCapability.AudioOutput())
                if (isGlasses) {
                    caps.add(DeviceCapability.Camera())
                    caps.add(DeviceCapability.MediaCapture())
                    caps.add(DeviceCapability.MediaTransfer())
                } else if (isBuddyBot) {
                    caps.add(DeviceCapability.Mobility(driveSupported = true))
                    caps.add(DeviceCapability.Manipulator(degreesOfFreedom = 3))
                    caps.add(DeviceCapability.Sensor("range"))
                }
            }

            val runtimeDev = RuntimeDevice(
                id = deviceId,
                kind = deviceType.lowercase(),
                displayName = displayName,
                connected = isConnected,
                capabilities = caps,
            )
            deviceList.add(runtimeDev)

            val scopeId = "device:$deviceId"
            if (isConnected) {
                val instances = caps.map { cap ->
                    val capId = CapabilityId(cap.kind)
                    CapabilityInstance(
                        id = capId,
                        displayName = "${cap.kind} ($displayName)",
                        description = "Device capability on $displayName",
                        category = categoryForKind(cap.kind),
                        sourceKind = CapabilitySourceKind.DEVICE,
                        ownerId = deviceId,
                        endpointId = deviceId,
                        availability = CapabilityAvailability.AVAILABLE,
                    )
                }
                catalogue.replaceScope(scopeId, instances)
            } else {
                catalogue.removeScope(scopeId)
            }
        }

        _devices.value = deviceList
    }

    private fun categoryForKind(kind: String): ToolCategory = when {
        kind.contains("audio") || kind.contains("camera") || kind.contains("sensor") -> ToolCategory.PERCEPTION
        kind.contains("mobility") || kind.contains("manipulator") -> ToolCategory.DEVICE_CONTROL
        else -> ToolCategory.MEDIA
    }
}
