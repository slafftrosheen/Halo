package com.halo.drivers.heycyan.media

/**
 * HeyCyan media types as documented in the SDK and CyanBridge.
 *
 * Evidence: SDK README, CyanBridge `AlbumDownloader.kt`, `GlassesMediaDownloader.m`
 *
 * | Value | Type  | Extensions          |
 * |-------|-------|---------------------|
 * | 1     | Photo | .jpg, .png, .heic   |
 * | 2     | Video | .mov, .mp4, .m4v    |
 * | 3     | Audio | .opus               |
 */
enum class MediaType(val value: Int) {
    PHOTO(1),
    VIDEO(2),
    AUDIO(3),
    UNKNOWN(-1);

    companion object {
        fun fromValue(value: Int): MediaType =
            entries.firstOrNull { it.value == value } ?: UNKNOWN

        /**
         * Infers media type from file extension.
         */
        fun fromExtension(filename: String): MediaType {
            val ext = filename.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "heic" -> PHOTO
                "mov", "mp4", "m4v" -> VIDEO
                "opus", "wav", "aac", "m4a" -> AUDIO
                else -> UNKNOWN
            }
        }
    }
}
