package com.halo.drivers.heycyan.thumbnail

import com.halo.drivers.heycyan.codec.HeyCyanWireFrameCodec
import com.halo.drivers.heycyan.codec.WireFrameDecodeResult
import com.halo.drivers.heycyan.protocol.HeyCyanCommandCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level wire codec for the HeyCyan 0xFD (ACTION_PICTURE_THUMBNAILS) protocol.
 *
 * Request format:
 * - Action: 0xFD
 * - Payload: [0x01, indexLow, indexHigh] (Little-Endian 16-bit requested chunk index)
 *
 * Response wire format (6-byte BC header + payload):
 * - Header: [0xBC, 0xFD, lenLE(2), crcLE(2)]
 * - Payload: [0x01, totalLE(2), currIndexLE(2), chunkBytes...]
 *   - total: total number of chunks (LE 16-bit)
 *   - currIndex: index of current chunk (LE 16-bit)
 *   - chunkBytes: raw JPEG slice
 *   - Completion condition: currIndex + 1 == total
 */
object HeyCyanThumbnailTransport {

    /**
     * Builds a wire frame to request chunk at [index] (0-based).
     */
    fun buildChunkRequest(index: Int): ByteArray {
        require(index in 0..0xFFFF) { "Chunk index out of 16-bit unsigned range: $index" }
        val payload = byteArrayOf(
            0x01,
            (index and 0xFF).toByte(),
            ((index ushr 8) and 0xFF).toByte(),
        )
        return HeyCyanWireFrameCodec.encode(
            action = HeyCyanCommandCatalog.ACTION_PICTURE_THUMBNAILS,
            payload = payload,
        )
    }

    /**
     * Decodes a raw frame into a [HeyCyanThumbnailChunk].
     * Accepts either a complete BC wire frame or the payload portion.
     */
    fun decodeChunk(bytes: ByteArray): HeyCyanThumbnailChunk? {
        val payload = if (bytes.size >= 6 && bytes[0] == HeyCyanWireFrameCodec.FRAME_HEADER_BYTE) {
            when (val decoded = HeyCyanWireFrameCodec.decode(bytes)) {
                is WireFrameDecodeResult.Success -> {
                    if (decoded.action != HeyCyanCommandCatalog.ACTION_PICTURE_THUMBNAILS) return null
                    if (!decoded.isValidCrc) return null
                    decoded.payload
                }
                else -> return null
            }
        } else {
            bytes
        }

        // Payload must have at least 5 bytes: [0x01, totalLow, totalHigh, currLow, currHigh]
        if (payload.size < 5) return null
        if (payload[0] != 0x01.toByte()) return null

        val buffer = ByteBuffer.wrap(payload, 1, 4).order(ByteOrder.LITTLE_ENDIAN)
        val total = buffer.short.toInt() and 0xFFFF
        val currIndex = buffer.short.toInt() and 0xFFFF

        if (total <= 0 || currIndex >= total) return null

        val chunkData = if (payload.size > 5) {
            payload.copyOfRange(5, payload.size)
        } else {
            ByteArray(0)
        }

        return HeyCyanThumbnailChunk(
            total = total,
            currIndex = currIndex,
            data = chunkData,
            isComplete = (currIndex + 1 == total),
        )
    }
}

/** Reassembles ATT notification fragments into complete BC frames. */
class HeyCyanLargeFrameAssembler(
    private val maxFrameBytes: Int = (HeyCyanThumbnailAssembler.DEFAULT_MAX_BYTES + 64).toInt(),
) {
    private var pending = ByteArray(0)

    fun accept(fragment: ByteArray): List<ByteArray> {
        if (fragment.isEmpty()) return emptyList()
        check(pending.size + fragment.size <= maxFrameBytes) { "HeyCyan frame exceeded bounded receive buffer" }
        pending += fragment
        val completed = mutableListOf<ByteArray>()
        while (pending.isNotEmpty()) {
            val headerIndex = pending.indexOf(HeyCyanWireFrameCodec.FRAME_HEADER_BYTE)
            if (headerIndex < 0) {
                pending = ByteArray(0)
                break
            }
            if (headerIndex > 0) pending = pending.copyOfRange(headerIndex, pending.size)
            if (pending.size < HeyCyanWireFrameCodec.HEADER_SIZE) break
            val payloadLength = (pending[2].toInt() and 0xFF) or
                ((pending[3].toInt() and 0xFF) shl 8)
            val frameLength = HeyCyanWireFrameCodec.HEADER_SIZE + payloadLength
            check(frameLength in HeyCyanWireFrameCodec.HEADER_SIZE..maxFrameBytes) {
                "Invalid HeyCyan frame length: $frameLength"
            }
            if (pending.size < frameLength) break
            completed += pending.copyOfRange(0, frameLength)
            pending = pending.copyOfRange(frameLength, pending.size)
        }
        return completed
    }

    fun reset() {
        pending = ByteArray(0)
    }
}

data class HeyCyanThumbnailChunk(
    val total: Int,
    val currIndex: Int,
    val data: ByteArray,
    val isComplete: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeyCyanThumbnailChunk) return false
        if (total != other.total) return false
        if (currIndex != other.currIndex) return false
        if (!data.contentEquals(other.data)) return false
        if (isComplete != other.isComplete) return false
        return true
    }

    override fun hashCode(): Int {
        var result = total
        result = 31 * result + currIndex
        result = 31 * result + data.contentHashCode()
        result = 31 * result + isComplete.hashCode()
        return result
    }
}
