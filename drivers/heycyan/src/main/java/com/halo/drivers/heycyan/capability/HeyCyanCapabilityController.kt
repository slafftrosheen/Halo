package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanMode
import com.halo.drivers.heycyan.protocol.HeyCyanOperation
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
 *  2. Enforces the video/audio recording exclusion constraint.
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
 *                                    ↓
 *                           ProtocolNotEstablished (checksum unresolved)
 *
 * Zero guessed packets leave Halo.
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
     * Collect this to feed Timeline, Metrics, and Halo Glass Activity without
     * taking any dependency on HeyCyan BLE internals.
     */
    val events: Flow<HeyCyanOperationEvent> = _events.asSharedFlow()

    /**
     * Execute a semantic [HeyCyanOperation].
     *
     * Returns [Result.failure] immediately — without touching the transport — for:
     *  - [HeyCyanCapabilityStatus.UNSUPPORTED] capabilities.
     *  - [HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL] capabilities.
     *  - Conflicting recording modes (video/audio exclusion).
     */
    suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
        val capability = capabilityFor(operation)
        val now = System.currentTimeMillis()

        // Guard: unsupported by device hardware — never reach transport
        if (capability.status == HeyCyanCapabilityStatus.UNSUPPORTED) {
            return failure(
                operation = operation,
                failure = HeyCyanOperationFailure.Unsupported(capability.id),
                timestamp = now,
            )
        }

        // Guard: protocol not established — never reach transport
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
        // The SDK explicitly states these cannot be active simultaneously.
        // Do not auto-stop the other mode — make the conflict explicit.
        if (operation == HeyCyanOperation.StartVideo) {
            val currentMode = stateStore.state.value.mode
            if (currentMode == HeyCyanMode.AUDIO) {
                return failure(
                    operation = operation,
                    failure = HeyCyanOperationFailure.DeviceRejected(
                        "HeyCyan cannot record video while audio recording is active.",
                    ),
                    timestamp = now,
                )
            }
        }

        if (operation == HeyCyanOperation.StartAudioRecording) {
            val currentMode = stateStore.state.value.mode
            if (currentMode == HeyCyanMode.VIDEO) {
                return failure(
                    operation = operation,
                    failure = HeyCyanOperationFailure.DeviceRejected(
                        "HeyCyan cannot record audio while video recording is active.",
                    ),
                    timestamp = now,
                )
            }
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
            HeyCyanOperation.StartVideo,
            HeyCyanOperation.StopVideo -> HeyCyanCapabilities.video
            HeyCyanOperation.StartAudioRecording,
            HeyCyanOperation.StopAudioRecording -> HeyCyanCapabilities.audioRecording
            HeyCyanOperation.GenerateAIPhoto -> HeyCyanCapabilities.aiPhoto
            HeyCyanOperation.EnableTransferMode,
            HeyCyanOperation.SynchronizeMedia -> HeyCyanCapabilities.wifiTransfer
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
