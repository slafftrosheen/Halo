package com.halo.drivers.heycyan.ble

import android.bluetooth.BluetoothGattCharacteristic
import com.halo.drivers.heycyan.debug.HeyCyanLogger
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
        )?.let { found ->
            // Heuristic: If we found a common standard characteristic (like 0x2A00 Device Name),
            // and it wasn't explicitly requested, it's likely a false positive for a vendor command.
            if (found.uuid.toString().startsWith("00002a")) {
                null
            } else {
                found.uuid
            }
        }

        if (characteristicUuid == null) {
            return Result.failure(
                IllegalStateException("No command characteristic resolved for HeyCyan")
            )
        }

        val bytesHex = bytes.joinToString("") { "%02X".format(it) }
        HeyCyanLogger.i("HALO-TX-TRACE", "GATT TX uuid=$characteristicUuid bytes=$bytesHex")

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
