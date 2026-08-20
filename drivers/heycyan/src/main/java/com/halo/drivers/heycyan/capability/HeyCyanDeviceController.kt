package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSynchronizer
import com.halo.drivers.heycyan.media.sync.HeyCyanMediaSyncResult
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceState
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.toCommand
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanController
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanExecutionResult as SdkExecutionResult
import kotlinx.coroutines.flow.StateFlow

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
) : HeyCyanController {
    val state: StateFlow<HeyCyanDeviceState>
        get() = stateStore.state

    override suspend fun execute(operation: HeyCyanOperation): Result<SdkExecutionResult> {
        return when (operation) {
            HeyCyanOperation.SynchronizeMedia -> synchronizeMedia()
            else -> commandRouter.execute(operation).map { SdkExecutionResult.Acknowledged }
        }
    }

    private suspend fun synchronizeMedia(): Result<SdkExecutionResult> {
        val sync = mediaSynchronizer
            ?: return Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.Unsupported("Wi-Fi media synchronizer not configured")
                )
            )

        return sync.synchronize().map { result ->
            SdkExecutionResult.Completed(mapOf("sync_result" to result.toString()))
        }
    }

    fun updateTransportSnapshot(snapshot: com.halo.drivers.heycyan.transport.HeyCyanTransportSnapshot) {
        stateStore.apply(
            com.halo.drivers.heycyan.protocol.HeyCyanEvent.Ready // trigger state store update
        )
    }
}
