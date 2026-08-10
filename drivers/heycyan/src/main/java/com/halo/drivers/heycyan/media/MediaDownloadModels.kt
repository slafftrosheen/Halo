package com.halo.drivers.heycyan.media

/**
 * Download progress for a single media file.
 *
 * Streaming-safe: [totalBytes] may be null if Content-Length is absent.
 */
data class MediaDownloadProgress(
    val bytesReceived: Long,
    val totalBytes: Long?,
) {
    /**
     * Fractional progress in [0.0, 1.0], or null if total size is unknown.
     */
    val fraction: Float?
        get() = totalBytes
            ?.takeIf { it > 0 }
            ?.let { bytesReceived.toFloat() / it }
}

/**
 * The opaque location of a successfully downloaded media file.
 * Platform-specific (e.g., file path, MediaStore URI) — callers must
 * not cast this without knowing the implementation.
 */
data class MediaLocation(
    val path: String,
)

/**
 * Integrity metadata accumulated while streaming a download.
 *
 * SHA-256 is optional — calculated if the architecture makes it cheap
 * but not mandatory if the source API provides no expected hash for comparison.
 */
data class MediaIntegrity(
    val expectedBytes: Long?,
    val actualBytes: Long,
    val sha256: String?,
)

/**
 * Result of a completed media file download.
 */
data class MediaDownloadResult(
    val media: HeyCyanMedia,
    val bytesWritten: Long,
    val destination: MediaLocation,
    val integrity: MediaIntegrity,
)
