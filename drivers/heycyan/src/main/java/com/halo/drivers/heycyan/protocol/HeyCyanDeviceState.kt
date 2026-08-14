package com.halo.drivers.heycyan.protocol

enum class VideoRecordingState {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING,
    ERROR,
    UNKNOWN
}

enum class AudioRecordingState {
    IDLE,
    RECORDING,
    ERROR
}

enum class DeviceHealthState {
    HEALTHY,
    WARNING_MEMORY_INSUFFICIENT,
    UNBOUND,
    ERROR
}

enum class WearState {
    WORN,
    NOT_WORN,
    UNKNOWN
}

data class VolumeState(
    val musicCurrent: Int = 0,
    val callCurrent: Int = 0,
    val systemCurrent: Int = 0
)

data class HardwareInputState(
    val lastButton: String? = null,
    val lastTouchGesture: String? = null,
    val lastInputTimestampMs: Long? = null
)

data class HeyCyanDeviceState(
    val connection: HeyCyanConnectionState =
        HeyCyanConnectionState.DISCONNECTED,

    val battery: BatteryState? = null,

    val version: VersionInfo? = null,

    val mediaCounts: MediaCounts? = null,

    val mode: HeyCyanMode? = null,

    val rawMode: Int? = null,

    val wifiCredentials: WiFiCredentials? = null,

    val transports: com.halo.drivers.heycyan.transport.HeyCyanTransportSnapshot =
        com.halo.drivers.heycyan.transport.HeyCyanTransportSnapshot(),

    val operation: com.halo.drivers.heycyan.capability.HeyCyanOperationState =
        com.halo.drivers.heycyan.capability.HeyCyanOperationState.Idle,

    val videoRecordingState: VideoRecordingState = VideoRecordingState.IDLE,

    val audioRecordingState: AudioRecordingState = AudioRecordingState.IDLE,

    val healthState: DeviceHealthState = DeviceHealthState.HEALTHY,

    val wearState: WearState = WearState.UNKNOWN,

    val volumeState: VolumeState? = null,

    val hardwareInputState: HardwareInputState = HardwareInputState(),

    val lastEventAtMillis: Long? = null,
)

data class BatteryState(
    val level: Int,
    val charging: Boolean,
    val timestampMs: Long = System.currentTimeMillis()
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
 */
typealias MediaInventory = MediaCounts
