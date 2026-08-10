package com.halo.drivers.heycyan.transport.rfcomm

/**
 * Checksum calculation strategy for MemoMind RFCOMM control packets.
 */
interface MemoMindChecksum {
    fun calculate(bytes: ByteArray): Byte
}

/**
 * Default checksum calculator: 8-bit sum modulo 256 of all frame bytes.
 */
class DefaultMemoMindChecksum : MemoMindChecksum {
    override fun calculate(bytes: ByteArray): Byte {
        var sum = 0
        for (b in bytes) {
            sum = (sum + (b.toInt() and 0xFF)) and 0xFF
        }
        return sum.toByte()
    }
}

/**
 * Source-faithful encoder for MemoMind RFCOMM packets.
 *
 * Encodes [MemoMindPacket] into `FA 00` framed byte arrays:
 *   [0xFA, 0x00, LenH, LenL, Command, Payload..., Checksum]
 */
class MemoMindPacketEncoder(
    private val checksumCalculator: MemoMindChecksum = DefaultMemoMindChecksum(),
) {
    fun encode(packet: MemoMindPacket): ByteArray {
        val totalLength = 2 + 2 + 1 + packet.payload.size + 1 // FA 00 + Len(2) + Cmd(1) + Payload + CS(1)
        val frame = ByteArray(totalLength)

        frame[0] = HeyCyanRfcommConstants.FRAME_HEADER_0
        frame[1] = HeyCyanRfcommConstants.FRAME_HEADER_1
        frame[2] = ((totalLength ushr 8) and 0xFF).toByte()
        frame[3] = (totalLength and 0xFF).toByte()
        frame[4] = packet.command.toByte()

        if (packet.payload.isNotEmpty()) {
            packet.payload.copyInto(frame, destinationOffset = 5)
        }

        // Checksum computed over all preceding bytes [0 .. totalLength-2]
        val payloadForChecksum = frame.copyOfRange(0, totalLength - 1)
        val cs = checksumCalculator.calculate(payloadForChecksum)
        frame[totalLength - 1] = cs

        return frame
    }
}
