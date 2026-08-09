package com.halo.sdk.driver

import kotlinx.coroutines.flow.StateFlow

/**
 * Generic Halo device capability interfaces.
 *
 * These live in the SDK (not in any specific driver) so any wearable backend
 * can implement the capabilities it supports. The capability model allows the
 * runtime to query what a device can do without hardcoding device-specific knowledge.
 *
 * Each capability interface is independently implementable. A device does not
 * need to implement all of them — only the ones it supports.
 */

// ── Device Information ───────────────────────────────────────────────

interface BatteryCapability {
    suspend fun getBattery(): Result<BatteryInfo>
    val batteryState: StateFlow<BatteryInfo?>
}

data class BatteryInfo(
    val level: Int,        // 0-100
    val isCharging: Boolean
)

interface DeviceInfoCapability {
    suspend fun getDeviceInfo(): Result<DeviceInfo>
}

data class DeviceInfo(
    val name: String,
    val hardwareVersion: String,
    val firmwareVersion: String,
    val macAddress: String?,
    val metadata: Map<String, String> = emptyMap()
)

// ── Camera ───────────────────────────────────────────────────────────

interface CameraCapability {
    suspend fun takePhoto(): Result<CaptureResult>
}

data class CaptureResult(
    val success: Boolean,
    val message: String? = null
)

// ── Video ────────────────────────────────────────────────────────────

interface VideoCapability {
    suspend fun startRecording(): Result<Unit>
    suspend fun stopRecording(): Result<Unit>
    val isRecording: StateFlow<Boolean>
}

// ── Audio Recording (on-device, NOT live microphone) ─────────────────

interface AudioRecordingCapability {
    suspend fun startRecording(): Result<Unit>
    suspend fun stopRecording(): Result<Unit>
    val isRecording: StateFlow<Boolean>
}

// ── Media Library ────────────────────────────────────────────────────

interface MediaLibraryCapability {
    suspend fun getMediaCounts(): Result<MediaCountsInfo>
    suspend fun syncMedia(): Result<SyncResult>
}

data class MediaCountsInfo(
    val photos: Int,
    val videos: Int,
    val audios: Int
)

data class SyncResult(
    val totalFiles: Int,
    val downloaded: Int,
    val failed: Int,
    val bytesDownloaded: Long
)

// ── Wireless Transfer ────────────────────────────────────────────────

interface WirelessTransferCapability {
    suspend fun enableTransfer(): Result<TransferSessionInfo>
    suspend fun disableTransfer(): Result<Unit>
}

data class TransferSessionInfo(
    val ssid: String,
    val deviceIp: String?,
    val filesAvailable: Int
)
