package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent

/**
 * Responsible for parsing raw BLE GATT notifications into domain HeyCyanEvents.
 */
class HeyCyanEventDecoder {
    fun decode(payload: ByteArray): HeyCyanEvent? {
        // TODO: "Protocol not yet decoded. PacketTracer is currently logging raw bytes."
        // For now, we do not emit any parsed events since we do not know the actual protocol.
        return null
    }
}
