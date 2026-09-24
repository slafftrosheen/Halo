package com.halo.drivers.heycyan.thumbnail

import java.io.ByteArrayOutputStream

/**
 * Reassembles incoming BLE thumbnail chunks into a contiguous byte array.
 *
 * Enforces hardware safety limits:
 * - Minimum thumbnail size: 1 KB (corrupted/empty transfers rejected)
 * - Maximum thumbnail size: 2 MB (prevents memory exhaustion)
 * - Validates chunk index sequence and completeness
 */
class HeyCyanThumbnailAssembler(
    val minBytes: Long = DEFAULT_MIN_BYTES,
    val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    companion object {
        const val DEFAULT_MIN_BYTES = 1024L
        const val DEFAULT_MAX_BYTES = 2L * 1024L * 1024L
        const val MAX_CHUNKS = 8_192
    }

    private val chunks = mutableMapOf<Int, ByteArray>()
    private var totalExpected = -1
    private var totalAccumulatedBytes = 0L

    sealed interface AssemblyStatus {
        data class InProgress(
            val receivedChunks: Int,
            val totalChunks: Int,
            val nextNeededIndex: Int,
        ) : AssemblyStatus

        data class Completed(
            val bytes: ByteArray,
        ) : AssemblyStatus {
            override fun equals(other: Any?): Boolean =
                other is Completed && bytes.contentEquals(other.bytes)

            override fun hashCode(): Int = bytes.contentHashCode()
        }

        data class Failed(val reason: String) : AssemblyStatus
    }

    fun reset() {
        chunks.clear()
        totalExpected = -1
        totalAccumulatedBytes = 0L
    }

    fun addChunk(chunk: HeyCyanThumbnailChunk): AssemblyStatus {
        if (chunk.total !in 1..MAX_CHUNKS) {
            return AssemblyStatus.Failed("Impossible thumbnail chunk count: ${chunk.total}")
        }
        if (chunk.data.isEmpty()) {
            return AssemblyStatus.Failed("Thumbnail chunk ${chunk.currIndex} was empty")
        }
        if (totalExpected == -1) {
            totalExpected = chunk.total
        } else if (chunk.total != totalExpected) {
            return AssemblyStatus.Failed(
                "Chunk total mismatch: expected $totalExpected, got ${chunk.total}",
            )
        }

        if (chunk.currIndex < 0 || chunk.currIndex >= totalExpected) {
            return AssemblyStatus.Failed("Invalid chunk index: ${chunk.currIndex} of $totalExpected")
        }

        val existing = chunks[chunk.currIndex]
        if (existing != null && !existing.contentEquals(chunk.data)) {
            return AssemblyStatus.Failed("Duplicate thumbnail chunk ${chunk.currIndex} contained different bytes")
        }
        if (existing == null) {
            totalAccumulatedBytes += chunk.data.size
            if (totalAccumulatedBytes > maxBytes) {
                return AssemblyStatus.Failed(
                    "Thumbnail exceeded maximum size limit of $maxBytes bytes (current: $totalAccumulatedBytes)",
                )
            }
            chunks[chunk.currIndex] = chunk.data
        }

        if (chunks.size == totalExpected) {
            if (totalAccumulatedBytes < minBytes) {
                return AssemblyStatus.Failed(
                    "Assembled thumbnail too small: $totalAccumulatedBytes bytes (min required: $minBytes)",
                )
            }
            val stream = ByteArrayOutputStream(totalAccumulatedBytes.toInt())
            for (i in 0 until totalExpected) {
                val piece = chunks[i] ?: return AssemblyStatus.Failed("Missing chunk at index $i")
                stream.write(piece)
            }
            return AssemblyStatus.Completed(stream.toByteArray())
        }

        // Find the next needed index
        var nextNeeded = 0
        while (chunks.containsKey(nextNeeded) && nextNeeded < totalExpected) {
            nextNeeded++
        }

        return AssemblyStatus.InProgress(
            receivedChunks = chunks.size,
            totalChunks = totalExpected,
            nextNeededIndex = nextNeeded,
        )
    }

    val isComplete: Boolean
        get() = totalExpected > 0 && chunks.size == totalExpected && totalAccumulatedBytes >= minBytes

    fun getNextNeededIndex(): Int {
        if (totalExpected <= 0) return 0
        var i = 0
        while (i < totalExpected) {
            if (!chunks.containsKey(i)) return i
            i++
        }
        return -1
    }
}
