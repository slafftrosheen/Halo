package com.halo.drivers.heycyan.codec

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.halo.sdk.runtime.RuntimeLogger
import java.util.UUID

/**
 * Logs raw BLE discovery and packets as readable text for protocol discovery.
 */
class LoggingPacketTracer(private val logger: RuntimeLogger) : PacketTracer {

    override fun onDiscoveredService(serviceUuid: UUID) {
        logger.info("ProtocolDiscovery", "Discovered Service: $serviceUuid")
    }

    override fun onDiscoveredService(serviceUuid: UUID, serviceType: Int) {
        val type = when (serviceType) {
            android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY -> "PRIMARY"
            android.bluetooth.BluetoothGattService.SERVICE_TYPE_SECONDARY -> "SECONDARY"
            else -> "UNKNOWN($serviceType)"
        }
        logger.info("ProtocolDiscovery", "Discovered Service: $serviceUuid (Type: $type)")
    }

    override fun onDiscoveredCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        properties: Int,
        permissions: Int
    ) {
        logger.info(
            "ProtocolDiscovery",
            "  Discovered Characteristic: $characteristicUuid " +
                "(Service: $serviceUuid, Properties: ${characteristicProperties(properties)}, " +
                "Permissions: ${attributePermissions(permissions)})"
        )
    }

    override fun onDiscoveredDescriptor(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        descriptorUuid: UUID,
        permissions: Int
    ) {
        logger.info(
            "ProtocolDiscovery",
            "    Discovered Descriptor: $descriptorUuid " +
                "(Service: $serviceUuid, Characteristic: $characteristicUuid, " +
                "Permissions: ${attributePermissions(permissions)})"
        )
    }

    override fun onDescriptorWrite(descriptorUuid: UUID, status: Int) {
        logger.info(
            "ProtocolDiscovery",
            "    Descriptor write: $descriptorUuid (Status: ${gattStatus(status)})"
        )
    }

    override fun onMtuChanged(mtu: Int) {
        logger.info("ProtocolDiscovery", "Negotiated MTU: $mtu")
    }

    override fun onTx(characteristicUuid: UUID, bytes: ByteArray) {
        logger.info("ProtocolDiscovery", "TX [$characteristicUuid]: ${bytes.toHex()}")
    }

    override fun onRx(characteristicUuid: UUID, bytes: ByteArray) {
        logger.info("ProtocolDiscovery", "RX [$characteristicUuid]: ${bytes.toHex()}")
    }

    private fun characteristicProperties(properties: Int): String = buildList {
        if (properties and BluetoothGattCharacteristic.PROPERTY_BROADCAST != 0) add("BROADCAST")
        if (properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) add("READ")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) add("WRITE_NO_RESPONSE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add("WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add("NOTIFY")
        if (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add("INDICATE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE != 0) add("SIGNED_WRITE")
        if (properties and BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS != 0) add("EXTENDED_PROPS")
    }.ifEmpty { listOf("NONE") }.joinToString("|") + " (0x${properties.toString(16).uppercase().padStart(4, '0')})"

    private fun attributePermissions(permissions: Int): String = buildList {
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ != 0) add("READ")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE != 0) add("WRITE")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED != 0) add("READ_ENCRYPTED")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED != 0) add("WRITE_ENCRYPTED")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM != 0) add("READ_ENCRYPTED_MITM")
        if (permissions and BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM != 0) add("WRITE_ENCRYPTED_MITM")
    }.ifEmpty { listOf("NONE") }.joinToString("|") + " (0x${permissions.toString(16).uppercase().padStart(4, '0')})"

    private fun gattStatus(status: Int): String = when (status) {
        BluetoothGatt.GATT_SUCCESS -> "SUCCESS(0)"
        BluetoothGatt.GATT_FAILURE -> "FAILURE($status)"
        else -> status.toString()
    }

    private fun ByteArray.toHex(): String = joinToString(" ") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }
}
