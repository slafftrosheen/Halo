package com.halo.drivers.heycyan.ble

import com.halo.drivers.heycyan.protocol.HeyCyanCommandTransport
import java.util.UUID

class HeyCyanGattCommandTransport(
    private val gatt: HeyCyanGattClient,
    private val commandCharacteristic: UUID?,
) : HeyCyanCommandTransport {

    override suspend fun send(
        bytes: ByteArray
    ): Result<Unit> {
        if (commandCharacteristic == null) {
            return Result.failure(
                IllegalStateException("No command characteristic resolved")
            )
        }

        val success = gatt.write(
            characteristicUuid = commandCharacteristic,
            payload = bytes,
        )

        return if (success) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "HeyCyan GATT command write failed"
                )
            )
        }
    }
}