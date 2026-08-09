package com.halo.drivers.heycyan.operations

import com.halo.drivers.heycyan.HeyCyanCommandDispatcher
import com.halo.drivers.heycyan.HeyCyanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls video recording on HeyCyan glasses.
 *
 * Evidence: SDK modes 0x02 (start) and 0x03 (stop),
 * CyanBridge `MemoMindPacketEncoder`
 *
 * Constraint: Cannot record video and audio simultaneously (SDK README line 176).
 */
class HeyCyanVideoController(
    private val dispatcher: HeyCyanCommandDispatcher,
    private val modeController: HeyCyanModeController
) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    /**
     * Starts video recording.
     *
     * Evidence: SDK `QCOperatorDeviceModeVideo` (0x02)
     * Confidence: HIGH
     */
    suspend fun startRecording(): Result<Unit> = runCatching {
        modeController.enforceCanCapture()
        dispatcher.sendModeCommand(HeyCyanMode.VIDEO).getOrThrow()
        _isRecording.value = true
        modeController.setActiveMode(HeyCyanMode.VIDEO)
    }

    /**
     * Stops video recording.
     *
     * Evidence: SDK `QCOperatorDeviceModeVideoStop` (0x03)
     * Confidence: HIGH
     */
    suspend fun stopRecording(): Result<Unit> = runCatching {
        check(_isRecording.value) { "No video recording in progress" }
        dispatcher.sendModeCommand(HeyCyanMode.VIDEO_STOP).getOrThrow()
        _isRecording.value = false
        modeController.clearActiveMode()
    }

    /**
     * Called when mode changes are detected from unsolicited events.
     */
    fun onModeChanged(mode: HeyCyanMode?) {
        _isRecording.value = mode == HeyCyanMode.VIDEO
    }
}
