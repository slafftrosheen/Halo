package com.halo.sdk.runtime.wearables

import java.io.ByteArrayOutputStream

/** Protocol facts: FerSaiyan/CyanBridge d7e726e, EyevueProtocol/TuneBudsProtocol.
 * Independent bounded codecs; no vendor SDK or Android types in the runtime. */
data class WearableFrame(val command: Int, val payload: ByteArray, val type: Int = 0)

object EyeVueWire {
    fun encode(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        require(command in 0..255 && payload.size <= 65533)
        val length = payload.size + 2
        val checksum = payload.fold(command) { a, b -> a + (b.toInt() and 255) }
        return byteArrayOf(0xAB.toByte(), 0x55, (length ushr 8).toByte(), length.toByte(), command.toByte()) +
            payload + checksum.toByte()
    }

    class Decoder {
        private var buffer = byteArrayOf()
        fun reset() { buffer = byteArrayOf() }
        fun append(chunk: ByteArray): List<WearableFrame> {
            require(chunk.size <= 131072 - buffer.size) { "EyeVue receive buffer limit exceeded" }
            buffer += chunk
            val frames = mutableListOf<WearableFrame>()
            var cursor = 0
            while (cursor + 1 < buffer.size) {
                if (buffer[cursor] != 0xAB.toByte() || buffer[cursor + 1] != 0x55.toByte()) { cursor++; continue }
                if (buffer.size - cursor < 4) break
                val length = ((buffer[cursor + 2].toInt() and 255) shl 8) or (buffer[cursor + 3].toInt() and 255)
                if (length < 2) { cursor++; continue }
                val end = cursor + length + 4
                if (end > buffer.size) break
                val command = buffer[cursor + 4].toInt() and 255
                val payload = buffer.copyOfRange(cursor + 5, end - 1)
                val checksum = payload.fold(command) { a, b -> a + (b.toInt() and 255) } and 255
                if (checksum == (buffer[end - 1].toInt() and 255)) {
                    frames += WearableFrame(command, payload)
                    cursor = end
                } else cursor++
            }
            buffer = buffer.copyOfRange(cursor, buffer.size)
            return frames
        }
    }
}

object TuneBudsWire {
    data class Encoded(val frames: List<ByteArray>, val nextSequence: Int)
    fun encode(command: Int, payload: ByteArray = byteArrayOf(), type: Int = 1,
               packetSize: Int = 20, sequence: Int = 0): Encoded {
        require(command in 0..255 && type in 1..3 && packetSize in 6..260 && sequence in 0..15)
        val capacity = packetSize - 5
        val count = maxOf(1, (payload.size + capacity - 1) / capacity)
        require(count <= 16) { "TuneBuds command exceeds 16 fragments" }
        var next = sequence
        val frames = (0 until count).map { index ->
            val part = payload.copyOfRange(index * capacity, minOf((index + 1) * capacity, payload.size))
            val header = byteArrayOf(next.toByte(), command.toByte(), type.toByte(),
                (((count - 1) shl 4) or index).toByte(), part.size.toByte())
            next = (next + 1) and 15
            header + part
        }
        return Encoded(frames, next)
    }

    fun tlv(payload: ByteArray): Map<Int, ByteArray> {
        val result = linkedMapOf<Int, ByteArray>()
        var cursor = 0
        while (cursor < payload.size) {
            require(payload.size - cursor >= 2) { "Truncated TuneBuds TLV header" }
            val key = payload[cursor++].toInt() and 255
            val size = payload[cursor++].toInt() and 255
            require(size <= payload.size - cursor) { "Truncated TuneBuds TLV value" }
            require(key !in result) { "Duplicate TuneBuds TLV key" }
            result[key] = payload.copyOfRange(cursor, cursor + size)
            cursor += size
        }
        return result
    }

    class Decoder {
        private data class Assembly(val total: Int, var index: Int, val bytes: ByteArrayOutputStream)
        private var buffer = byteArrayOf()
        private val assemblies = mutableMapOf<Pair<Int, Int>, Assembly>()
        fun reset() { buffer = byteArrayOf(); assemblies.clear() }
        fun append(chunk: ByteArray): List<WearableFrame> {
            require(chunk.size <= 65536 - buffer.size) { "TuneBuds receive buffer limit exceeded" }
            buffer += chunk
            val result = mutableListOf<WearableFrame>()
            var cursor = 0
            while (buffer.size - cursor >= 2) {
                // The AB Mate stream multiplexes AI envelopes with command packets.
                if (buffer[cursor] == 0x55.toByte() && buffer[cursor + 1] == 0xAA.toByte()) {
                    var end = cursor + 2
                    while (end + 1 < buffer.size &&
                        !(buffer[end] == 0xA5.toByte() && buffer[end + 1] == 0x5A.toByte())) end++
                    if (end + 1 >= buffer.size) break
                    cursor = end + 2
                    continue
                }
                if (buffer.size - cursor < 5) break
                val sequence = buffer[cursor].toInt() and 255
                val command = buffer[cursor + 1].toInt() and 255
                val type = buffer[cursor + 2].toInt() and 255
                val fragment = buffer[cursor + 3].toInt() and 255
                val total = (fragment ushr 4) + 1
                val index = fragment and 15
                val length = buffer[cursor + 4].toInt() and 255
                if (sequence > 15 || type !in 1..3 || index >= total) { cursor++; continue }
                if (buffer.size - cursor < length + 5) break
                val part = buffer.copyOfRange(cursor + 5, cursor + 5 + length)
                cursor += 5 + length
                val key = command to type
                if (index == 0) {
                    require(assemblies.size < 32 || key in assemblies) { "Too many TuneBuds assemblies" }
                    assemblies[key] = Assembly(total, 0, ByteArrayOutputStream())
                }
                val assembly = assemblies[key] ?: continue
                if (assembly.total != total || assembly.index != index) { assemblies.remove(key); continue }
                assembly.bytes.write(part)
                assembly.index++
                if (assembly.index == total) {
                    result += WearableFrame(command, assembly.bytes.toByteArray(), type)
                    assemblies.remove(key)
                }
            }
            buffer = buffer.copyOfRange(cursor, buffer.size)
            return result
        }
    }
}
