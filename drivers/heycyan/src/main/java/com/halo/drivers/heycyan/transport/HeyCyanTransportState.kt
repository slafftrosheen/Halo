package com.halo.drivers.heycyan.transport

/**
 * Transport types available in the HeyCyan driver architecture.
 */
enum class HeyCyanTransportType {
    BLE_GATT,
    WIFI,
}

/**
 * Normalized transport state across BLE GATT and Wi-Fi.
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
 * Complete transport status snapshot across device transports.
 */
data class HeyCyanTransportSnapshot(
    val ble: HeyCyanTransportStatus = HeyCyanTransportStatus(HeyCyanTransportType.BLE_GATT, HeyCyanTransportState.DISCONNECTED),
    val wifi: HeyCyanTransportStatus = HeyCyanTransportStatus(HeyCyanTransportType.WIFI, HeyCyanTransportState.DISCONNECTED),
)
