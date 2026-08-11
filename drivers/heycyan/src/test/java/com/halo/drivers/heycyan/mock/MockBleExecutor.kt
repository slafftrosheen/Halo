package com.halo.drivers.heycyan.mock

import com.halo.drivers.heycyan.capability.HeyCyanBleExecutor
import com.halo.drivers.heycyan.capability.HeyCyanCapabilityException
import com.halo.drivers.heycyan.capability.HeyCyanOperationFailure
import com.halo.drivers.heycyan.protocol.HeyCyanOperation

/**
 * In-process mock BLE executor for unit testing.
 *
 * Behaviour is controlled by [behaviour]:
 *  - [MockBleExecutor.Behaviour.Succeed]   — always returns [Result.success(Unit)]
 *  - [MockBleExecutor.Behaviour.Fail]      — always returns [Result.failure] with [HeyCyanOperationFailure.Transport]
 *  - [MockBleExecutor.Behaviour.Reject]    — returns [HeyCyanOperationFailure.DeviceRejected]
 *  - [MockBleExecutor.Behaviour.Protocol]  — returns [HeyCyanOperationFailure.ProtocolNotEstablished]
 *  - [MockBleExecutor.Behaviour.Unavailable] — executor null simulation (use null instead of this mock)
 *
 * Does NOT depend on Android SDK, BluetoothGatt, or HeyCyanPacketFramer.
 */
class MockBleExecutor(
    var behaviour: Behaviour = Behaviour.Succeed,
) : HeyCyanBleExecutor {

    /**
     * All operations that were received by this executor, in order.
     * Useful for asserting which operations were attempted on BLE.
     */
    val receivedOperations: MutableList<HeyCyanOperation> = mutableListOf()

    override suspend fun execute(operation: HeyCyanOperation): Result<Unit> {
        receivedOperations.add(operation)
        return when (behaviour) {
            Behaviour.Succeed -> Result.success(Unit)
            is Behaviour.Fail -> Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.Transport(
                        message = "Mock BLE transport failure",
                        cause = (behaviour as? Behaviour.Fail)?.cause,
                    )
                )
            )
            Behaviour.Reject -> Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.DeviceRejected(
                        message = "Mock device rejected operation: $operation"
                    )
                )
            )
            Behaviour.Protocol -> Result.failure(
                HeyCyanCapabilityException(
                    HeyCyanOperationFailure.ProtocolNotEstablished(
                        operation = operation,
                        detail = "Mock: BLE packet framing not established for $operation",
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
        data object Protocol : Behaviour()
    }
}
