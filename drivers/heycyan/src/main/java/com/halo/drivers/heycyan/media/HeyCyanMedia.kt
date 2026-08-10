package com.halo.drivers.heycyan.media

/**
 * Domain model for a single media file on the HeyCyan device.
 *
 * The manifest at `http://<IP>/files/media.config` returns a list of filenames.
 * The type of each file is inferred from the extension, consistent with the CyanBridge
 * SDK evidence: jpg/jpeg/png/heic→Photo, mov/mp4/m4v→Video, opus→Audio.
 *
 * This sealed interface is the PR-026 semantic model used by the capability layer,
 * manifest, downloader, and repository. It wraps [MediaItem] which is the existing
 * sync-engine model.
 *
 * Evidence: CyanBridge `AlbumDownloader.kt`, iOS `GlassesMediaDownloader.m`
 */
sealed interface HeyCyanMedia {

    val id: String
    val filename: String
    val sizeBytes: Long?
    val createdAt: Long?

    data class Photo(
        override val id: String,
        override val filename: String,
        override val sizeBytes: Long?,
        override val createdAt: Long?,
        val width: Int?,
        val height: Int?,
    ) : HeyCyanMedia

    data class Video(
        override val id: String,
        override val filename: String,
        override val sizeBytes: Long?,
        override val createdAt: Long?,
        val durationMs: Long?,
    ) : HeyCyanMedia

    data class Audio(
        override val id: String,
        override val filename: String,
        override val sizeBytes: Long?,
        override val createdAt: Long?,
        val durationMs: Long?,
    ) : HeyCyanMedia

    /**
     * Preserves unrecognised extensions rather than dropping them.
     * Any unknown file from the manifest is wrapped here.
     */
    data class Unknown(
        override val id: String,
        override val filename: String,
        override val sizeBytes: Long?,
        override val createdAt: Long?,
        val mimeType: String?,
    ) : HeyCyanMedia
}

/**
 * Converts a [MediaItem] from the existing sync engine into the PR-026 [HeyCyanMedia]
 * domain model. Type is determined by [MediaItem.type] (which is source-backed from
 * extension detection), NOT assumed from the extension alone.
 */
fun MediaItem.toHeyCyanMedia(): HeyCyanMedia {
    val id = filename // stable ID from manifest; fallback to filename for now
    return when (type) {
        MediaType.PHOTO -> HeyCyanMedia.Photo(
            id = id,
            filename = filename,
            sizeBytes = sizeBytes,
            createdAt = createdAt,
            width = null,
            height = null,
        )
        MediaType.VIDEO -> HeyCyanMedia.Video(
            id = id,
            filename = filename,
            sizeBytes = sizeBytes,
            createdAt = createdAt,
            durationMs = null,
        )
        MediaType.AUDIO -> HeyCyanMedia.Audio(
            id = id,
            filename = filename,
            sizeBytes = sizeBytes,
            createdAt = createdAt,
            durationMs = null,
        )
        MediaType.UNKNOWN -> HeyCyanMedia.Unknown(
            id = id,
            filename = filename,
            sizeBytes = sizeBytes,
            createdAt = createdAt,
            mimeType = null,
        )
    }
}
