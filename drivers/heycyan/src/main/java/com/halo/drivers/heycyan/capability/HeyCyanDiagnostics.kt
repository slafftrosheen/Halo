package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.CapabilityDescriptor
import com.halo.drivers.heycyan.HeyCyanBleEvent
import com.halo.drivers.heycyan.HeyCyanConnectionState
import com.halo.drivers.heycyan.HeyCyanDeviceController
import com.halo.drivers.heycyan.HeyCyanDeviceState
import com.halo.drivers.heycyan.HeyCyanMode
import com.halo.drivers.heycyan.operations.HeyCyanCameraController
import com.halo.drivers.heycyan.operations.HeyCyanVideoController
import com.halo.drivers.heycyan.operations.HeyCyanAudioRecordingController
import com.halo.drivers.heycyan.operations.HeyCyanModeController
import kotlinx.coroutines.flow.StateFlow

/**
 * Diagnostics surface for HeyCyan developer tools.
 *
 * Exposes internal device state, connection details, and protocol diagnostics
 * that are useful during development and debugging but not part of the
 * production cognitive loop.
 *
 * Everything here is read-only and does not affect device operation.
 */
class HeyCyanDiagnostics(
    private val deviceController: HeyCyanDeviceController,
    private val cameraController: HeyCyanCameraController,
    private val videoController: HeyCyanVideoController,
    private val audioRecordingController: HeyCyanAudioRecordingController,
    private val modeController: HeyCyanModeController,
    private val capabilities: HeyCyanCapabilities
) {

    // ── Connection ───────────────────────────────────────────────────

    val connectionState: StateFlow<HeyCyanConnectionState>
        get() = deviceController.connectionState

    // ── Device State ─────────────────────────────────────────────────

    val deviceState: StateFlow<HeyCyanDeviceState>
        get() = deviceController.deviceState

    // ── Mode ─────────────────────────────────────────────────────────

    val currentMode: StateFlow<HeyCyanMode?>
        get() = deviceController.currentMode

    val activeMode: StateFlow<HeyCyanMode?>
        get() = modeController.activeMode

    val isRecording: Boolean
        get() = modeController.isRecording

    // ── Camera ───────────────────────────────────────────────────────

    val cameraState: StateFlow<HeyCyanCameraController.CameraState>
        get() = cameraController.cameraState

    // ── Video ────────────────────────────────────────────────────────

    val isVideoRecording: StateFlow<Boolean>
        get() = videoController.isRecording

    // ── Audio ────────────────────────────────────────────────────────

    val isAudioRecording: StateFlow<Boolean>
        get() = audioRecordingController.isRecording

    // ── Capabilities ─────────────────────────────────────────────────

    val capabilityMatrix: List<CapabilityDescriptor>
        get() = capabilities.getCapabilityMatrix()

    // ── Unknown Events ───────────────────────────────────────────────

    /**
     * Returns the unknown event stream for protocol analysis.
     * These events were received but not recognized by the decoder.
     */
    val unknownEvents
        get() = deviceController.unknownEvents

    // ── Snapshot ──────────────────────────────────────────────────────

    /**
     * Returns a complete diagnostics snapshot as a map for logging/UI.
     */
    fun snapshot(): Map<String, Any?> = buildMap {
        val state = deviceController.deviceState.value
        put("connection", state.connection.name)
        put("batteryLevel", state.battery?.level)
        put("isCharging", state.battery?.isCharging)
        put("hardwareVersion", state.version?.hardwareVersion)
        put("firmwareVersion", state.version?.firmwareVersion)
        put("wifiHwVersion", state.version?.wifiHardwareVersion)
        put("wifiFwVersion", state.version?.wifiFirmwareVersion)
        put("macAddress", state.macAddress)
        put("photos", state.mediaCounts?.photos)
        put("videos", state.mediaCounts?.videos)
        put("audios", state.mediaCounts?.audios)
        put("currentMode", state.currentMode?.name)
        put("isRecording", modeController.isRecording)
        put("isVideoRecording", videoController.isRecording.value)
        put("isAudioRecording", audioRecordingController.isRecording.value)
        put("cameraState", cameraController.cameraState.value.name)
        put("lastUpdated", state.lastUpdated)
    }
}
