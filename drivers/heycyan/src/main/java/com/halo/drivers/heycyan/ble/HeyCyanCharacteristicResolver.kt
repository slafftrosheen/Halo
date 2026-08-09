package com.halo.drivers.heycyan.ble

import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

/**
 * Resolves HeyCyan GATT characteristics by their BLE property flags.
 *
 * The HeyCyan SDK embeds characteristic UUIDs in compiled binaries only.
 * Rather than hardcoding UUIDs that may vary across firmware versions,
 * we discover the correct characteristics at runtime after GATT service
 * discovery completes, matching by property flags.
 *
 * Evidence: SDK README notes UUIDs are in binaries; CyanBridge discovers dynamically.
 */
class HeyCyanCharacteristicResolver {

    data class ResolvedCharacteristics(
        val commandCharacteristic: UUID,
        val notificationCharacteristic: UUID,
        val indicationCharacteristic: UUID?,
        val readCharacteristic: UUID?
    )

    /**
     * Resolves the required characteristics from discovered GATT services.
     *
     * @param characteristics Map of UUID → BluetoothGattCharacteristic from service discovery
     * @return Resolved characteristics, or null if required characteristics are missing
     */
    fun resolve(
        characteristics: Map<UUID, BluetoothGattCharacteristic>
    ): ResolvedCharacteristics? {
        val writeChar = findByProperty(
            characteristics,
            BluetoothGattCharacteristic.PROPERTY_WRITE
        ) ?: findByProperty(
            characteristics,
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        )

        val notifyChar = findByProperty(
            characteristics,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY
        )

        if (writeChar == null || notifyChar == null) return null

        val indicateChar = findByProperty(
            characteristics,
            BluetoothGattCharacteristic.PROPERTY_INDICATE
        )

        val readChar = findByProperty(
            characteristics,
            BluetoothGattCharacteristic.PROPERTY_READ
        )

        return ResolvedCharacteristics(
            commandCharacteristic = writeChar,
            notificationCharacteristic = notifyChar,
            indicationCharacteristic = indicateChar,
            readCharacteristic = readChar
        )
    }

    private fun findByProperty(
        characteristics: Map<UUID, BluetoothGattCharacteristic>,
        property: Int
    ): UUID? {
        return characteristics.entries.firstOrNull { (_, char) ->
            char.properties and property != 0
        }?.key
    }
}
