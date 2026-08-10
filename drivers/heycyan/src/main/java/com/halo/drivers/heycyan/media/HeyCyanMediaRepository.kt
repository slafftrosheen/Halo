package com.halo.drivers.heycyan.media

import java.io.InputStream

/**
 * Repository abstraction for HeyCyan media files.
 *
 * Platform-neutral — the Android host maps this to app-private storage or MediaStore.
 * The runtime never sees file paths, URIs, or Context.
 *
 * Deduplication is by stable media ID (see [MediaIdentityResolver]).
 */
interface HeyCyanMediaRepository {

    /** Returns all known media items in the local repository. */
    suspend fun list(): List<HeyCyanMedia>

    /**
     * Saves a media file from [source] to the repository.
     *
     * @return [MediaLocation] on success, or failure if storage is unavailable.
     */
    suspend fun save(
        media: HeyCyanMedia,
        source: InputStream,
    ): Result<MediaLocation>

    /** Returns true if the media item already exists in the repository. */
    suspend fun exists(media: HeyCyanMedia): Boolean
}

/**
 * Stable identity resolver for deduplication.
 *
 * Primary: stable media ID from the manifest (the filename as returned by the device).
 * Fallback: filename + size + timestamp composite key (when metadata is available).
 *
 * Do NOT use filename alone — the device reuses filenames across sessions.
 */
interface MediaIdentityResolver {
    fun identity(media: HeyCyanMedia): String
}

/**
 * Default identity resolver.
 *
 * Uses the manifest-assigned ID as primary key, then falls back to
 * filename+sizeBytes+createdAt composite if they are present.
 */
class DefaultMediaIdentityResolver : MediaIdentityResolver {

    override fun identity(media: HeyCyanMedia): String {
        // The id field from the manifest is the filename — stable within a session.
        // If sizeBytes and createdAt are available, incorporate them to detect
        // filename collisions across sessions.
        val base = media.id
        val size = media.sizeBytes
        val ts = media.createdAt

        return if (size != null && ts != null) {
            "$base:$size:$ts"
        } else {
            base
        }
    }
}
