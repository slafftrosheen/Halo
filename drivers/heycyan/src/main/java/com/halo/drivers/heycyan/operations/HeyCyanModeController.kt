package com.halo.drivers.heycyan.operations

import com.halo.drivers.heycyan.HeyCyanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enforces mode mutual exclusion and valid transitions for HeyCyan glasses.
 *
 * SDK constraint: Cannot record video and audio simultaneously (SDK README line 176).
 *
 * Mode state machine:
 * ```
 * IDLE → PHOTO → IDLE
 * IDLE → VIDEO → VIDEO_STOP → IDLE
 * IDLE → AUDIO → AUDIO_STOP → IDLE
 * IDLE → TRANSFER → IDLE
 * IDLE → AI_PHOTO → IDLE  (BLOCKED)
 * ```
 *
 * VIDEO and AUDIO are mutually exclusive active recording modes.
 */
class HeyCyanModeController {

    private val _activeMode = MutableStateFlow<HeyCyanMode?>(null)
    val activeMode: StateFlow<HeyCyanMode?> = _activeMode.asStateFlow()

    /**
     * Whether any recording is currently in progress.
     */
    val isRecording: Boolean
        get() {
            val mode = _activeMode.value
            return mode == HeyCyanMode.VIDEO || mode == HeyCyanMode.AUDIO
        }

    /**
     * Checks whether a new capture can be started.
     *
     * @throws IllegalStateException if a recording is in progress
     */
    fun enforceCanCapture() {
        val mode = _activeMode.value
        check(mode != HeyCyanMode.VIDEO) {
            "Cannot start new capture while video is recording (mode=$mode). Stop video first."
        }
        check(mode != HeyCyanMode.AUDIO) {
            "Cannot start new capture while audio is recording (mode=$mode). Stop audio first."
        }
        check(mode != HeyCyanMode.TRANSFER) {
            "Cannot start capture while in transfer mode (mode=$mode). Exit transfer first."
        }
    }

    /**
     * Sets the active mode after a successful mode command.
     */
    fun setActiveMode(mode: HeyCyanMode) {
        _activeMode.value = mode
    }

    /**
     * Clears the active mode (recording stopped, transfer exited).
     */
    fun clearActiveMode() {
        _activeMode.value = null
    }

    /**
     * Called when a mode change event is received from the device.
     * Updates the local mode tracking.
     */
    fun onDeviceModeChanged(mode: HeyCyanMode) {
        when (mode) {
            HeyCyanMode.VIDEO_STOP, HeyCyanMode.AUDIO_STOP -> {
                _activeMode.value = null
            }
            else -> {
                _activeMode.value = mode
            }
        }
    }

    /**
     * Checks whether a specific mode transition is valid.
     */
    fun isValidTransition(from: HeyCyanMode?, to: HeyCyanMode): Boolean {
        return when {
            // Can always transition to any mode from idle
            from == null -> true
            // Can stop the current recording
            from == HeyCyanMode.VIDEO && to == HeyCyanMode.VIDEO_STOP -> true
            from == HeyCyanMode.AUDIO && to == HeyCyanMode.AUDIO_STOP -> true
            // PHOTO is instantaneous, always returns to idle
            from == HeyCyanMode.PHOTO -> true
            // Cannot start new recording while one is active
            from == HeyCyanMode.VIDEO || from == HeyCyanMode.AUDIO -> false
            // Default: allow
            else -> true
        }
    }
}
