package com.halo.drivers.heycyan.media

/**
 * The manifest of all media files available on the HeyCyan device.
 *
 * Produced by [HeyCyanMediaConfigParser] from the HTTP response at
 * `http://<IP>/files/media.config`.
 *
 * Intentionally independent of HTTP — the manifest can be constructed from
 * any source (HTTP, file, test fixture) and consumed identically by the UI
 * and runtime.
 *
 * Source API format (two variants established from CyanBridge/iOS SDK):
 *
 *   JSON object:  {"files": ["img001.jpg", "vid001.mp4", ...]}
 *   JSON array:   ["img001.jpg", "vid001.mp4", ...]
 *   Line-based:   one filename per line, # for comments
 *
 * The parser handles all three. Field names are source-established — do not change.
 */
data class HeyCyanMediaManifest(
    /**
     * Device identifier if one is available from the manifest or connection context.
     * Null if not provided by the device HTTP server.
     */
    val deviceId: String?,

    /**
     * Epoch millis when the manifest was fetched.
     * Null if not provided by the device HTTP server.
     */
    val generatedAt: Long?,

    /**
     * All media items reported by the device manifest.
     * Unknown extensions are preserved as [HeyCyanMedia.Unknown].
     */
    val items: List<HeyCyanMedia>,
)
