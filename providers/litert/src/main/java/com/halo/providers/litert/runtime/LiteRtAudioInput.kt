package com.halo.providers.litert.runtime

import android.media.MediaDataSource
import android.media.MediaMetadataRetriever

/** Shared Android preflight for encoded audio. Call off the main thread; retains no file or sample. */
object LiteRtAudioInput {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_DURATION_MS = 30_000L

    fun validateEncoded(bytes: ByteArray) {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Choose an audio file smaller than 20 MiB" }
        val source = object : MediaDataSource() {
            override fun getSize(): Long = bytes.size.toLong()
            override fun close() = Unit
            override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                if (size == 0) return 0
                if (position < 0 || position >= bytes.size) return -1
                val start = position.toInt()
                val count = minOf(size, bytes.size - start)
                bytes.copyInto(buffer, offset, start, start + count)
                return count
            }
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(source)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            validateDuration(duration)
        } catch (error: RuntimeException) {
            throw IllegalArgumentException("Choose a decodable audio clip up to 30 seconds long", error)
        } finally {
            try { retriever.release() } finally { source.close() }
        }
    }
    internal fun validateDuration(durationMs: Long?) {
        require(durationMs != null && durationMs in 1..MAX_DURATION_MS) { "Audio must be between 1 ms and 30 seconds" }
    }
}
