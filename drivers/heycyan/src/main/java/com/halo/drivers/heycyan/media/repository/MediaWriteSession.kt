package com.halo.drivers.heycyan.media.repository

import com.halo.drivers.heycyan.media.HeyCyanMedia

/**
 * Abstraction for streaming bytes directly to disk during a download.
 */
interface MediaWriteSession {

    val identity: String

    val media: HeyCyanMedia

    /** Writes bytes from [bytes] buffer. */
    suspend fun write(bytes: ByteArray, offset: Int, length: Int)

    /** Flushes buffered data to underlying storage. */
    suspend fun flush()

    /** Closes the session without committing. */
    suspend fun close()
}

/**
 * Path resolver for local media storage location.
 * Sanitizes input filenames to prevent path traversal (`../`, `..\`, absolute paths).
 */
interface MediaPathResolver {
    fun pathFor(media: HeyCyanMedia, identity: String): String
}

/**
 * Default implementation of [MediaPathResolver].
 */
class DefaultMediaPathResolver(
    private val rootDir: String,
    private val deviceId: String = "default_device",
) : MediaPathResolver {

    override fun pathFor(media: HeyCyanMedia, identity: String): String {
        // Sanitize filename — strip path traversal characters
        val rawName = media.filename.substringAfterLast('/').substringAfterLast('\\')
        val safeName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "media_$identity" }

        val typeFolder = when (media.toMediaType()) {
            HeyCyanMediaType.PHOTO -> "photos"
            HeyCyanMediaType.VIDEO -> "videos"
            HeyCyanMediaType.AUDIO -> "audio"
            HeyCyanMediaType.UNKNOWN -> "unknown"
        }

        return "$rootDir/$deviceId/$typeFolder/$safeName"
    }
}
