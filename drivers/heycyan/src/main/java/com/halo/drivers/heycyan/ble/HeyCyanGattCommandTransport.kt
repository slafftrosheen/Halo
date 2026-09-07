package com.halo.drivers.heycyan.ble

import com.halo.drivers.heycyan.debug.HeyCyanLogger
import com.halo.drivers.heycyan.protocol.HeyCyanCommandTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Sends already-framed, evidence-backed HeyCyan commands over the selected GATT
 * command characteristic.
 *
 * The transport deliberately refuses characteristic discovery heuristics. A
 * vendor command must identify its endpoint explicitly; choosing an arbitrary
 * writable characteristic is unsafe and can mutate unrelated device state.
 */
class HeyCyanGattCommandTransport(
    private val gatt: HeyCyanGattClient,
    private val commandCharacteristic: UUID?,
) : HeyCyanCommandTransport {

    private val writeMutex = Mutex()

    override suspend fun send(bytes: ByteArray): Result<Unit> = writeMutex.withLock {
        val characteristicUuid = commandCharacteristic
            ?: return Result.failure(
                IllegalStateException(
                    "No verified HeyCyan command characteristic configured"
                )
            )

        val bytesHex = bytes.joinToString("") { "%02X".format(it) }
        HeyCyanLogger.i(
            "HALO-TX-TRACE",
            "GATT TX uuid=$characteristicUuid bytes=$bytesHex"
        )

        val success = gatt.write(
            characteristicUuid = characteristicUuid,
            payload = bytes,
        )

        return if (success) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "HeyCyan GATT command write failed for verified characteristic $characteristicUuid"
                )
            )
        }
    }
}
