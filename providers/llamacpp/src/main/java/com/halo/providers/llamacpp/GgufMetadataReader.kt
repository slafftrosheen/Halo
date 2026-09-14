package com.halo.providers.llamacpp

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File

/**
 * Minimal, defensive GGUF metadata reader.
 *
 * It intentionally reads only the header and metadata section; tensor data is never
 * loaded into memory. The reader is used for capability discovery, not model loading.
 */
internal object GgufMetadataReader {
    private const val MAGIC = 0x46554747L
    private const val MIN_VERSION = 2L
    private const val MAX_VERSION = 3L
    private const val MAX_STRING_BYTES = 1L * 1024L * 1024L
    private const val MAX_ARRAY_ELEMENTS = 1_000_000L

    data class Metadata(
        val architecture: String?,
        val contextLength: Int?,
        val blockCount: Int?,
        val embeddingLength: Int?,
        val attentionHeadCount: Int?,
        val attentionHeadCountKv: Int?,
    )

    fun read(file: File): Metadata? {
        if (!file.isFile || file.length() <= 0L) return null
        return try {
            DataInputStream(BufferedInputStream(file.inputStream(), 64 * 1024)).use { input ->
                if (input.readUInt32LE() != MAGIC) return null
                val version = input.readUInt32LE()
                if (version !in MIN_VERSION..MAX_VERSION) return null

                input.readUInt64LE() // tensor count
                val metadataCount = input.readUInt64LE()
                if (metadataCount < 0L || metadataCount > MAX_ARRAY_ELEMENTS) return null

                var architecture: String? = null
                var contextLength: Int? = null
                var blockCount: Int? = null
                var embeddingLength: Int? = null
                var attentionHeadCount: Int? = null
                var attentionHeadCountKv: Int? = null

                // Keep only the small set of architecture metadata needed by the planner.
                // This avoids retaining arbitrary GGUF metadata in memory.
                val pending = HashMap<String, Any?>(6)
                repeat(metadataCount.toInt()) {
                    val key = input.readString()
                    val type = input.readUInt32LE()
                    val value = input.readValue(type)
                    if (key == "general.architecture" && value is String) {
                        architecture = value
                    } else if (
                        key.endsWith(".context_length") ||
                        key.endsWith(".block_count") ||
                        key.endsWith(".embedding_length") ||
                        key.endsWith(".attention.head_count") ||
                        key.endsWith(".attention.head_count_kv")
                    ) {
                        pending[key] = value
                    }
                }

                architecture?.let { prefix ->
                    contextLength = pending["$prefix.context_length"].asPositiveInt()
                    blockCount = pending["$prefix.block_count"].asPositiveInt()
                    embeddingLength = pending["$prefix.embedding_length"].asPositiveInt()
                    attentionHeadCount = pending["$prefix.attention.head_count"].asPositiveInt()
                    attentionHeadCountKv = pending["$prefix.attention.head_count_kv"].asPositiveInt()
                }

                Metadata(architecture, contextLength, blockCount, embeddingLength, attentionHeadCount, attentionHeadCountKv)
            }
        } catch (_: EOFException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun DataInputStream.readValue(type: Long): Any? = when (type) {
        0L -> readUInt8()
        1L -> readInt8()
        2L -> readUInt16LE()
        3L -> readInt16LE()
        4L -> readUInt32LE()
        5L -> readInt32LE()
        6L -> readFloatLE()
        7L -> readUInt8() != 0
        8L -> readString()
        9L -> {
            val elementType = readUInt32LE()
            val count = readUInt64LE()
            if (count < 0L || count > MAX_ARRAY_ELEMENTS) throw IllegalArgumentException("Invalid GGUF array length")
            repeat(count.toInt()) { readValue(elementType) }
            null
        }
        10L -> readUInt64LE()
        11L -> readInt64LE()
        12L -> readDoubleLE()
        else -> throw IllegalArgumentException("Unsupported GGUF metadata type: $type")
    }

    private fun Any?.asPositiveInt(): Int? = when (this) {
        is Byte -> if (this > 0) toInt() else null
        is Short -> if (this > 0) toInt() else null
        is Int -> if (this > 0) this else null
        is Long -> if (this in 1L..Int.MAX_VALUE.toLong()) toInt() else null
        is Float -> if (this > 0f && this <= Int.MAX_VALUE) toInt() else null
        is Double -> if (this > 0.0 && this <= Int.MAX_VALUE) toInt() else null
        else -> null
    }

    private fun DataInputStream.readString(): String {
        val length = readUInt64LE()
        if (length < 0L || length > MAX_STRING_BYTES || length > available().toLong()) {
            throw IllegalArgumentException("Invalid GGUF string length: $length")
        }
        val bytes = ByteArray(length.toInt())
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun DataInputStream.readUInt8(): Int = readUnsignedByte()
    private fun DataInputStream.readInt8(): Byte = readByte()
    private fun DataInputStream.readUInt16LE(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        return b0 or (b1 shl 8)
    }
    private fun DataInputStream.readInt16LE(): Short = readUInt16LE().toShort()
    private fun DataInputStream.readUInt32LE(): Long {
        val b0 = readUnsignedByte().toLong()
        val b1 = readUnsignedByte().toLong()
        val b2 = readUnsignedByte().toLong()
        val b3 = readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
    private fun DataInputStream.readInt32LE(): Int = readUInt32LE().toInt()
    private fun DataInputStream.readUInt64LE(): Long {
        var result = 0L
        repeat(8) { index -> result = result or (readUnsignedByte().toLong() shl (index * 8)) }
        return result
    }
    private fun DataInputStream.readInt64LE(): Long = readUInt64LE()
    private fun DataInputStream.readFloatLE(): Float = Float.fromBits(readInt32LE())
    private fun DataInputStream.readDoubleLE(): Double = Double.fromBits(readInt64LE())
}
