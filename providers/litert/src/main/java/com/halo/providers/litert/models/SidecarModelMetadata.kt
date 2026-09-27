package com.halo.providers.litert.models

import org.json.JSONObject

/**
 * Sidecar metadata written next to an installed `.litertlm` artifact.
 *
 * The sidecar is *descriptive*, never authoritative: inventory always re-derives the physical
 * byte count and SHA-256 from the file and only uses the sidecar to detect tampering.
 */
data class InstalledLiteRtModelMetadata(
    val id: String,
    val displayName: String,
    val sourceUrl: String?,
    val sha256: String,
    val bytes: Long,
    val installedAtMs: Long = System.currentTimeMillis(),
    val contextWindowTokens: Int = UNKNOWN_CONTEXT_TOKENS,
    /** False for arbitrary imports whose real context window is unknown to Halo. */
    val contextKnown: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
) {
    fun toJson(): String = JSONObject().apply {
        put("id", id)
        put("displayName", displayName)
        put("sourceUrl", sourceUrl)
        put("sha256", sha256)
        put("bytes", bytes)
        put("installedAtMs", installedAtMs)
        put("contextWindowTokens", contextWindowTokens)
        put("contextKnown", contextKnown)
        put("supportsVision", supportsVision)
        put("supportsTools", supportsTools)
    }.toString()

    companion object {
        /** Sentinel for "Halo does not know this model's context window". */
        const val UNKNOWN_CONTEXT_TOKENS = 0

        fun fromJson(jsonStr: String): InstalledLiteRtModelMetadata? = runCatching {
            val json = JSONObject(jsonStr)
            val context = json.optInt("contextWindowTokens", UNKNOWN_CONTEXT_TOKENS)
            InstalledLiteRtModelMetadata(
                id = json.getString("id"),
                displayName = json.getString("displayName"),
                sourceUrl = json.optString("sourceUrl").ifBlank { null },
                sha256 = json.getString("sha256"),
                bytes = json.getLong("bytes"),
                installedAtMs = json.optLong("installedAtMs", System.currentTimeMillis()),
                contextWindowTokens = context,
                contextKnown = json.optBoolean("contextKnown", context > 0),
                supportsVision = json.optBoolean("supportsVision", false),
                supportsTools = json.optBoolean("supportsTools", false),
            )
        }.getOrNull()
    }
}
