package com.halo.drivers.heycyan.transport.rfcomm

/**
 * Raw RFCOMM control frame packet model for the MemoMind protocol.
 *
 * Framing format (`FA 00`):
 *   [0xFA, 0x00, LengthHigh, LengthLow, Command, Payload..., Checksum]
 *
 * Distinct from BLE GATT packets — this is strictly for RFCOMM control channel communication.
 */
data class MemoMindPacket(
    val command: Int,
    val payload: ByteArray = byteArrayOf(),
) {
    init {
        require(command in 0..0xFF) { "MemoMind command byte must be in 0..255 range" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemoMindPacket) return false
        return command == other.command && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        return 31 * command + payload.contentHashCode()
    }
}
