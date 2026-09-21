package com.halo.drivers.heycyan.ble

import kotlinx.coroutines.CompletableDeferred
import java.util.UUID

internal sealed interface GattOperation {
    val sequence: Long
    val connectionGeneration: Long
    val completion: CompletableDeferred<GattOperationResult>

    data class CharacteristicWrite(
        override val sequence: Long,
        override val connectionGeneration: Long,
        val characteristicUuid: UUID,
        val value: ByteArray,
        val writeType: Int,
        override val completion: CompletableDeferred<GattOperationResult> = CompletableDeferred()
    ) : GattOperation

    data class DescriptorWrite(
        override val sequence: Long,
        override val connectionGeneration: Long,
        val characteristicUuid: UUID,
        val descriptorUuid: UUID,
        val value: ByteArray,
        override val completion: CompletableDeferred<GattOperationResult> = CompletableDeferred()
    ) : GattOperation
}

sealed interface GattOperationResult {
    data class Success(val callbackStatus: Int, val elapsedMs: Long) : GattOperationResult
    data class InitiationFailed(val platformStatus: Int) : GattOperationResult
    data class CallbackFailed(val callbackStatus: Int) : GattOperationResult
    data object Timeout : GattOperationResult
    data object Disconnected : GattOperationResult
    data object StaleConnection : GattOperationResult
}
