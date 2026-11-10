package com.halo.sdk.runtime.hardware

import kotlinx.coroutines.flow.StateFlow

enum class PeripheralKind { INPUT, USB }
enum class InputRole { STYLUS, MOUSE, TOUCH, KEYBOARD, GAMEPAD, JOYSTICK, ROTARY }
enum class InventoryAvailability { AVAILABLE, PARTIAL, UNAVAILABLE, BACKGROUND }

/** Advertised OS ranges, not measurements or physical qualification. */
data class InputAxisReport(val axis: Int, val source: Int, val min: Float, val max: Float, val flat: Float, val fuzz: Float, val resolution: Float) {
    val valid: Boolean get() = listOf(min, max, flat, fuzz, resolution).all { it.isFinite() } && max > min
}
data class UsbEndpointReport(val address: Int, val direction: Int, val type: Int, val maxPacketSize: Int, val interval: Int)
data class UsbInterfaceReport(val id: Int, val alternateSetting: Int, val deviceClass: Int, val subclass: Int, val protocol: Int,
    val endpoints: List<UsbEndpointReport>, val endpointCount: Int)
data class PeripheralProfile(
    val kind: PeripheralKind,
    val name: String,
    val vendorId: Int? = null,
    val productId: Int? = null,
    val roles: Set<InputRole> = emptySet(),
    val axes: List<InputAxisReport> = emptyList(),
    val axisCount: Int = 0,
    val external: Boolean? = null,
    val virtual: Boolean? = null,
    val usbClass: Int? = null,
    val usbSubclass: Int? = null,
    val usbProtocol: Int? = null,
    val interfaces: List<UsbInterfaceReport> = emptyList(),
    val interfaceCount: Int = 0,
    val usbPermission: Boolean? = null,
    val detailsTruncated: Boolean = false,
)
/** Platform keys stay inside the host/session and are never exposed as device authority. */
data class PeripheralObservation(val platformKey: String, val profile: PeripheralProfile)
data class ObservedPeripheral(val token: String, val profile: PeripheralProfile)
data class PeripheralInventorySnapshot(
    val sourceVersion: Long = 0,
    val inputAvailability: InventoryAvailability = InventoryAvailability.BACKGROUND,
    val usbAvailability: InventoryAvailability = InventoryAvailability.BACKGROUND,
    val inputCount: Int = 0,
    val usbCount: Int = 0,
    val devices: List<ObservedPeripheral> = emptyList(),
    val truncated: Boolean = false,
)
interface PeripheralInventory {
    val state: StateFlow<PeripheralInventorySnapshot>
    suspend fun snapshot(): PeripheralInventorySnapshot
}

/** Host-thread confined, connection-scoped read authority; no connect, claim, permission request or I/O API. */
class PeripheralInventorySession(private val newToken: () -> String) {
    private var connections = emptyMap<String, ObservedPeripheral>()
    var state = PeripheralInventorySnapshot()
        private set

    fun update(observed: List<PeripheralObservation>, inputAvailability: InventoryAvailability,
        usbAvailability: InventoryAvailability, inputCount: Int, usbCount: Int, invalidated: Set<String> = emptySet()) {
        val bounded = observed.filter {
            when (it.profile.kind) {
                PeripheralKind.INPUT -> inputAvailability in setOf(InventoryAvailability.AVAILABLE, InventoryAvailability.PARTIAL)
                PeripheralKind.USB -> usbAvailability in setOf(InventoryAvailability.AVAILABLE, InventoryAvailability.PARTIAL)
            }
        }.distinctBy { it.platformKey }.sortedBy { it.platformKey }.take(MAX_DEVICES)
        val next = bounded.associate { observation ->
            val profile = boundedProfile(observation.profile)
            val old = connections[observation.platformKey]
            observation.platformKey to if (old?.profile == profile && observation.platformKey !in invalidated) old
                else ObservedPeripheral(newToken(), profile)
        }
        val nextState = PeripheralInventorySnapshot(state.sourceVersion, inputAvailability, usbAvailability,
            inputCount.coerceAtLeast(0), usbCount.coerceAtLeast(0), next.values.toList(),
            inputCount.toLong() + usbCount > next.size)
        if (nextState != state) state = nextState.copy(sourceVersion = state.sourceVersion + 1)
        connections = next
    }

    fun stop() = update(emptyList(), InventoryAvailability.BACKGROUND, InventoryAvailability.BACKGROUND, 0, 0)
    fun find(token: String, sourceVersion: Long): ObservedPeripheral? =
        state.devices.firstOrNull { state.sourceVersion == sourceVersion && it.token == token }

    companion object {
        const val MAX_DEVICES = 48
        const val MAX_AXES = 16
        const val MAX_INTERFACES = 8
        const val MAX_ENDPOINTS = 8
        fun boundedProfile(profile: PeripheralProfile): PeripheralProfile {
            val validAxes = profile.axes.filter { it.valid }
            return profile.copy(
                name = profile.name.filterNot { it.isISOControl() }.trim().take(120).ifBlank { "Unnamed peripheral" },
                roles = profile.roles.toSet(),
                axes = validAxes.take(MAX_AXES).toList(),
                axisCount = profile.axisCount.coerceAtLeast(profile.axes.size),
                interfaceCount = profile.interfaceCount.coerceAtLeast(profile.interfaces.size),
                interfaces = profile.interfaces.take(MAX_INTERFACES).map { it.copy(endpoints = it.endpoints.take(MAX_ENDPOINTS).toList(),
                    endpointCount = it.endpointCount.coerceAtLeast(it.endpoints.size)) },
                detailsTruncated = profile.detailsTruncated || profile.axisCount.coerceAtLeast(profile.axes.size) > validAxes.take(MAX_AXES).size ||
                    profile.interfaceCount.coerceAtLeast(profile.interfaces.size) > MAX_INTERFACES ||
                    profile.interfaces.any { it.endpointCount.coerceAtLeast(it.endpoints.size) > MAX_ENDPOINTS },
            )
        }
    }
}
