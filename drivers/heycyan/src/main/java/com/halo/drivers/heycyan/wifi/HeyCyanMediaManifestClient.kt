package com.halo.drivers.heycyan.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client for fetching the media manifest from the HeyCyan glasses.
 *
 * The glasses expose a manifest at `http://<IP>/files/media.config`
 * containing the list of available media files.
 *
 * Evidence:
 * - CyanBridge `AlbumDownloader.fetchConfig()` — parses as line-based or JSON
 * - iOS `GlassesWiFiHandler.m` `fetchMediaManifest` — parses as JSON
 *
 * This client supports both formats:
 * 1. Line-based: one filename per line
 * 2. JSON: `{"files": ["file1.jpg", "file2.mp4", ...]}`
 */
class HeyCyanMediaManifestClient(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT
) {
    companion object {
        private const val DEFAULT_TIMEOUT = 10_000L
        private const val MANIFEST_PATH = "/files/media.config"
    }

    /**
     * Fetches the media manifest from the device.
     *
     * @param deviceIp The discovered device IP address
     * @return List of filenames available for download
     */
    suspend fun fetchManifest(deviceIp: String): List<String> {
        return withContext(Dispatchers.IO) {
            val url = URL("http://$deviceIp$MANIFEST_PATH")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = timeoutMillis.toInt()
            connection.readTimeout = timeoutMillis.toInt()
            connection.requestMethod = "GET"

            try {
                val responseCode = connection.responseCode
                check(responseCode in 200..299) {
                    "Manifest request failed with HTTP $responseCode"
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseManifest(body)
            } finally {
                connection.disconnect()
            }
        }
    }

    /**
     * Parses the manifest body, supporting both JSON and line-based formats.
     */
    internal fun parseManifest(body: String): List<String> {
        val trimmed = body.trim()

        // Try JSON format first: {"files": ["file1.jpg", ...]}
        if (trimmed.startsWith("{")) {
            return parseJsonManifest(trimmed)
        }

        // Try JSON array: ["file1.jpg", ...]
        if (trimmed.startsWith("[")) {
            return parseJsonArray(trimmed)
        }

        // Fall back to line-based: one filename per line
        return parseLineBasedManifest(trimmed)
    }

    private fun parseJsonManifest(json: String): List<String> {
        // Simple manual JSON parsing to avoid OkHttp/Moshi dependency for this small operation
        // Look for "files" array
        val filesStart = json.indexOf("[")
        val filesEnd = json.lastIndexOf("]")
        if (filesStart < 0 || filesEnd < 0 || filesEnd <= filesStart) {
            return emptyList()
        }
        return parseJsonArray(json.substring(filesStart, filesEnd + 1))
    }

    private fun parseJsonArray(json: String): List<String> {
        // Extract quoted strings from JSON array
        val pattern = Regex("\"([^\"]+)\"")
        return pattern.findAll(json).map { it.groupValues[1] }.toList()
    }

    private fun parseLineBasedManifest(body: String): List<String> {
        return body.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { !it.startsWith("#") } // Skip comments
    }
}
