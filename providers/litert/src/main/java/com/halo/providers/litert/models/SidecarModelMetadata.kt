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
    val supportsText: Boolean = true,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    val family: String? = null,
    val quantizationLabel: String? = null,
    val access: LiteRtCatalogAccess? = null,
    val maturity: LiteRtModelMaturity? = null,
    val tier: LiteRtModelTier? = null,
    val minDeviceRamGb: Int? = null,
    val acquisitionOwner: String? = null,
) {
    fun toJson(): String = JSONObject().apply {
        put("acquisitionOwner", acquisitionOwner)
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
        put("supportsText", supportsText)
        put("supportsAudio", supportsAudio)
        put("supportsThinking", supportsThinking)
        put("family", family)
        put("quantizationLabel", quantizationLabel)
        put("access", access?.name)
        put("maturity", maturity?.name)
        put("tier", tier?.name)
        put("minDeviceRamGb", minDeviceRamGb)
    }.toString()

    companion object {
        /** Sentinel for "Halo does not know this model's context window". */
        const val UNKNOWN_CONTEXT_TOKENS = 0

        fun fromJson(jsonStr: String): InstalledLiteRtModelMetadata? = runCatching {
            val json = JSONObject(jsonStr)
            val context = json.optInt("contextWindowTokens", UNKNOWN_CONTEXT_TOKENS)
            InstalledLiteRtModelMetadata(
                acquisitionOwner = json.optString("acquisitionOwner").ifBlank { null }?.also(::requireModelAcquisitionOwner),
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
                supportsText = json.optBoolean("supportsText", true),
                supportsAudio = json.optBoolean("supportsAudio", false),
                supportsThinking = json.optBoolean("supportsThinking", false),
                family = json.optString("family").ifBlank { null },
                quantizationLabel = json.optString("quantizationLabel").ifBlank { null },
                access = json.optString("access").ifBlank { null }?.let(::parseAccess),
                maturity = json.optString("maturity").ifBlank { null }?.let(::parseMaturity),
                tier = json.optString("tier").ifBlank { null }?.let(::parseTier),
                minDeviceRamGb = json.optInt("minDeviceRamGb", -1).takeIf { it > 0 },
            )
        }.getOrNull()

        internal fun fromCatalog(model: LiteRtCatalogModel, sourceUrl: String?): InstalledLiteRtModelMetadata =
            InstalledLiteRtModelMetadata(
                id = model.id,
                displayName = model.displayName,
                sourceUrl = sourceUrl,
                sha256 = model.sha256,
                bytes = model.expectedBytes,
                contextWindowTokens = model.contextWindowTokens,
                contextKnown = !model.runtimeProfile.useModelDefaultContext,
                supportsVision = model.supportsVision,
                supportsTools = model.supportsTools,
                supportsText = model.supportsText,
                supportsAudio = model.supportsAudio,
                supportsThinking = model.supportsThinking,
                family = model.family,
                quantizationLabel = model.quantizationLabel,
                access = model.access,
                maturity = model.maturity,
                tier = model.tier,
                minDeviceRamGb = model.minDeviceRamGb,
            )

        private fun parseAccess(value: String): LiteRtCatalogAccess? =
            LiteRtCatalogAccess.entries.firstOrNull { it.name == value }

        private fun parseMaturity(value: String): LiteRtModelMaturity? =
            LiteRtModelMaturity.entries.firstOrNull { it.name == value }

        private fun parseTier(value: String): LiteRtModelTier? =
            LiteRtModelTier.entries.firstOrNull { it.name == value }
    }
}
