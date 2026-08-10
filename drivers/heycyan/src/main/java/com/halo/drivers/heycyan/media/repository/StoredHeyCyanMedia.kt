package com.halo.drivers.heycyan.media.repository

import com.halo.drivers.heycyan.media.HeyCyanMedia

/**
 * High-level media category enum for local storage and UI queries.
 */
enum class HeyCyanMediaType {
    PHOTO,
    VIDEO,
    AUDIO,
    UNKNOWN,
}

/**
 * Extension to convert PR-026 [HeyCyanMedia] domain model into [HeyCyanMediaType].
 */
fun HeyCyanMedia.toMediaType(): HeyCyanMediaType = when (this) {
    is HeyCyanMedia.Photo -> HeyCyanMediaType.PHOTO
    is HeyCyanMedia.Video -> HeyCyanMediaType.VIDEO
    is HeyCyanMedia.Audio -> HeyCyanMediaType.AUDIO
    is HeyCyanMedia.Unknown -> HeyCyanMediaType.UNKNOWN
}

/**
 * Canonical local media record stored in [com.halo.drivers.heycyan.media.repository.HeyCyanMediaRepository].
 *
 * Distinct from remote representation ([HeyCyanMedia]) — tracks local persistence metadata,
 * storage path, download timestamp, and integrity SHA-256 hash.
 */
data class StoredHeyCyanMedia(
    val identity: String,
    val deviceId: String?,
    val remoteId: String?,
    val filename: String,
    val mediaType: HeyCyanMediaType,
    val localPath: String,
    val sizeBytes: Long,
    val createdAtMillis: Long?,
    val downloadedAtMillis: Long,
    val sha256: String?,
)
