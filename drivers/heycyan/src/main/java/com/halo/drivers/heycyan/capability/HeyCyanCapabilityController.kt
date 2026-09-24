package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.protocol.HeyCyanMode
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.toCommand
import com.halo.drivers.heycyan.arbiter.HeyCyanOperationLease
import com.halo.drivers.heycyan.arbiter.HeyCyanResource
import com.halo.drivers.heycyan.arbiter.HeyCyanResourceArbiter
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
    private val resourceArbiter: HeyCyanResourceArbiter? = null,
    private val aiThumbnailCapture: (suspend () -> Result<Unit>)? = null,
) {
    private var videoLease: HeyCyanOperationLease? = null
    private var audioLease: HeyCyanOperationLease? = null
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

        if (operation == HeyCyanOperation.GenerateAIPhoto) {
            return executeAiThumbnail(operation, now)
        }

        val currentState = stateStore.state.value
        if (operation == HeyCyanOperation.RouteAudioToGlasses &&
            currentState.connection != HeyCyanConnectionState.READY
        ) {
            return failure(
                operation,
                HeyCyanOperationFailure.DeviceRejected(
                    "HeyCyan must be connected and READY before speaker routing can be changed."
                ),
                now,
            )
        }
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

        val commandLease = acquireResources(operation) ?: return failure(
            operation,
            HeyCyanOperationFailure.DeviceRejected("HeyCyan resources are busy with a conflicting operation."),
            now,
        )

        _operationState.value = HeyCyanOperationState.Running(operation)
        _events.emit(HeyCyanOperationEvent.Started(operation, now))

        return executor.execute(operation.toCommand())
            .onSuccess {
                commitLease(operation, commandLease)
                val ts = System.currentTimeMillis()
                _operationState.value = HeyCyanOperationState.Succeeded(operation)
                _events.emit(HeyCyanOperationEvent.Completed(operation, ts))
            }
            .onFailure { error ->
                commandLease?.release()
                val ts = System.currentTimeMillis()
                val opFailure = error.toOperationFailure()
                _operationState.value = HeyCyanOperationState.Failed(operation, opFailure)
                _events.emit(HeyCyanOperationEvent.Failed(operation, ts, opFailure))
            }
    }

    private suspend fun executeAiThumbnail(
        operation: HeyCyanOperation,
        timestamp: Long,
    ): Result<Unit> {
        if (stateStore.state.value.connection != HeyCyanConnectionState.READY) {
            return failure(
                operation,
                HeyCyanOperationFailure.DeviceRejected("HeyCyan must be READY before AI thumbnail capture."),
                timestamp,
            )
        }
        val capture = aiThumbnailCapture ?: return failure(
            operation,
            HeyCyanOperationFailure.Unsupported("AI thumbnail capture service is unavailable"),
            timestamp,
        )
        _operationState.value = HeyCyanOperationState.Running(operation)
        _events.emit(HeyCyanOperationEvent.Started(operation, timestamp))
        return capture()
            .onSuccess {
                val completedAt = System.currentTimeMillis()
                _operationState.value = HeyCyanOperationState.Succeeded(operation)
                _events.emit(HeyCyanOperationEvent.Completed(operation, completedAt))
            }
            .onFailure { error ->
                val failedAt = System.currentTimeMillis()
                val failure = error.toOperationFailure()
                _operationState.value = HeyCyanOperationState.Failed(operation, failure)
                _events.emit(HeyCyanOperationEvent.Failed(operation, failedAt, failure))
            }
    }

    private fun acquireResources(operation: HeyCyanOperation): HeyCyanOperationLease? {
        val arbiter = resourceArbiter ?: return NO_OP_LEASE
        val resources = when (operation) {
            HeyCyanOperation.TakePhoto -> setOf(HeyCyanResource.CAMERA, HeyCyanResource.CONTROL_CHANNEL)
            HeyCyanOperation.StartVideo -> setOf(HeyCyanResource.CAMERA, HeyCyanResource.VIDEO_RECORDER)
            HeyCyanOperation.StartRecording -> setOf(HeyCyanResource.AUDIO_RECORDER)
            HeyCyanOperation.GenerateAIPhoto -> setOf(HeyCyanResource.CAMERA, HeyCyanResource.BLE_BULK_TRANSFER)
            else -> setOf(HeyCyanResource.CONTROL_CHANNEL)
        }
        return arbiter.tryAcquire(operation.toString(), resources)
    }

    private fun commitLease(operation: HeyCyanOperation, lease: HeyCyanOperationLease?) {
        if (lease == null) return
        when (operation) {
            HeyCyanOperation.StartVideo -> videoLease = lease
            HeyCyanOperation.StartRecording -> audioLease = lease
            HeyCyanOperation.StopVideo -> {
                lease.release()
                videoLease?.release()
                videoLease = null
            }
            HeyCyanOperation.StopRecording -> {
                lease.release()
                audioLease?.release()
                audioLease = null
            }
            else -> lease.release()
        }
    }

    fun releaseLongRunningResources() {
        videoLease?.release()
        videoLease = null
        audioLease?.release()
        audioLease = null
    }

    private fun capabilityFor(operation: HeyCyanOperation): HeyCyanCapability = when (operation) {
        HeyCyanOperation.GetBattery -> HeyCyanCapabilities.battery
        HeyCyanOperation.GetDeviceInfo -> HeyCyanCapabilities.deviceInfo
        HeyCyanOperation.GetMediaInventory -> HeyCyanCapabilities.mediaInventory
        HeyCyanOperation.TakePhoto -> HeyCyanCapabilities.photo
        HeyCyanOperation.StartVideo, HeyCyanOperation.StopVideo -> HeyCyanCapabilities.video
        HeyCyanOperation.StartRecording, HeyCyanOperation.StopRecording -> HeyCyanCapabilities.audioRecording
        HeyCyanOperation.PrepareVoiceRouting -> HeyCyanCapabilities.liveAudio
        HeyCyanOperation.RouteAudioToGlasses, HeyCyanOperation.RouteAudioToPhone, HeyCyanOperation.EnableClassicBluetooth -> HeyCyanCapabilities.speakerOutput
        HeyCyanOperation.GenerateAIPhoto -> HeyCyanCapabilities.aiPhoto
        HeyCyanOperation.EnterWifiTransferMode, HeyCyanOperation.ExitWifiTransferMode, HeyCyanOperation.SynchronizeMedia -> HeyCyanCapabilities.wifiTransfer
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

    private companion object {
        val NO_OP_LEASE = object : HeyCyanOperationLease {
            override val id = "no-op"
            override val operation = "no-op"
            override val resources = emptySet<HeyCyanResource>()
            override val acquiredAtMs = 0L
            override val isActive = false
            override fun release() = Unit
        }
    }
}
