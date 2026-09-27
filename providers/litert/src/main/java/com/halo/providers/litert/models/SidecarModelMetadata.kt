package com.halo.providers.litert.models

import org.json.JSONObject

data class InstalledLiteRtModelMetadata(
    val id: String,
    val displayName: String,
    val sourceUrl: String?,
    val sha256: String,
    val bytes: Long,
    val installedAtMs: Long = System.currentTimeMillis(),
    val contextWindowTokens: Int = 4096,
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
        put("supportsVision", supportsVision)
        put("supportsTools", supportsTools)
    }.toString()

    companion object {
        fun fromJson(jsonStr: String): InstalledLiteRtModelMetadata? = runCatching {
            val json = JSONObject(jsonStr)
            InstalledLiteRtModelMetadata(
                id = json.getString("id"),
                displayName = json.getString("displayName"),
                sourceUrl = json.optString("sourceUrl").ifBlank { null },
                sha256 = json.getString("sha256"),
                bytes = json.getLong("bytes"),
                installedAtMs = json.optLong("installedAtMs", System.currentTimeMillis()),
                contextWindowTokens = json.optInt("contextWindowTokens", 4096),
                supportsVision = json.optBoolean("supportsVision", false),
                supportsTools = json.optBoolean("supportsTools", false)
            )
        }.getOrNull()
    }
}
