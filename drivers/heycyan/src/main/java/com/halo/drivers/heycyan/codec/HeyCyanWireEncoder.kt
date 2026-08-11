package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommand

/**
 * Wire encoder for HeyCyan BLE packets.
 *
 * This interface defines the contract for encoding commands into BLE wire format.
 * The exact outer framing/checksum structure is NOT yet verified and should
 * not be implemented without proper capture evidence.
 */
interface HeyCyanWireEncoder {

    /**
     * Encodes a HeyCyan command into a complete BLE packet.
     *
     * Throws UnsupportedOperationException if the wire format has not been verified.
     */
    fun encode(command: HeyCyanCommand): ByteArray
}

/**
 * Unverified wire encoder that throws until proper capture evidence is available.
 *
 * The forensic documentation explicitly says:
 * [HEADER=0xAA][LENGTH][COMMAND_ID][PAYLOAD...][CHECKSUM]
 * is medium confidence, while the mode values themselves are high confidence.
 *
 * Therefore this encoder keeps us honest by not pretending to have a verified framing.
 */
class UnverifiedHeyCyanWireEncoder : HeyCyanWireEncoder {

    override fun encode(command: HeyCyanCommand): ByteArray {
        throw UnsupportedOperationException(
            "HeyCyan outer BLE command framing has not been verified. " +
            "Use encodePayload() for semantic payload only. " +
            "Capture evidence required for wire format implementation."
        )
    }
}