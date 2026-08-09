package com.halo.drivers.heycyan.operations

import com.halo.drivers.heycyan.CapabilityStatus
import com.halo.drivers.heycyan.HeyCyanCommandDispatcher
import com.halo.drivers.heycyan.HeyCyanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls audio recording on HeyCyan glasses.
 *
 * IMPORTANT: This is device-local recording, NOT live microphone streaming.
 * The device records audio internally and the resulting file must be transferred
 * via WiFi afterward. See PR-016 forensic analysis.
 *
 * Live BLE microphone: UNSUPPORTED_BY_DEVICE
 * Recorded audio: IMPLEMENTED (mode 0x04/0x05 + WiFi transfer)
 *
 * Evidence: SDK modes 0x04 (start) and 0x05 (stop),
 * CyanBridge `MemoMindAudioBridge`, `AlbumDownloader` (`.opus` files)
 *
 * Constraint: Cannot record video and audio simultaneously (SDK README line 176).
 */
class HeyCyanAudioRecordingController(
    private val dispatcher: HeyCyanCommandDispatcher,
    private val modeController: HeyCyanModeController
) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /**
     * Live BLE microphone status — explicitly unsupported.
     *
     * Evidence: Forensic analysis confirms no live PCM over BLE.
     * The cognitive loop should use PhoneMicrophoneAudioSource.
     */
    val liveMicrophoneStatus: CapabilityStatus = CapabilityStatus.UNSUPPORTED_BY_DEVICE

    /**
     * Starts audio recording on the device.
     *
     * The glasses record audio locally. After stopping, the recording
     * becomes available in the device's media inventory and can be
     * transferred via WiFi.
     *
     * Evidence: SDK `QCOperatorDeviceModeAudio` (0x04)
     * Confidence: HIGH
     */
    suspend fun startRecording(): Result<Unit> = runCatching {
        modeController.enforceCanCapture()
        dispatcher.sendModeCommand(HeyCyanMode.AUDIO).getOrThrow()
        _isRecording.value = true
        modeController.setActiveMode(HeyCyanMode.AUDIO)
    }

    /**
     * Stops audio recording.
     *
     * After stopping, the recorded audio file appears in the device's
     * media inventory (typically as .opus format).
     *
     * Evidence: SDK `QCOperatorDeviceModeAudioStop` (0x05)
     * Confidence: HIGH
     */
    suspend fun stopRecording(): Result<Unit> = runCatching {
        check(_isRecording.value) { "No audio recording in progress" }
        dispatcher.sendModeCommand(HeyCyanMode.AUDIO_STOP).getOrThrow()
        _isRecording.value = false
        modeController.clearActiveMode()
    }

    /**
     * Called when mode changes are detected from unsolicited events.
     */
    fun onModeChanged(mode: HeyCyanMode?) {
        _isRecording.value = mode == HeyCyanMode.AUDIO
    }
}
