package com.halo.drivers.heycyan.transport.rfcomm

import java.io.ByteArrayOutputStream

/**
 * Result wrapper for decoded stream frames.
 */
sealed interface MemoMindDecodeResult {
    data class Packet(val packet: MemoMindPacket) : MemoMindDecodeResult
    data class Malformed(val bytes: ByteArray, val reason: String) : MemoMindDecodeResult
}

/**
 * Stateful stream decoder for MemoMind RFCOMM control packets.
 *
 * Handles streaming fragmentation, partial packets split across reads,
 * back-to-back packets in a single buffer, and garbage recovery before header.
 */
class MemoMindPacketDecoder(
    private val checksumCalculator: MemoMindChecksum = DefaultMemoMindChecksum(),
) {
    private val buffer = ByteArrayOutputStream()

    /**
     * Feeds incoming bytes from the RFCOMM socket into the stream decoder.
     *
     * @return List of fully decoded [MemoMindPacket]s extracted from accumulated stream bytes.
     */
    @Synchronized
    fun feed(bytes: ByteArray): List<MemoMindPacket> {
        buffer.write(bytes)
        val extracted = mutableListOf<MemoMindPacket>()

        var currentBytes = buffer.toByteArray()
        while (currentBytes.size >= 5) { // Minimum packet size: FA 00 + LenH + LenL + Cmd + CS = 6 (or min 5)
            val headerIndex = findHeader(currentBytes)
            if (headerIndex < 0) {
                // No FA 00 header found — clear buffer
                buffer.reset()
                break
            }

            if (headerIndex > 0) {
                // Discard garbage before header
                currentBytes = currentBytes.copyOfRange(headerIndex, currentBytes.size)
                buffer.reset()
                buffer.write(currentBytes)
            }

            if (currentBytes.size < 4) {
                break // Wait for more bytes to read total length
            }

            val length = ((currentBytes[2].toInt() and 0xFF) shl 8) or (currentBytes[3].toInt() and 0xFF)
            if (length < 5 || length > 4096) {
                // Invalid length — discard invalid header byte and resume search
                currentBytes = currentBytes.copyOfRange(2, currentBytes.size)
                buffer.reset()
                buffer.write(currentBytes)
                continue
            }

            if (currentBytes.size < length) {
                // Partial packet — wait for remaining bytes
                break
            }

            // Extract complete packet slice
            val rawPacket = currentBytes.copyOfRange(0, length)
            val expectedChecksum = rawPacket[length - 1]
            val computedChecksum = checksumCalculator.calculate(rawPacket.copyOfRange(0, length - 1))

            if (expectedChecksum == computedChecksum) {
                val command = rawPacket[4].toInt() and 0xFF
                val payload = if (length > 6) rawPacket.copyOfRange(5, length - 1) else byteArrayOf()
                extracted.add(MemoMindPacket(command = command, payload = payload))
            }

            // Advance buffer past extracted packet
            val remaining = currentBytes.copyOfRange(length, currentBytes.size)
            buffer.reset()
            buffer.write(remaining)
            currentBytes = remaining
        }

        return extracted
    }

    @Synchronized
    fun reset() {
        buffer.reset()
    }

    private fun findHeader(bytes: ByteArray): Int {
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == HeyCyanRfcommConstants.FRAME_HEADER_0 &&
                bytes[i + 1] == HeyCyanRfcommConstants.FRAME_HEADER_1
            ) {
                return i
            }
        }
        return -1
    }
}
