package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommand

/**
 * Seam for HeyCyan BLE wire framing.
 *
 * Current forensic documentation marks the AA LENGTH COMMAND CHECKSUM
 * structure as medium-confidence. This interface keeps the semantic
 * encoding separate from the unresolved wire framing.
 */
interface HeyCyanWireEncoder {

    fun encode(
        command: HeyCyanCommand
    ): ByteArray
}

/**
 * Temporary implementation that prevents guessed packets from being
 * transmitted until the wire framing is verified from real-device
 * captures or SDK correlation.
 */
class UnverifiedHeyCyanWireEncoder : HeyCyanWireEncoder {

    override fun encode(
        command: HeyCyanCommand
    ): ByteArray {
        throw UnsupportedOperationException(
            "HeyCyan outer BLE command framing has not been verified"
        )
    }
}
