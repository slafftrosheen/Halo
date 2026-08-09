package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.BatteryState
import com.halo.drivers.heycyan.CapabilityDescriptor
import com.halo.drivers.heycyan.CapabilityStatus
import com.halo.drivers.heycyan.HeyCyanDeviceController
import com.halo.drivers.heycyan.operations.HeyCyanAudioRecordingController
import com.halo.drivers.heycyan.operations.HeyCyanCameraController
import com.halo.drivers.heycyan.operations.HeyCyanModeController
import com.halo.drivers.heycyan.operations.HeyCyanVideoController
import com.halo.sdk.driver.AudioRecordingCapability
import com.halo.sdk.driver.BatteryCapability
import com.halo.sdk.driver.BatteryInfo
import com.halo.sdk.driver.CameraCapability
import com.halo.sdk.driver.CaptureResult
import com.halo.sdk.driver.DeviceCapabilities
import com.halo.sdk.driver.DeviceInfo
import com.halo.sdk.driver.DeviceInfoCapability
import com.halo.sdk.driver.MediaCountsInfo
import com.halo.sdk.driver.MediaLibraryCapability
import com.halo.sdk.driver.SyncResult
import com.halo.sdk.driver.TransferSessionInfo
import com.halo.sdk.driver.VideoCapability
import com.halo.sdk.driver.WirelessTransferCapability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * HeyCyan-specific capability exposure.
 *
 * Maps HeyCyan device controller + sub-controllers to generic Halo capability
 * interfaces. Each capability is individually accessible, and the device
 * capabilities object declares what is supported.
 */
