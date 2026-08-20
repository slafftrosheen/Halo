package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanMode
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.toCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The HeyCyan semantic capability controller.
 *
 * This is the single entry point for all capability-level operations. It:
 *
 *  1. Guards blocked/unsupported capabilities before touching the transport.
 *  2. Enforces the video/audio recording exclusion constraint based on state.
 *  3. Drives the [operationState] state machine.
 *  4. Emits structured [HeyCyanOperationEvent]s for Timeline / Metrics / Runtime observers.
 *  5. Delegates to [HeyCyanCommandExecutor] which owns the encode→frame→transport pipeline.
 *
 * Architecture:
 *   HeyCyanCapabilityController
 *       ↓
 *   HeyCyanCommandExecutor
 *       ↓
 *   HeyCyanCommandEncoder → HeyCyanPacketFramer → HeyCyanCommandTransport
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

    /**
     * Transport-agnostic operation event stream.
     */
    val events: Flow<HeyCyanOperationEvent> = _events.asSharedFlow()

    /**
     * Execute a semantic [HeyCyanOperation].
     */
    suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
        val capability = capabilityFor(operation)
        val now = System.currentTimeMillis()

        // Guard: unsupported by device hardware
        if (capability.status == HeyCyanCapabilityStatus.UNSUPPORTED) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.Unsupported(capability.id),
                timestamp = now,
            )
        }

        // Guard: protocol not established
        if (capability.status == HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.ProtocolNotEstablished(
                    operation = operation,
                    detail = capability.description,
                ),
                timestamp = now,
            )
        }

        // Guard: video/audio recording exclusion
        // If we are starting Video, we must not be in Audio recording state.
        val currentState = stateStore.state.value
        
        if (operation == HeyCyanOperation.StartVideo && currentState.mode == HeyCyanMode.AUDIO) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan cannot record video while audio recording is active.",
                ),
                timestamp = now,
            )
        }

        if (operation == HeyCyanOperation.StartVideo && (currentState.mode == HeyCyanMode.VIDEO || currentState.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.RECORDING || currentState.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.STARTING)) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan is already starting or recording video.",
                ),
                timestamp = now,
            )
        }

        if (operation == HeyCyanOperation.StopVideo && (currentState.mode != HeyCyanMode.VIDEO && currentState.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.IDLE)) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan is not currently recording video.",
                ),
                timestamp = now,
            )
        }

        if (operation == HeyCyanOperation.StopVideo && currentState.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.STOPPING) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan is already stopping video recording.",
                ),
                timestamp = now,
            )
        }

        if (operation == HeyCyanOperation.StartRecording && currentState.mode == HeyCyanMode.VIDEO) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan cannot record audio while video recording is active.",
                ),
                timestamp = now,
            )
        }

        if (operation == HeyCyanOperation.StartRecording && currentState.mode == HeyCyanMode.AUDIO) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan is already recording audio.",
                ),
                timestamp = now,
            )
        }

        if (operation == HeyCyanOperation.StopRecording && currentState.mode != HeyCyanMode.AUDIO) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan is not currently recording audio.",
                ),
                timestamp = now,
            )
        }

        // Dispatch
        _operationState.value = HeyCyanOperationState.Running(operation)
        _events.emit(HeyCyanOperationEvent.Started(operation, now))

        val result = executor.execute(operation.toCommand())

        return result
            .onSuccess {
                val ts = System.currentTimeMillis()
                _operationState.value = HeyCyanOperationState.Succeeded(operation)
                _events.emit(HeyCyanOperationEvent.Completed(operation, ts))
            }
            .onFailure { error ->
                val ts = System.currentTimeMillis()
                val opFailure = error.toOperationFailure()
                _operationState.value = HeyCyanOperationState.Failed(
                    operation = operation,
                    reason = opFailure,
                )
                _events.emit(HeyCyanOperationEvent.Failed(operation, ts, opFailure))
            }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun capabilityFor(operation: HeyCyanOperation): HeyCyanCapability =
        when (operation) {
            HeyCyanOperation.GetBattery -> HeyCyanCapabilities.battery
            HeyCyanOperation.GetDeviceInfo -> HeyCyanCapabilities.deviceInfo
            HeyCyanOperation.GetMediaInventory -> HeyCyanCapabilities.mediaInventory
            HeyCyanOperation.TakePhoto -> HeyCyanCapabilities.photo
            HeyCyanOperation.StartVideo, HeyCyanOperation.StopVideo -> HeyCyanCapabilities.video
            HeyCyanOperation.StartRecording, HeyCyanOperation.StopRecording, HeyCyanOperation.PrepareVoiceRouting -> HeyCyanCapabilities.audioRecording
            HeyCyanOperation.GenerateAIPhoto -> HeyCyanCapabilities.aiPhoto
            HeyCyanOperation.EnterWifiTransferMode, HeyCyanOperation.SynchronizeMedia -> HeyCyanCapabilities.wifiTransfer
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
