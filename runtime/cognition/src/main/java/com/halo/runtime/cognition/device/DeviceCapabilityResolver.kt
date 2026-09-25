package com.halo.runtime.cognition.device

/**
 * Portable device capabilities. These are semantic abilities, not scarce
 * resource identities. Resource mapping happens later through
 * CapabilityResourceBinding / ResourcePlanner.
 */
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
        is DeviceCapability.AudioInput -> "device.audio.input"
        is DeviceCapability.AudioOutput -> "device.audio.output"
        is DeviceCapability.Camera -> "device.camera.still"
        is DeviceCapability.Display -> "device.display.output"
        is DeviceCapability.ButtonInput -> "device.input.button"
        is DeviceCapability.TouchInput -> "device.input.touch"
        is DeviceCapability.MediaCapture -> "device.media.capture"
        is DeviceCapability.MediaTransfer -> "device.media.transfer"
        is DeviceCapability.Mobility -> "device.mobility"
        is DeviceCapability.Manipulator -> "device.manipulator"
        is DeviceCapability.Sensor -> "device.sensor.${this.kind.trim().lowercase().replace(' ', '.')}"
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

        val targetKind = canonicalCapabilityId(requirement.capabilityKind)

        fun capabilityOn(device: RuntimeDevice): DeviceCapability? =
            device.capabilities.firstOrNull { canonicalCapabilityId(it.kind) == targetKind }

        if (requirement.affinity == CapabilityAffinity.SPECIFIC_OWNER) {
            val targetId = requirement.preferredDeviceId ?: requirement.originDeviceId
            val specific = connectedDevices.firstOrNull { it.id == targetId }
            val capability = specific?.let(::capabilityOn)
            if (capability != null) {
                return CapabilityResolution(requirement, specific, capability, matchedPreferred = true)
            }
            if (!requirement.allowFallback) return CapabilityResolution(requirement, null, null)
        }

        if (requirement.affinity == CapabilityAffinity.ORIGIN && requirement.originDeviceId != null) {
            val origin = connectedDevices.firstOrNull { it.id == requirement.originDeviceId }
            val capability = origin?.let(::capabilityOn)
            if (capability != null) {
                return CapabilityResolution(requirement, origin, capability, matchedPreferred = true)
            }
            if (!requirement.allowFallback) return CapabilityResolution(requirement, null, null)
        }

        if (requirement.affinity == CapabilityAffinity.HOST) {
            val host = connectedDevices
                .filter(::isHost)
                .sortedBy { it.id }
                .firstOrNull { capabilityOn(it) != null }
            if (host != null) {
                return CapabilityResolution(requirement, host, capabilityOn(host), matchedPreferred = true)
            }
            if (!requirement.allowFallback) return CapabilityResolution(requirement, null, null)
        }

        requirement.preferredDeviceId?.let { preferredId ->
            val preferred = connectedDevices.firstOrNull { it.id == preferredId }
            val capability = preferred?.let(::capabilityOn)
            if (capability != null) {
                return CapabilityResolution(requirement, preferred, capability, matchedPreferred = true)
            }
        }

        val selected = connectedDevices
            .mapNotNull { device -> capabilityOn(device)?.let { capability -> device to capability } }
            .sortedWith(
                compareBy<Pair<RuntimeDevice, DeviceCapability>>(
                    { (device, _) -> if (device.id == requirement.originDeviceId) 0 else 1 },
                    { (device, _) -> if (device.id == requirement.preferredDeviceId) 0 else 1 },
                    { (device, _) -> if (isHost(device)) 0 else 1 },
                    { (device, _) -> device.displayName.lowercase() },
                    { (device, _) -> device.id },
                )
            )
            .firstOrNull()

        return if (selected != null) {
            CapabilityResolution(
                requirement = requirement,
                selectedDevice = selected.first,
                capability = selected.second,
                matchedPreferred = selected.first.id == requirement.preferredDeviceId ||
                    selected.first.id == requirement.originDeviceId,
            )
        } else {
            CapabilityResolution(requirement, null, null)
        }
    }

    private fun isHost(device: RuntimeDevice): Boolean =
        device.id == "host:primary" || device.kind.equals("host", ignoreCase = true)

    /**
     * Temporary compatibility aliases for pre-RUNTIME-020.1 resource-shaped
     * capability ids. Matching after normalization is exact.
     */
    private fun canonicalCapabilityId(raw: String): String = when (
        raw.trim().lowercase().replace('_', '.')
    ) {
        "audio.input" -> "device.audio.input"
        "audio.output" -> "device.audio.output"
        "camera.sensor", "camera.still", "camera.photo" -> "device.camera.still"
        "display.output" -> "device.display.output"
        "media.capture" -> "device.media.capture"
        "media.transfer" -> "device.media.transfer"
        "mobility.drive" -> "device.mobility"
        "manipulator.motion" -> "device.manipulator"
        else -> raw.trim().lowercase().replace('_', '.')
    }
}
