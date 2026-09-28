package com.halo.providers.litert.models

import com.halo.sdk.provider.LocalComputeBackend

/**
 * How Halo may obtain an artifact.
 *
 * Only [OPEN_DIRECT] artifacts may be fetched by the built-in downloader. A gated repository must
 * never be mirrored: the license gate belongs to the upstream publisher, so Halo can only accept a
 * manually imported, already-licensed file.
 */
enum class LiteRtCatalogAccess {
    /** Public, immutable, directly downloadable from the pinned repository revision. */
    OPEN_DIRECT,

    /** Upstream license gate (for example Gemma). Import a licensed file from device only. */
    GATED_IMPORT_ONLY,

    /** Not published as a stable artifact; the operator supplies the file. */
    MANUAL_IMPORT_ONLY,
}

/** Rough device-weight class. Used for UI grouping and preload decisions, never for routing yet. */
enum class LiteRtModelTier {
    SMALL,
    MEDIUM,
    LARGE,
}

/**
 * How much evidence Halo has for an artifact.
 *
 * [DEVELOPMENT] is the honest default for anything Halo has only catalogued. A model is never
 * promoted to [VERIFIED] merely because it can be downloaded or initialized.
 */
enum class LiteRtModelMaturity {
    DEVELOPMENT,
    EXPERIMENTAL,
    VERIFIED,
}

/**
 * Model-scoped runtime parameters.
 *
 * Halo must not assume every LiteRT artifact wants identical engine/sampler settings: upstream
 * device measurements show the same quantization behaving very differently per backend. Resolution
 * order is explicit request override -> user model preference -> this profile -> Halo safe default.
 */
data class LiteRtRuntimeProfile(
    val preferredBackend: LocalComputeBackend = LocalComputeBackend.AUTO,
    val allowCpuFallback: Boolean = true,
    /** Real context window in tokens, or null when only the artifact/engine default is trustworthy. */
    val knownContextTokens: Int? = null,
    /** True when the runtime must ask LiteRT-LM for the model's own default context. */
    val useModelDefaultContext: Boolean = false,
    val defaultMaxOutputTokens: Int = 512,
    val defaultTopK: Int? = null,
    val defaultTopP: Double? = null,
    val defaultTemperature: Double? = null,
    /**
     * Bounded allocation ladder tried only for memory/resource allocation failures under an AUTO
     * policy. Never used to mask corrupt artifacts, unsupported ops or ABI errors.
     */
    val contextFallbackLadder: List<Int> = DEFAULT_CONTEXT_LADDER,
) {
    init {
        require(defaultMaxOutputTokens > 0) { "defaultMaxOutputTokens must be positive" }
        require(defaultTopK == null || defaultTopK > 0) { "defaultTopK must be positive" }
        require(defaultTopP == null || defaultTopP in 0.0..1.0) { "defaultTopP must be in [0, 1]" }
        require(defaultTemperature == null || defaultTemperature >= 0.0) { "defaultTemperature must be >= 0" }
        require(contextFallbackLadder.isNotEmpty()) { "contextFallbackLadder must not be empty" }
        require(contextFallbackLadder.all { it > 0 }) { "contextFallbackLadder entries must be positive" }
    }

    companion object {
        /** Safe default ladder for the 4096-token text artifacts Halo currently ships. */
        val DEFAULT_CONTEXT_LADDER = listOf(4096, 2048, 1024)
    }
}

/**
 * A built-in, pinnable LiteRT-LM artifact.
 *
 * The catalog deliberately stores *source identity* (repository + immutable revision +
 * filename) instead of a mutable `downloadUrl`. Hugging Face URLs that point at `main`,
 * `latest`, or any other moving ref cannot be integrity-checked, so they are impossible to
 * pin. The download URL is derived and therefore always carries the pinned revision.
 *
 * Capability flags describe *this artifact as Halo can prove it*, not what the base model family
 * upstream claims. Unproven support must be false or explicitly experimental.
 */
