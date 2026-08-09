package com.halo.drivers.heycyan

/**
 * Aggregate snapshot of HeyCyan device state.
 *
 * This is the authoritative in-memory representation of everything we know
 * about the connected device. Updated by HeyCyanDeviceControllerImpl as
 * events arrive or queries complete.
 */
data class HeyCyanDeviceState(
    val connection: HeyCyanConnectionState = HeyCyanConnectionState.UNBOUND,
    val battery: BatteryState? = null,
    val version: DeviceVersion? = null,
    val macAddress: String? = null,
    val mediaCounts: MediaCounts? = null,
    val currentMode: HeyCyanMode? = null,
    val lastUpdated: Long = 0L
) {
    companion object {
        val INITIAL = HeyCyanDeviceState()
    }

    /**
     * Whether the device is in a connected and ready state.
     */
    val isReady: Boolean get() = connection == HeyCyanConnectionState.READY

    /**
     * Whether any device information has been populated.
     */
    val hasDeviceInfo: Boolean get() = version != null || macAddress != null
}
