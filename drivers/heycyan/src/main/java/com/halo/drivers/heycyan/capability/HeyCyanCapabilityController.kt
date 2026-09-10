package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.AudioRecordingState
import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import com.halo.drivers.heycyan.protocol.VideoRecordingState
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Semantic capability controller for HeyCyan.
 *
 * This is the only execution gate used by the active runtime path. It owns
 * capability availability checks, connection checks, mutually-exclusive
 * recording guards, operation lifecycle, and the boundary to wire execution.
 */
class HeyCyanCapabilityController(
    private val executor: HeyCyanCommandExecutor,
    private val stateStore: HeyCyanDeviceStateStore,
) {

    private val _operationState =
        MutableStateFlow<HeyCyanOperationState>(HeyCyanOperationState.Idle)

    /** Current lifecycle state of the most recently dispatched operation. */
    val operationState: StateFlow<HeyCyanOperationState> =
        _operationState.asStateFlow()

    private val _events =
        MutableSharedFlow<HeyCyanOperationEvent>(extraBufferCapacity = 64)

    /** Transport-agnostic operation event stream. */
    val events: Flow<HeyCyanOperationEvent> = _events.asSharedFlow()

    /**
     * Execute a semantic HeyCyan operation.
     *
     * Physical actions are never attempted while the device is disconnected,
     * and protocol-blocked capabilities fail before reaching the executor.
     */
    suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
        val capability = capabilityFor(operation)
        val now = System.currentTimeMillis()

        when (capability.status) {
            HeyCyanCapabilityStatus.UNSUPPORTED -> {
                return failure(
                    operation,
                    HeyCyanOperationFailure.Unsupported(capability.id),
                    now,
                )
            }
            HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL,
            HeyCyanCapabilityStatus.STRUCTURAL_VALIDATION,
            HeyCyanCapabilityStatus.PLATFORM_LIMITED -> {
                return failure(
                    operation,
                    HeyCyanOperationFailure.ProtocolNotEstablished(
                        operation = operation,
                        detail = capability.description,
                    ),
                    now,
                )
            }
            HeyCyanCapabilityStatus.AVAILABLE,
            HeyCyanCapabilityStatus.AVAILABLE_WITH_LIMITATION -> Unit
        }

        val currentState = stateStore.state.value
        if (currentState.connection !in setOf(
                HeyCyanConnectionState.CONNECTED,
                HeyCyanConnectionState.READY,
            )
        ) {
            return failure(
                operation,
                HeyCyanOperationFailure.NotConnected(
                    "HeyCyan is not ready (state: ${currentState.connection})"
                ),
                now,
            )
        }

        validateRecordingState(operation, currentState)?.let { rejected ->
            return failure(operation, rejected, now)
        }

        // Maintain explicit transient state for UI/diagnostics. Completion still
        // depends on the device's decoded state events; we do not claim physical
        // completion merely because the BLE write returned successfully.
        applyTransientState(operation)

        _operationState.value = HeyCyanOperationState.Running(operation)
        _events.emit(HeyCyanOperationEvent.Started(operation, now))

        return try {
            executor.execute(operation.toCommand())
                .onSuccess {
                    val ts = System.currentTimeMillis()
                    _operationState.value = HeyCyanOperationState.Succeeded(operation)
                    _events.emit(HeyCyanOperationEvent.Completed(operation, ts))
                }
                .onFailure { error ->
                    val ts = System.currentTimeMillis()
                    val opFailure = error.toOperationFailure()
                    _operationState.value = HeyCyanOperationState.Failed(operation, opFailure)
                    _events.emit(HeyCyanOperationEvent.Failed(operation, ts, opFailure))
                    clearTransientState(operation)
                }
        } catch (error: kotlinx.coroutines.CancellationException) {
            clearTransientState(operation)
            throw error
        }
    }

    private fun validateRecordingState(
        operation: HeyCyanOperation,
        state: com.halo.drivers.heycyan.protocol.HeyCyanDeviceState,
    ): HeyCyanOperationFailure? = when (operation) {
        HeyCyanOperation.StartVideo -> when {
            state.mode == HeyCyanMode.AUDIO || state.audioRecordingState == AudioRecordingState.RECORDING ->
                HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan cannot record video while audio recording is active."
                )
            state.mode == HeyCyanMode.VIDEO ||
                state.videoRecordingState == VideoRecordingState.STARTING ||
                state.videoRecordingState == VideoRecordingState.RECORDING ||
                state.videoRecordingState == VideoRecordingState.STOPPING ->
                HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan is already starting or recording video."
                )
            else -> null
        }

        HeyCyanOperation.StopVideo -> when {
            state.videoRecordingState == VideoRecordingState.STOPPING ->
                HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan is already stopping video recording."
                )
            state.mode == HeyCyanMode.VIDEO ||
                state.videoRecordingState == VideoRecordingState.RECORDING ||
                state.videoRecordingState == VideoRecordingState.STARTING -> null
            else -> HeyCyanOperationFailure.DeviceRejected(
                "HeyCyan is not currently recording video."
            )
        }

        HeyCyanOperation.StartRecording -> when {
            state.mode == HeyCyanMode.VIDEO || state.videoRecordingState == VideoRecordingState.RECORDING ->
                HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan cannot record audio while video recording is active."
                )
            state.mode == HeyCyanMode.AUDIO || state.audioRecordingState == AudioRecordingState.RECORDING ->
                HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan is already recording audio."
                )
            else -> null
        }

        HeyCyanOperation.StopRecording -> when {
            state.audioRecordingState == AudioRecordingState.RECORDING || state.mode == HeyCyanMode.AUDIO -> null
            else -> HeyCyanOperationFailure.DeviceRejected(
                "HeyCyan is not currently recording audio."
            )
        }

        else -> null
    }

    private fun applyTransientState(operation: HeyCyanOperation) {
        when (operation) {
            HeyCyanOperation.StartVideo ->
                stateStore.apply(HeyCyanEvent.VideoRecordingStarting)
            HeyCyanOperation.StopVideo ->
                stateStore.apply(HeyCyanEvent.VideoRecordingStopping)
            HeyCyanOperation.StartRecording ->
                stateStore.apply(HeyCyanEvent.AudioRecordingStarting)
            HeyCyanOperation.StopRecording ->
                stateStore.apply(HeyCyanEvent.AudioRecordingStopping)
            else -> Unit
        }
    }

    private fun clearTransientState(operation: HeyCyanOperation) {
        when (operation) {
            HeyCyanOperation.StartVideo,
            HeyCyanOperation.StopVideo -> stateStore.apply(HeyCyanEvent.VideoRecordingResetPending)
            HeyCyanOperation.StartRecording,
            HeyCyanOperation.StopRecording -> stateStore.apply(HeyCyanEvent.AudioRecordingResetPending)
            else -> Unit
        }
    }

    private fun capabilityFor(operation: HeyCyanOperation): HeyCyanCapability = when (operation) {
        HeyCyanOperation.GetBattery -> HeyCyanCapabilities.battery
        HeyCyanOperation.GetDeviceInfo -> HeyCyanCapabilities.deviceInfo
        HeyCyanOperation.GetMediaInventory -> HeyCyanCapabilities.mediaInventory
        HeyCyanOperation.TakePhoto -> HeyCyanCapabilities.photo
        HeyCyanOperation.StartVideo,
        HeyCyanOperation.StopVideo -> HeyCyanCapabilities.video
        HeyCyanOperation.StartRecording,
        HeyCyanOperation.StopRecording -> HeyCyanCapabilities.audioRecording
        HeyCyanOperation.PrepareVoiceRouting -> HeyCyanCapabilities.liveAudio
        HeyCyanOperation.GenerateAIPhoto -> HeyCyanCapabilities.aiPhoto
        HeyCyanOperation.EnterWifiTransferMode -> HeyCyanCapabilities.wifiTransfer
        HeyCyanOperation.SynchronizeMedia ->
            HeyCyanCapabilities.mediaInventory
    }

    private suspend fun failure(
        operation: HeyCyanOperation,
        failure: HeyCyanOperationFailure,
        timestamp: Long,
    ): Result<Unit> {
        _operationState.value = HeyCyanOperationState.Failed(operation, failure)
        _events.emit(HeyCyanOperationEvent.Failed(operation, timestamp, failure))
        return Result.failure(HeyCyanCapabilityException(failure))
    }
}
