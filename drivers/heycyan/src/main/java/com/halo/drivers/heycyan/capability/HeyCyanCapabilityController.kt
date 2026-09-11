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
 * This is the single entry point for capability-level operations and delegates
 * verified wire encoding/transmission to HeyCyanCommandExecutor.
 */
class HeyCyanCapabilityController(
    private val executor: HeyCyanCommandExecutor,
    private val stateStore: HeyCyanDeviceStateStore,
) {
    private val _operationState = MutableStateFlow<HeyCyanOperationState>(HeyCyanOperationState.Idle)
    val operationState: StateFlow<HeyCyanOperationState> = _operationState.asStateFlow()

    private val _events = MutableSharedFlow<HeyCyanOperationEvent>(extraBufferCapacity = 64)
    val events: Flow<HeyCyanOperationEvent> = _events.asSharedFlow()

    suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
        val now = System.currentTimeMillis()

        if (operation == HeyCyanOperation.SynchronizeMedia) {
            return failure(
                operation,
                HeyCyanOperationFailure.Unsupported(
                    "SynchronizeMedia is a media-service operation and must be executed by HeyCyanMediaSynchronizer"
                ),
                now,
            )
        }

        val capability = capabilityFor(operation)
        if (capability.status == HeyCyanCapabilityStatus.UNSUPPORTED) {
            return failure(operation, HeyCyanOperationFailure.Unsupported(capability.id), now)
        }
        if (capability.status == HeyCyanCapabilityStatus.BLOCKED_BY_PROTOCOL ||
            capability.status == HeyCyanCapabilityStatus.STRUCTURAL_VALIDATION
        ) {
            return failure(
                operation,
                HeyCyanOperationFailure.ProtocolNotEstablished(operation, capability.description),
                now,
            )
        }

        val currentState = stateStore.state.value
        if (operation == HeyCyanOperation.StartVideo && currentState.mode == HeyCyanMode.AUDIO) {
            return failure(operation, HeyCyanOperationFailure.DeviceRejected("HeyCyan cannot record video while audio recording is active."), now)
        }
        if (operation == HeyCyanOperation.StartVideo &&
            (currentState.mode == HeyCyanMode.VIDEO ||
                currentState.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.RECORDING ||
                currentState.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.STARTING)
        ) {
            return failure(operation, HeyCyanOperationFailure.DeviceRejected("HeyCyan is already starting or recording video."), now)
        }
        if (operation == HeyCyanOperation.StopVideo &&
            currentState.mode != HeyCyanMode.VIDEO &&
            currentState.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.IDLE
        ) {
            return failure(operation, HeyCyanOperationFailure.DeviceRejected("HeyCyan is not currently recording video."), now)
        }
        if (operation == HeyCyanOperation.StopVideo && currentState.videoRecordingState == com.halo.drivers.heycyan.protocol.VideoRecordingState.STOPPING) {
            return failure(operation, HeyCyanOperationFailure.DeviceRejected("HeyCyan is already stopping video recording."), now)
        }
        if (operation == HeyCyanOperation.StartRecording && currentState.mode == HeyCyanMode.VIDEO) {
            return failure(operation, HeyCyanOperationFailure.DeviceRejected("HeyCyan cannot record audio while video recording is active."), now)
        }
        if (operation == HeyCyanOperation.StartRecording && currentState.mode == HeyCyanMode.AUDIO) {
            return failure(operation, HeyCyanOperationFailure.DeviceRejected("HeyCyan is already recording audio."), now)
        }
        if (operation == HeyCyanOperation.StopRecording && currentState.mode != HeyCyanMode.AUDIO) {
            return failure(operation, HeyCyanOperationFailure.DeviceRejected("HeyCyan is not currently recording audio."), now)
        }

        _operationState.value = HeyCyanOperationState.Running(operation)
        _events.emit(HeyCyanOperationEvent.Started(operation, now))

        return executor.execute(operation.toCommand())
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
            }
    }

    private fun capabilityFor(operation: HeyCyanOperation): HeyCyanCapability = when (operation) {
        HeyCyanOperation.GetBattery -> HeyCyanCapabilities.battery
        HeyCyanOperation.GetDeviceInfo -> HeyCyanCapabilities.deviceInfo
        HeyCyanOperation.GetMediaInventory -> HeyCyanCapabilities.mediaInventory
        HeyCyanOperation.TakePhoto -> HeyCyanCapabilities.photo
        HeyCyanOperation.StartVideo, HeyCyanOperation.StopVideo -> HeyCyanCapabilities.video
        HeyCyanOperation.StartRecording, HeyCyanOperation.StopRecording -> HeyCyanCapabilities.audioRecording
        HeyCyanOperation.PrepareVoiceRouting -> HeyCyanCapabilities.liveAudio
        HeyCyanOperation.RouteAudioToGlasses -> HeyCyanCapabilities.speakerOutput
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
