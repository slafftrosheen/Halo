package com.halo.drivers.heycyan.media

/**
 * Represents a single media file on the HeyCyan device.
 */
data class MediaItem(
    /** Filename as reported by the device manifest. */
    val filename: String,

    /** Inferred or declared media type. */
    val type: MediaType,

    /** File size in bytes (null if not available from manifest). */
    val sizeBytes: Long? = null,

    /** Creation timestamp (null if not available). */
    val createdAt: Long? = null,

    /** Current synchronization state. */
    val syncState: SyncState = SyncState.NOT_SYNCED
)

/**
 * Synchronization state of a media item.
 */
enum class SyncState {
    /** File exists on device but has not been downloaded. */
    NOT_SYNCED,

    /** Download is in progress. */
    DOWNLOADING,

    /** File has been successfully downloaded and validated. */
    SYNCED,

    /** Download failed. */
    FAILED
}

/**
 * Represents a downloaded media file with local path information.
 */
data class MediaReference(
    /** The original media item from the device. */
    val item: MediaItem,

    /** Absolute local file path (null if not yet downloaded). */
    val localPath: String? = null,

    /** Absolute local thumbnail path (null if not generated). */
    val thumbnailPath: String? = null,

    /** When the file was downloaded. */
    val downloadedAt: Long? = null
) {
    /** Whether the file has been downloaded to local storage. */
    val isLocal: Boolean get() = localPath != null
}

/**
 * Progress update during media download.
 */
data class DownloadProgress(
    val item: MediaItem,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val percentage: Float? = totalBytes?.let {
        if (it > 0) (bytesDownloaded.toFloat() / it) * 100f else null
    }
)
