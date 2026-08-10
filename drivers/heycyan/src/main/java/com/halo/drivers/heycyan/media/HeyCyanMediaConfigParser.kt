package com.halo.drivers.heycyan.media

/**
 * Parser for the HeyCyan media manifest returned by `http://<IP>/files/media.config`.
 *
 * The device returns one of three formats, as established from CyanBridge
 * (`AlbumDownloader.fetchConfig()`) and the iOS SDK (`GlassesWiFiHandler.m`):
 *
 *   1. JSON object:  {"files": ["img001.jpg", "vid001.mp4", ...]}
 *   2. JSON array:   ["img001.jpg", "vid001.mp4", ...]
 *   3. Line-based:   one filename per line; lines starting with # are comments
 *
 * The JSON field name is `"files"` — source-established from CyanBridge.
 * Do not add or rename fields without a new real-device capture.
 *
 * The source does not include per-file metadata (size, date, dimensions) in the manifest
 * payload — only filenames. Do not invent metadata fields.
 */
interface HeyCyanMediaConfigParser {
    fun parse(payload: String): Result<HeyCyanMediaManifest>
}

/**
 * Default implementation that handles all three device response formats.
 *
 * The parser is deliberately dependency-free (no OkHttp/Moshi/Gson) to keep
 * it testable without Android context. It uses only stdlib string operations,
 * matching how the existing [com.halo.drivers.heycyan.wifi.HeyCyanMediaManifestClient]
 * was implemented.
 *
 * Source evidence:
 *   - CyanBridge AlbumDownloader.kt: parses both JSON and line-based formats
 *   - iOS GlassesWiFiHandler.m: parses as JSON `{"files": [...]}`
 */
class DefaultHeyCyanMediaConfigParser : HeyCyanMediaConfigParser {

    override fun parse(payload: String): Result<HeyCyanMediaManifest> = runCatching {
        val trimmed = payload.trim()
        val filenames = when {
            // JSON object: {"files": ["img001.jpg", ...]}
            trimmed.startsWith("{") -> parseJsonObject(trimmed)
            // JSON array: ["img001.jpg", ...]
            trimmed.startsWith("[") -> parseJsonArray(trimmed)
            // Line-based: one filename per line
            else -> parseLineBased(trimmed)
        }

        val items = filenames.map { filename ->
            val type = MediaType.fromExtension(filename)
            MediaItem(
                filename = filename,
                type = type,
            ).toHeyCyanMedia()
        }

        HeyCyanMediaManifest(
            deviceId = null,   // not provided by device HTTP server
            generatedAt = System.currentTimeMillis(),
            items = items,
        )
    }

    // -------------------------------------------------------------------------
    // Private parsing — source field name: "files"
    // -------------------------------------------------------------------------

    /**
     * Extracts the `"files"` array from a JSON object.
     * The key name `"files"` is source-established from CyanBridge.
     */
    private fun parseJsonObject(json: String): List<String> {
        val filesStart = json.indexOf("[")
        val filesEnd = json.lastIndexOf("]")
        if (filesStart < 0 || filesEnd < 0 || filesEnd <= filesStart) {
            return emptyList()
        }
        return parseJsonArray(json.substring(filesStart, filesEnd + 1))
    }

    /**
     * Extracts quoted string values from a JSON array.
     */
    private fun parseJsonArray(json: String): List<String> {
        val pattern = Regex(""""([^"]+)"""")
        return pattern.findAll(json).map { it.groupValues[1] }.toList()
    }

    /**
     * Parses line-based format: one filename per line, # = comment.
     */
    private fun parseLineBased(body: String): List<String> =
        body.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
}
