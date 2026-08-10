package com.halo.drivers.heycyan.transport

/**
 * Normalized transport state across BLE GATT, RFCOMM, and Wi-Fi.
 */
enum class HeyCyanTransportState {
    UNAVAILABLE,
    DISCONNECTED,
    CONNECTING,
    READY,
    FAILED,
}

/**
 * Status snapshot of a specific transport type.
 */
data class HeyCyanTransportStatus(
    val type: HeyCyanTransportType,
    val state: HeyCyanTransportState,
    val reason: String? = null,
)

/**
 * Complete transport status snapshot across all 3 device transports.
 */
data class HeyCyanTransportSnapshot(
    val ble: HeyCyanTransportStatus = HeyCyanTransportStatus(HeyCyanTransportType.BLE_GATT, HeyCyanTransportState.DISCONNECTED),
    val rfcomm: HeyCyanTransportStatus = HeyCyanTransportStatus(HeyCyanTransportType.RFCOMM, HeyCyanTransportState.DISCONNECTED),
    val wifi: HeyCyanTransportStatus = HeyCyanTransportStatus(HeyCyanTransportType.WIFI, HeyCyanTransportState.DISCONNECTED),
)
