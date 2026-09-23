package com.halo.runtime.cognition.device

sealed interface DeviceCapability {
    data class AudioInput(val sampleRates: List<Int> = listOf(16000)) : DeviceCapability
    data class AudioOutput(val sampleRates: List<Int> = listOf(16000)) : DeviceCapability
    data class Camera(val facing: String = "FRONT", val maxResolution: String = "1080p") : DeviceCapability
    data class Display(val width: Int = 1080, val height: Int = 1920) : DeviceCapability
    data class ButtonInput(val buttons: List<String> = emptyList()) : DeviceCapability
    data class TouchInput(val gestures: List<String> = emptyList()) : DeviceCapability
    data class MediaCapture(val photoSupported: Boolean = true, val videoSupported: Boolean = false) : DeviceCapability
    data class MediaTransfer(val wifiDirectSupported: Boolean = true) : DeviceCapability
    data class Mobility(val driveSupported: Boolean = false) : DeviceCapability
    data class Manipulator(val degreesOfFreedom: Int = 0) : DeviceCapability
    data class Sensor(val kind: String) : DeviceCapability
}

data class RuntimeDevice(
    val id: String,
    val kind: String,
    val displayName: String,
    val connected: Boolean = true,
    val capabilities: Set<DeviceCapability> = emptySet(),
)

data class CapabilityRequirement(
    val capabilityKind: String,
    val preferredDeviceId: String? = null,
)

data class CapabilityResolution(
    val selectedDevice: RuntimeDevice?,
    val capability: DeviceCapability?,
    val matchedPreferred: Boolean = false,
)

interface DeviceCapabilityResolver {
    fun resolve(
        requirement: CapabilityRequirement,
        availableDevices: List<RuntimeDevice>,
    ): CapabilityResolution
}

class DefaultDeviceCapabilityResolver : DeviceCapabilityResolver {
    override fun resolve(
        requirement: CapabilityRequirement,
        availableDevices: List<RuntimeDevice>,
    ): CapabilityResolution {
        val connectedDevices = availableDevices.filter { it.connected }
        if (connectedDevices.isEmpty()) {
            return CapabilityResolution(null, null, false)
        }

        // Try preferred device first
        if (requirement.preferredDeviceId != null) {
            val preferred = connectedDevices.firstOrNull { it.id == requirement.preferredDeviceId }
            if (preferred != null) {
                val cap = preferred.capabilities.firstOrNull { matchesKind(it, requirement.capabilityKind) }
                if (cap != null) {
                    return CapabilityResolution(preferred, cap, matchedPreferred = true)
                }
            }
        }

        // Fall back to first connected device advertising capability
        for (device in connectedDevices) {
            val cap = device.capabilities.firstOrNull { matchesKind(it, requirement.capabilityKind) }
            if (cap != null) {
                return CapabilityResolution(device, cap, matchedPreferred = false)
            }
        }

        return CapabilityResolution(null, null, false)
    }

    private fun matchesKind(capability: DeviceCapability, kind: String): Boolean {
        val simpleName = capability::class.java.simpleName.lowercase()
        val targetKind = kind.lowercase().replace("_", "")
        return simpleName.contains(targetKind) || targetKind.contains(simpleName)
    }
}