data class LiteRtCatalogModel(
    val id: String,
    val displayName: String,
    val repoId: String,
    val revision: String,
    val filename: String,
    val expectedBytes: Long,
    val sha256: String,
    val contextWindowTokens: Int,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val minLiteRtLmVersion: String,
    // ---- development catalog matrix metadata -------------------------------------------------
    val family: String = "Unknown",
    val quantizationLabel: String = "unknown",
    val access: LiteRtCatalogAccess = LiteRtCatalogAccess.OPEN_DIRECT,
    val maturity: LiteRtModelMaturity = LiteRtModelMaturity.DEVELOPMENT,
    val tier: LiteRtModelTier = LiteRtModelTier.SMALL,
    val supportsText: Boolean = true,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    /** Lower bound for a comfortable load, or null when Halo has no measurement yet. */
    val minDeviceRamGb: Int? = null,
    val runtimeProfile: LiteRtRuntimeProfile = LiteRtRuntimeProfile(),
) {
    init {
        require(id.isNotBlank()) { "Catalog model id must not be blank" }
        require(revision.matches(REVISION_PATTERN)) {
            "Catalog model '$id' must pin an immutable 40-character commit revision, got '$revision'"
        }
        require(sha256.matches(SHA256_PATTERN)) {
            "Catalog model '$id' must declare a lowercase 64-character SHA-256, got '$sha256'"
        }
        require(sha256 !in REJECTED_SHA256) {
            "Catalog model '$id' declares a known placeholder SHA-256 ($sha256)"
        }
        require(expectedBytes > MINIMUM_PLAUSIBLE_BYTES) {
            "Catalog model '$id' declares an implausible size ($expectedBytes bytes); " +
                "rounded or guessed sizes are not accepted"
        }
        require(filename.endsWith(".litertlm", ignoreCase = true)) {
            "Catalog model '$id' must reference a .litertlm artifact, got '$filename'"
        }
        require(!filename.contains('/')) { "Catalog model '$id' filename must not contain a path separator" }
        require(repoId.isNotBlank()) { "Catalog model '$id' must name a repository" }
        require(contextWindowTokens > 0) { "Catalog model '$id' must declare a positive context window" }
        require(!(access == LiteRtCatalogAccess.GATED_IMPORT_ONLY && minDeviceRamGb != null && minDeviceRamGb <= 0)) {
            "Catalog model '$id' declares an implausible RAM floor"
        }
    }

    /** Immutable, fully pinned download URL derived from [repoId] + [revision] + [filename]. */
    val downloadUrl: String
        get() = "https://huggingface.co/$repoId/resolve/$revision/$filename?download=true"

    /**
     * True only when the built-in downloader may fetch this artifact. Gated and manual artifacts
     * must be imported from device so the upstream license gate is never bypassed.
     */
    val directDownloadAllowed: Boolean
        get() = access == LiteRtCatalogAccess.OPEN_DIRECT

    val requiresLicenseAcceptance: Boolean
        get() = access == LiteRtCatalogAccess.GATED_IMPORT_ONLY

    /** User-facing acquisition guidance for anything the downloader must not fetch. */
    val importGuidance: String?
        get() = when (access) {
            LiteRtCatalogAccess.OPEN_DIRECT -> null
            LiteRtCatalogAccess.GATED_IMPORT_ONLY ->
                "Requires license acceptance on the upstream repository. " +
                    "Import a licensed .litertlm artifact from this device."
            LiteRtCatalogAccess.MANUAL_IMPORT_ONLY ->
                "This artifact is not published as a stable download. Import a .litertlm file from this device."
        }

    /** Effective context Halo will request, when the profile declares one. */
    val resolvedContextTokens: Int?
        get() = if (runtimeProfile.useModelDefaultContext) null
        else runtimeProfile.knownContextTokens ?: contextWindowTokens

    val sizeLabelMb: Long
        get() = (expectedBytes + 999_999L) / 1_000_000L

    companion object {
        internal val REVISION_PATTERN = Regex("[0-9a-f]{40}")
        internal val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        internal const val MINIMUM_PLAUSIBLE_BYTES = 1_048_576L

        /** SHA-256 of the empty string and of the literal "test" — previously shipped as fake pins. */
        internal val REJECTED_SHA256 = setOf(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
        )
    }
}
