package com.halo.drivers.heycyan.ble

import android.bluetooth.BluetoothGattCharacteristic
import com.halo.drivers.heycyan.protocol.HeyCyanCommandTransport
import java.util.UUID

class HeyCyanGattCommandTransport(
    private val gatt: HeyCyanGattClient,
    private val commandCharacteristic: UUID?,
) : HeyCyanCommandTransport {

    override suspend fun send(
        bytes: ByteArray
    ): Result<Unit> {
        val characteristicUuid = commandCharacteristic ?: gatt.findCharacteristicByProperty(
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        )?.uuid

        if (characteristicUuid == null) {
            return Result.failure(
                IllegalStateException("No command characteristic resolved for HeyCyan")
            )
        }

        val success = gatt.write(
            characteristicUuid = characteristicUuid,
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
