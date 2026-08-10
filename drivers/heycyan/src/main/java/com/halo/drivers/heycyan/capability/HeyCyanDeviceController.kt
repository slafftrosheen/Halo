package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSynchronizer
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSyncResult
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceState
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.HeyCyanOperation
import com.halo.drivers.heycyan.transport.HeyCyanTransportSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * High-level execution result for [HeyCyanDeviceController.execute].
 */
sealed interface HeyCyanExecutionResult {
    data object Acknowledged : HeyCyanExecutionResult
    data class MediaSynchronized(val summary: HeyCyanMediaSyncResult) : HeyCyanExecutionResult
}

/**
 * Unified Device Controller for HeyCyan smart glasses.
 *
 * Single entry point presenting HeyCyan as ONE device to Halo.
 * Transport selection (BLE GATT, RFCOMM, Wi-Fi) is handled internally by routers/coordinators.
 */
class HeyCyanDeviceController(
    private val commandRouter: HeyCyanCommandRouter,
    private val mediaSynchronizer: HeyCyanMediaSynchronizer? = null,
    private val stateStore: HeyCyanDeviceStateStore = HeyCyanDeviceStateStore(),
) {
    val state: StateFlow<HeyCyanDeviceState>
        get() = stateStore.state

    suspend fun execute(operation: HeyCyanOperation): Result<HeyCyanExecutionResult> {
        return when (operation) {
            HeyCyanOperation.SynchronizeMedia -> synchronizeMedia()
            else -> commandRouter.execute(operation).map { HeyCyanExecutionResult.Acknowledged }
        }
    }

    private suspend fun synchronizeMedia(): Result<HeyCyanExecutionResult> {
        val sync = mediaSynchronizer
            ?: return Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.Unsupported("Wi-Fi media synchronizer not configured")
                )
            )

        return sync.synchronize().map { result ->
            HeyCyanExecutionResult.MediaSynchronized(result)
        }
    }

    fun updateTransportSnapshot(snapshot: HeyCyanTransportSnapshot) {
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.Ready // trigger state store update
        )
    }
}
