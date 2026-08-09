package com.halo.drivers.heycyan.codec

/**
 * Interface for persistently saving BLE packet traces for later replay or analysis.
 */
interface PacketRecorder {
    suspend fun save(packet: BlePacket)
}
