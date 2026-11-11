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

        require(filenames.size <= 5000 && filenames.distinct().size == filenames.size) { "Invalid device file list" }
        filenames.forEach { name ->
            require(name.length in 1..255 && name.none(Char::isISOControl) && name != "." && name != ".." && '/' !in name && '\\' !in name)
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

    // Only the documented JSON shape is accepted. Error objects, nested structures,
    // malformed escapes and trailing data cannot become successful empty catalogs.
    private fun parseJsonObject(json: String): List<String> = JsonFiles(json).parse(objectForm = true)
    private fun parseJsonArray(json: String): List<String> = JsonFiles(json).parse(objectForm = false)

    private class JsonFiles(private val text: String) {
        private var offset = 0
        private fun whitespace() { while (offset < text.length && text[offset] in " \t\r\n") offset++ }
        private fun expect(char: Char) { whitespace(); require(offset < text.length && text[offset++] == char) { "Invalid media manifest JSON" } }
        private fun take(char: Char): Boolean { whitespace(); return if (offset < text.length && text[offset] == char) { offset++; true } else false }
        private fun string(): String {
            expect('"')
            val result = StringBuilder()
            while (offset < text.length) {
                val char = text[offset++]
                if (char == '"') return result.toString()
                require(char >= ' ') { "Invalid JSON control character" }
                if (char != '\\') result.append(char) else {
                    require(offset < text.length)
                    when (val escape = text[offset++]) {
                        '"', '\\', '/' -> result.append(escape)
                        'b' -> result.append('\b')
                        'f' -> result.append('\u000c')
                        'n' -> result.append('\n')
                        'r' -> result.append('\r')
                        't' -> result.append('\t')
                        'u' -> { require(offset + 4 <= text.length)
                            result.append(text.substring(offset, offset + 4).toInt(16).toChar()); offset += 4 }
                        else -> error("Invalid JSON escape")
                    }
                }
                require(result.length <= 255) { "Device filename too long" }
            }
            error("Unterminated JSON string")
        }
        fun parse(objectForm: Boolean): List<String> {
            if (objectForm) { expect('{'); require(string() == "files"); expect(':') }
            expect('[')
            val names = mutableListOf<String>()
            if (!take(']')) {
                do { names += string(); require(names.size <= 5000) } while (take(','))
                expect(']')
            }
            if (objectForm) expect('}')
            whitespace(); require(offset == text.length)
            return names
        }
    }

    private fun parseLineBased(body: String): List<String> {
        require(!body.startsWith("<")) { "Device returned a document instead of a file list" }
        return body.lineSequence().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }.take(5001).toList()
    }
}
