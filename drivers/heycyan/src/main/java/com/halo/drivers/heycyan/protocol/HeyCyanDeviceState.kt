package com.halo.drivers.heycyan.protocol

data class HeyCyanDeviceState(
    val connection: HeyCyanConnectionState =
        HeyCyanConnectionState.DISCONNECTED,

    val battery: BatteryState? = null,

    val version: VersionInfo? = null,

    val mediaCounts: MediaCounts? = null,

    val mode: HeyCyanMode? = null,

    val rawMode: Int? = null,

    val wifiCredentials: WiFiCredentials? = null,

    val lastEventAtMillis: Long? = null,
)

data class BatteryState(
    val level: Int,
    val charging: Boolean,
)

data class VersionInfo(
    val hardware: String,
    val firmware: String,
    val wifiHardware: String,
    val wifiFirmware: String,
)

data class MediaCounts(
    val photos: Int,
    val videos: Int,
    val audio: Int,
    val mediaType: Int,
)

data class WiFiCredentials(
    val ssid: String,
    val password: String,
)

/**
 * Type alias for [MediaCounts] using the PR-025 semantic naming.
 *
 * Response format (event 0x02):
 *   bytes 1-2 → photo count (UInt16 LE)
 *   bytes 3-4 → video count (UInt16 LE)
 *   bytes 5-6 → audio count (UInt16 LE)
 *   byte 7    → mediaType  (raw; meaning not yet established)
 *
 * Do not drop mediaType — store as opaque metadata.
 */
typealias MediaInventory = MediaCounts
