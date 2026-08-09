package com.halo.drivers.heycyan

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Complete BLE control operations for HeyCyan glasses.
 *
 * Covers the full SDK surface: device info, battery, media, camera,
 * video, audio recording, transfer mode, and lifecycle management.
 *
 * Evidence sources: HeyCyanSmartGlassesSDK, CyanBridge, PR-016 forensics.
 */
interface HeyCyanDeviceController {

    // ── Lifecycle ────────────────────────────────────────────────────

    /** Current connection state. */
    val connectionState: StateFlow<HeyCyanConnectionState>

    /** Aggregate device state snapshot, continuously updated. */
    val deviceState: StateFlow<HeyCyanDeviceState>

    /**
     * Initializes the device after GATT readiness.
     * Queries version, MAC, battery, media counts, syncs datetime.
     */
    suspend fun initializeDevice(): Result<HeyCyanDeviceState>

    /**
     * Attempts to reconnect to a previously connected device.
     */
    suspend fun reconnect(address: String): Result<Unit>

    // ── Device Information ───────────────────────────────────────────

    /**
     * Retrieves device version information.
     * Evidence: SDK `getDeviceVersionInfo`, CyanBridge Event 0x03
     */
    suspend fun getVersion(): Result<DeviceVersion>

    /**
     * Retrieves battery level and charging state.
     * Evidence: SDK `getDeviceBattery`, CyanBridge Event 0x01
     */
    suspend fun getBattery(): Result<BatteryState>

    /**
     * Retrieves media file counts (photos, videos, audio).
     * Evidence: SDK `getDeviceMedia`, CyanBridge Event 0x02
     */
    suspend fun getMediaCounts(): Result<MediaCounts>

    /**
     * Retrieves device MAC address.
     * Evidence: SDK `getDeviceMacAddress` (response format TBD)
     */
    suspend fun getMacAddress(): Result<String>

    /**
     * Synchronizes device date/time with host.
     * Evidence: SDK `setupDeviceDateTime`
     */
    suspend fun setDateTime(): Result<Unit>

    // ── Device Operations ────────────────────────────────────────────

    /**
     * Sets device operational mode.
     * Evidence: SDK `setDeviceMode`, CyanBridge mode bytes 0x01–0x07
     */
    suspend fun setMode(mode: HeyCyanMode): Result<HeyCyanMode>

    /**
     * Takes a photo.
     * Evidence: SDK mode 0x01
     */
    suspend fun takePhoto(): Result<Unit>

    /**
     * Starts video recording.
     * Evidence: SDK mode 0x02
     *
     * Note: Cannot record video and audio simultaneously (SDK constraint).
     */
    suspend fun startVideoRecording(): Result<Unit>

    /**
     * Stops video recording.
     * Evidence: SDK mode 0x03
     */
    suspend fun stopVideoRecording(): Result<Unit>

    /**
     * Starts audio recording on the device.
     * Evidence: SDK mode 0x04
     *
     * IMPORTANT: This records audio locally on the glasses. It is NOT
     * a live microphone stream. Recorded audio must be transferred via WiFi.
     *
     * Note: Cannot record video and audio simultaneously (SDK constraint).
     */
    suspend fun startAudioRecording(): Result<Unit>

    /**
     * Stops audio recording.
     * Evidence: SDK mode 0x05
     */
    suspend fun stopAudioRecording(): Result<Unit>

    /**
     * Triggers AI Photo mode.
     * Status: BLOCKED_BY_UNKNOWN_PROTOCOL — trigger works but response
     * protocol (chunk reassembly, image output) is not fully understood.
     */
    suspend fun triggerAIPhoto(): Result<Unit>

    // ── WiFi Transfer ────────────────────────────────────────────────

    /**
     * Enables WiFi transfer mode and retrieves hotspot credentials.
     * Evidence: SDK `openWifiWithMode(Transfer)`, CyanBridge Event 0x05
     */
    suspend fun enableTransferMode(): Result<WiFiCredentials>

    // ── Event Streams ────────────────────────────────────────────────

    /** Stream of raw unknown events for protocol analysis. */
    val unknownEvents: Flow<HeyCyanBleEvent.Unknown>

    /** Continuous battery state updates (from both queries and unsolicited events). */
    val batteryUpdates: StateFlow<BatteryState?>

    /** Current device mode. */
    val currentMode: StateFlow<HeyCyanMode?>
}

data class DeviceVersion(
    val hardwareVersion: String,
    val firmwareVersion: String,
    val wifiHardwareVersion: String,
    val wifiFirmwareVersion: String
)

data class BatteryState(
    val level: Int,        // 0-100
    val isCharging: Boolean
)

data class MediaCounts(
    val photos: Int,
    val videos: Int,
    val audios: Int,
    val mediaType: Int
)

data class WiFiCredentials(
    val ssid: String,
    val password: String
)