class HeyCyanCapabilities(
    private val deviceController: HeyCyanDeviceController,
    private val cameraController: HeyCyanCameraController,
    private val videoController: HeyCyanVideoController,
    private val audioRecordingController: HeyCyanAudioRecordingController,
    private val modeController: HeyCyanModeController
) {

    val deviceCapabilities = DeviceCapabilities.HEYCYAN_GLASSES

    // ── Battery ──────────────────────────────────────────────────────

    val battery: BatteryCapability = object : BatteryCapability {
        private val _batteryState = MutableStateFlow<BatteryInfo?>(null)
        override val batteryState: StateFlow<BatteryInfo?> = _batteryState.asStateFlow()

        override suspend fun getBattery(): Result<BatteryInfo> {
            return deviceController.getBattery().map { bs ->
                BatteryInfo(level = bs.level, isCharging = bs.isCharging).also {
                    _batteryState.value = it
                }
            }
        }
    }

    // ── Device Info ──────────────────────────────────────────────────

    val deviceInfo: DeviceInfoCapability = object : DeviceInfoCapability {
        override suspend fun getDeviceInfo(): Result<DeviceInfo> {
            return deviceController.getVersion().map { version ->
                DeviceInfo(
                    name = "HeyCyan Smart Glasses",
                    hardwareVersion = version.hardwareVersion,
                    firmwareVersion = version.firmwareVersion,
                    macAddress = null,
                    metadata = mapOf(
                        "wifiHardwareVersion" to version.wifiHardwareVersion,
                        "wifiFirmwareVersion" to version.wifiFirmwareVersion
                    )
                )
            }
        }
    }

    // ── Camera ───────────────────────────────────────────────────────

    val camera: CameraCapability = object : CameraCapability {
        override suspend fun takePhoto(): Result<CaptureResult> {
            return cameraController.takePhoto().map {
                CaptureResult(success = true, message = "Photo captured")
            }
        }
    }

    // ── Video ────────────────────────────────────────────────────────

    val video: VideoCapability = object : VideoCapability {
        override val isRecording: StateFlow<Boolean> = videoController.isRecording

        override suspend fun startRecording(): Result<Unit> =
            videoController.startRecording()

        override suspend fun stopRecording(): Result<Unit> =
            videoController.stopRecording()
    }

    // ── Audio Recording ──────────────────────────────────────────────

    val audioRecording: AudioRecordingCapability = object : AudioRecordingCapability {
        override val isRecording: StateFlow<Boolean> = audioRecordingController.isRecording

        override suspend fun startRecording(): Result<Unit> =
            audioRecordingController.startRecording()

        override suspend fun stopRecording(): Result<Unit> =
            audioRecordingController.stopRecording()
    }

    // ── Media Library ────────────────────────────────────────────────

    val mediaLibrary: MediaLibraryCapability = object : MediaLibraryCapability {
        override suspend fun getMediaCounts(): Result<MediaCountsInfo> {
            return deviceController.getMediaCounts().map { counts ->
                MediaCountsInfo(
                    photos = counts.photos,
                    videos = counts.videos,
                    audios = counts.audios
                )
            }
        }

        override suspend fun syncMedia(): Result<SyncResult> {
            // Sync requires WiFi transfer session — see HeyCyanTransferController
            return Result.failure(
                UnsupportedOperationException("Use HeyCyanTransferController for media sync")
            )
        }
    }

    // ── Wireless Transfer ────────────────────────────────────────────

    val wirelessTransfer: WirelessTransferCapability = object : WirelessTransferCapability {
        override suspend fun enableTransfer(): Result<TransferSessionInfo> {
            return deviceController.enableTransferMode().map { creds ->
                TransferSessionInfo(
                    ssid = creds.ssid,
                    deviceIp = null, // IP discovered after WiFi connect
                    filesAvailable = 0
                )
            }
        }

        override suspend fun disableTransfer(): Result<Unit> {
            // Transfer mode exit is implicit — disconnect WiFi or send another mode
            return Result.success(Unit)
        }
    }

    // ── AI Photo (BLOCKED) ───────────────────────────────────────────

    val aiPhoto = HeyCyanAIPhotoCapability()

    // ── Capability Matrix ────────────────────────────────────────────

    /**
     * Returns the complete capability matrix for this device.
     */
    fun getCapabilityMatrix(): List<CapabilityDescriptor> = listOf(
        CapabilityDescriptor("Photo Capture", CapabilityStatus.IMPLEMENTED, "SDK mode 0x01"),
        CapabilityDescriptor("Video Recording", CapabilityStatus.IMPLEMENTED, "SDK modes 0x02/0x03"),
        CapabilityDescriptor("Audio Recording", CapabilityStatus.IMPLEMENTED, "SDK modes 0x04/0x05", "Device-local only, not live mic"),
        CapabilityDescriptor("AI Photo", CapabilityStatus.BLOCKED_BY_UNKNOWN_PROTOCOL, "SDK mode 0x06", "Trigger exists but chunk reassembly unknown"),
        CapabilityDescriptor("Live BLE Microphone", CapabilityStatus.UNSUPPORTED_BY_DEVICE, "Forensic analysis", "No live PCM over BLE"),
        CapabilityDescriptor("Battery Query", CapabilityStatus.IMPLEMENTED, "SDK command 0x13"),
        CapabilityDescriptor("Version Query", CapabilityStatus.IMPLEMENTED, "SDK command 0x10"),
        CapabilityDescriptor("MAC Address Query", CapabilityStatus.IMPLEMENTED_WITH_LIMITATION, "SDK command 0x11", "Response format TBD"),
        CapabilityDescriptor("DateTime Sync", CapabilityStatus.IMPLEMENTED, "SDK command 0x12"),
        CapabilityDescriptor("Media Count Query", CapabilityStatus.IMPLEMENTED, "SDK command 0x14"),
        CapabilityDescriptor("WiFi Transfer Mode", CapabilityStatus.IMPLEMENTED, "SDK openWifiWithMode"),
        CapabilityDescriptor("WiFi Media Download", CapabilityStatus.IMPLEMENTED, "CyanBridge AlbumDownloader"),
        CapabilityDescriptor("WiFi IP Discovery", CapabilityStatus.IMPLEMENTED, "CyanBridge GlassesWiFiHandler"),
        CapabilityDescriptor("WiFi Hotspot Connect", CapabilityStatus.IMPLEMENTED_WITH_LIMITATION, "WifiNetworkSpecifier", "API 29+ only, legacy fallback for 26-28"),
        CapabilityDescriptor("Button Events", CapabilityStatus.IMPLEMENTED, "SDK QCKeyClickType"),
        CapabilityDescriptor("Charging State", CapabilityStatus.IMPLEMENTED, "Battery event includes charging flag"),
        CapabilityDescriptor("Speaker/Audio Output", CapabilityStatus.UNSUPPORTED_BY_DEVICE, "Device inspection", "No speaker on glasses"),
        CapabilityDescriptor("Display", CapabilityStatus.UNSUPPORTED_BY_DEVICE, "Device inspection", "No display on glasses"),
        CapabilityDescriptor("Firmware Update", CapabilityStatus.NOT_YET_INVESTIGATED, "SDK has OTA references", "Not yet analyzed"),
        CapabilityDescriptor("Factory Reset", CapabilityStatus.NOT_YET_INVESTIGATED, "SDK may have reset command", "Not yet analyzed")
    )
}
