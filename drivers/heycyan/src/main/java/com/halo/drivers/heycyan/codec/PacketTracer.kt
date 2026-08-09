package com.halo.drivers.heycyan.codec

import java.util.UUID

/**
 * Observability interface for raw BLE discovery and traffic.
 *
 * The discovery callbacks deliberately expose metadata without interpreting any
 * payload. This keeps protocol reverse-engineering separate from transport logging.
 */
interface PacketTracer {
    fun onConnectionStateChanged(status: Int, newState: Int) = Unit

    fun onReady() = Unit

    fun onMtuRequested(mtu: Int) = Unit

    fun onDiscoveredService(serviceUuid: UUID) = Unit

    fun onDiscoveredService(serviceUuid: UUID, serviceType: Int) {
        onDiscoveredService(serviceUuid)
    }

    fun onDiscoveredCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        properties: Int
    ) = Unit

    fun onDiscoveredCharacteristic(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        properties: Int,
        permissions: Int
    ) {
        onDiscoveredCharacteristic(serviceUuid, characteristicUuid, properties)
    }

    fun onDiscoveredDescriptor(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        descriptorUuid: UUID,
        permissions: Int
    ) = Unit

    fun onDescriptorWrite(descriptorUuid: UUID, status: Int) = Unit

    fun onMtuChanged(mtu: Int) = Unit

    fun onTx(characteristicUuid: UUID, bytes: ByteArray)
    fun onRx(characteristicUuid: UUID, bytes: ByteArray)
}
