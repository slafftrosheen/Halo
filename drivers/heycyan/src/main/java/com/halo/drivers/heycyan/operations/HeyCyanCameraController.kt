package com.halo.drivers.heycyan.operations

import com.halo.drivers.heycyan.CapabilityStatus
import com.halo.drivers.heycyan.HeyCyanCommandDispatcher
import com.halo.drivers.heycyan.HeyCyanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls camera operations on HeyCyan glasses.
 *
 * Capabilities:
 * - Photo capture: IMPLEMENTED (mode 0x01)
 * - AI Photo: BLOCKED_BY_UNKNOWN_PROTOCOL (mode 0x06)
 *
 * Evidence: SDK `QCOperatorDeviceModePhoto`, CyanBridge `MemoMindPacketEncoder`
 */
class HeyCyanCameraController(
    private val dispatcher: HeyCyanCommandDispatcher,
    private val modeController: HeyCyanModeController
) {

    enum class CameraState {
        IDLE,
        CAPTURING,
        ERROR
    }

    private val _cameraState = MutableStateFlow(CameraState.IDLE)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    /**
     * Takes a photo.
     *
     * Evidence: SDK mode 0x01, CyanBridge MemoMindPacketEncoder
     * Confidence: HIGH — explicit in both SDK and CyanBridge
     */
    suspend fun takePhoto(): Result<Unit> = runCatching {
        modeController.enforceCanCapture()
        _cameraState.value = CameraState.CAPTURING
        try {
            dispatcher.sendModeCommand(HeyCyanMode.PHOTO).getOrThrow()
            // Photo capture is fire-and-trigger; the device takes the photo
            // and the result appears in media counts on next query
        } finally {
            _cameraState.value = CameraState.IDLE
        }
    }

    /**
     * AI Photo — BLOCKED_BY_UNKNOWN_PROTOCOL.
     *
     * The trigger command (mode 0x06) exists in both SDK and CyanBridge,
     * but the chunk reassembly protocol and image output format are not
     * fully understood from the reference material.
     *
     * Evidence: SDK `QCOperatorDeviceModeAIPhoto`, CyanBridge Event 0x06
     * Status: BLOCKED — protocol incomplete
     */
    val aiPhotoStatus: CapabilityStatus = CapabilityStatus.BLOCKED_BY_UNKNOWN_PROTOCOL

    suspend fun triggerAIPhoto(): Result<Unit> = Result.failure(
        UnsupportedOperationException(
            "AI Photo is BLOCKED_BY_UNKNOWN_PROTOCOL: trigger exists (mode 0x06) " +
                "but chunk reassembly and image output format are not understood."
        )
    )
}
