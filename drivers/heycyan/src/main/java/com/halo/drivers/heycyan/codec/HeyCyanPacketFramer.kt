package com.halo.drivers.heycyan.codec

/**
 * Low-level packet framing for HeyCyan BLE protocol.
 *
 * Packet structure (reference-derived from CyanBridge MemoMindPacketEncoder):
 * ```
 * [HEADER=0xAA][LENGTH][COMMAND_ID][PAYLOAD...][CHECKSUM]
 * ```
 *
 * This class handles only the wire format. Command semantics are in
 * HeyCyanCommandEncoder; event semantics are in HeyCyanEventDecoder.
 *
 * Checksum algorithm: Simple additive sum (mod 256). The actual algorithm
 * needs live-capture validation — this is the best inference from CyanBridge.
 */
class HeyCyanPacketFramer {

    companion object {
        const val HEADER: Byte = 0xAA.toByte()
        const val MIN_PACKET_SIZE = 4 // header + length + command_id + checksum
    }

    /**
     * Represents a deframed packet with its constituent parts.
     */
    data class Frame(
        val commandId: Byte,
        val payload: ByteArray,
        val checksum: Byte,
        val checksumValid: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return commandId == other.commandId &&
                payload.contentEquals(other.payload) &&
                checksum == other.checksum &&
                checksumValid == other.checksumValid
        }

        override fun hashCode(): Int {
            var result = commandId.toInt()
            result = 31 * result + payload.contentHashCode()
            result = 31 * result + checksum.toInt()
            result = 31 * result + checksumValid.hashCode()
            return result
        }
    }

    /**
     * Frames a command for BLE transmission.
     *
     * @param commandId The command identifier byte
     * @param payload The command payload (may be empty)
     * @return Complete framed packet ready for GATT write
     */
    fun frame(commandId: Byte, payload: ByteArray = byteArrayOf()): ByteArray {
        val length = MIN_PACKET_SIZE + payload.size
        val checksum = calculateChecksum(commandId, payload, length)

        return byteArrayOf(
            HEADER,
            length.toByte(),
            commandId,
            *payload,
            checksum
        )
    }

    /**
     * Attempts to deframe a received packet.
     *
     * @param raw The raw bytes received from a BLE notification
     * @return Deframed packet, or null if the bytes don't form a valid packet
     */
    fun deframe(raw: ByteArray): Frame? {
        if (raw.size < MIN_PACKET_SIZE) return null
        if (raw[0] != HEADER) return null

        val declaredLength = raw[1].toInt() and 0xFF
        if (declaredLength < MIN_PACKET_SIZE || declaredLength > raw.size) return null

        val commandId = raw[2]
        val payloadSize = declaredLength - MIN_PACKET_SIZE
        val payload = if (payloadSize > 0) {
            raw.copyOfRange(3, 3 + payloadSize)
        } else {
            byteArrayOf()
        }
        val receivedChecksum = raw[declaredLength - 1]
        val expectedChecksum = calculateChecksum(commandId, payload, declaredLength)

        return Frame(
            commandId = commandId,
            payload = payload,
            checksum = receivedChecksum,
            checksumValid = receivedChecksum == expectedChecksum
        )
    }

    private fun calculateChecksum(commandId: Byte, payload: ByteArray, length: Int): Byte {
        // Additive checksum (mod 256) — inferred from CyanBridge.
        // Needs live-capture validation.
        var sum = (HEADER.toInt() and 0xFF) + length + (commandId.toInt() and 0xFF)
        for (b in payload) {
            sum += (b.toInt() and 0xFF)
        }
        return (sum and 0xFF).toByte()
    }
}
