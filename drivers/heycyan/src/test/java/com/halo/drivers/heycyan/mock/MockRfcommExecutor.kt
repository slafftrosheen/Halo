package com.halo.drivers.heycyan.mock

import com.halo.drivers.heycyan.capability.HeyCyanCapabilityException
import com.halo.drivers.heycyan.capability.HeyCyanOperationFailure
import com.halo.drivers.heycyan.capability.HeyCyanRfcommExecutor
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation

/**
 * In-process mock RFCOMM executor for unit testing.
 *
 * Mirrors [MockBleExecutor] — same behaviour model, independent call tracking.
 * Does NOT depend on Android Bluetooth SDK or MemoMindPacketEncoder framing.
 */
class MockRfcommExecutor(
    var behaviour: Behaviour = Behaviour.Succeed,
) : HeyCyanRfcommExecutor {

    /**
     * All operations received by this executor, in order.
     */
    val receivedOperations: MutableList<HeyCyanOperation> = mutableListOf()

    override suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
        receivedOperations.add(operation)
        return when (behaviour) {
            Behaviour.Succeed -> Result.success(Unit)
            is Behaviour.Fail -> Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.Transport(
                        message = "Mock RFCOMM transport failure",
                        cause = (behaviour as? Behaviour.Fail)?.cause,
                    )
                )
            )
            Behaviour.Reject -> Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.DeviceRejected(
                        message = "Mock RFCOMM device rejected operation: $operation"
                    )
                )
            )
            Behaviour.Unavailable -> Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.NotConnected(
                        message = "Mock: RFCOMM transport not connected"
                    )
                )
            )
        }
    }

    fun reset() {
        receivedOperations.clear()
        behaviour = Behaviour.Succeed
    }

    sealed class Behaviour {
        data object Succeed : Behaviour()
        data class Fail(val cause: Throwable? = null) : Behaviour()
        data object Reject : Behaviour()
        data object Unavailable : Behaviour()
    }
}
