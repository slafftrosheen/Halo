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

val DeviceCapability.kind: String
    get() = when (this) {
        is DeviceCapability.AudioInput -> "audio.input"
        is DeviceCapability.AudioOutput -> "audio.output"
        is DeviceCapability.Camera -> "camera.sensor"
        is DeviceCapability.Display -> "display.output"
        is DeviceCapability.ButtonInput -> "input.button"
        is DeviceCapability.TouchInput -> "input.touch"
        is DeviceCapability.MediaCapture -> "media.capture"
        is DeviceCapability.MediaTransfer -> "media.transfer"
        is DeviceCapability.Mobility -> "mobility.drive"
        is DeviceCapability.Manipulator -> "manipulator.motion"
        is DeviceCapability.Sensor -> "sensor.${this.kind}"
    }

enum class CapabilityAffinity {
    ANY,
    ORIGIN,
    HOST,
    SPECIFIC_OWNER,
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
    val originDeviceId: String? = null,
    val preferredDeviceId: String? = null,
    val affinity: CapabilityAffinity = CapabilityAffinity.ANY,
    val allowFallback: Boolean = true,
)

data class CapabilityResolution(
    val requirement: CapabilityRequirement,
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
            return CapabilityResolution(requirement, null, null, false)
        }

        val targetKind = requirement.capabilityKind.lowercase().replace("_", ".")

        // 1. SPECIFIC_OWNER affinity
        if (requirement.affinity == CapabilityAffinity.SPECIFIC_OWNER) {
            val targetId = requirement.preferredDeviceId ?: requirement.originDeviceId
            val specificDevice = connectedDevices.firstOrNull { it.id == targetId }
            val cap = specificDevice?.capabilities?.firstOrNull { matchesKind(it, targetKind) }
            if (cap != null) {
                return CapabilityResolution(requirement, specificDevice, cap, matchedPreferred = true)
            }
            if (!requirement.allowFallback) {
                return CapabilityResolution(requirement, null, null, false)
            }
        }

        // 2. ORIGIN affinity
        if (requirement.affinity == CapabilityAffinity.ORIGIN && requirement.originDeviceId != null) {
            val originDevice = connectedDevices.firstOrNull { it.id == requirement.originDeviceId }
            val cap = originDevice?.capabilities?.firstOrNull { matchesKind(it, targetKind) }
            if (cap != null) {
                return CapabilityResolution(requirement, originDevice, cap, matchedPreferred = true)
            }
            if (!requirement.allowFallback) {
                return CapabilityResolution(requirement, null, null, false)
            }
        }

        // 3. Preferred device check
        if (requirement.preferredDeviceId != null) {
            val preferred = connectedDevices.firstOrNull { it.id == requirement.preferredDeviceId }
            val cap = preferred?.capabilities?.firstOrNull { matchesKind(it, targetKind) }
            if (cap != null) {
                return CapabilityResolution(requirement, preferred, cap, matchedPreferred = true)
            }
        }

        // 4. Stable fallback ordering
        val candidates = connectedDevices
            .mapNotNull { device ->
                val cap = device.capabilities.firstOrNull { matchesKind(it, targetKind) }
                if (cap != null) Pair(device, cap) else null
            }
            .sortedWith(
                compareBy(
                    { (device, _) -> if (device.id == requirement.originDeviceId) 0 else 1 },
                    { (device, _) -> if (device.id == requirement.preferredDeviceId) 0 else 1 },
                    { (device, _) -> if (device.kind.equals("host", ignoreCase = true)) 0 else 1 },
                    { (device, _) -> device.displayName },
                    { (device, _) -> device.id }
                )
            )

        val selected = candidates.firstOrNull()
        if (selected != null) {
            return CapabilityResolution(
                requirement = requirement,
                selectedDevice = selected.first,
                capability = selected.second,
                matchedPreferred = selected.first.id == requirement.preferredDeviceId || selected.first.id == requirement.originDeviceId,
            )
        }

        return CapabilityResolution(requirement, null, null, false)
    }

    private fun matchesKind(capability: DeviceCapability, targetKind: String): Boolean {
        val capKind = capability.kind.lowercase()
        return capKind == targetKind ||
            capKind.contains(targetKind) ||
            targetKind.contains(capKind) ||
            capKind.replace(".", "") == targetKind.replace(".", "")
    }
}
