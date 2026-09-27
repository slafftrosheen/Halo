package com.halo.providers.litert.models

/**
 * A built-in, pinnable LiteRT-LM artifact.
 *
 * The catalog deliberately stores *source identity* (repository + immutable revision +
 * filename) instead of a mutable `downloadUrl`. Hugging Face URLs that point at `main`,
 * `latest`, or any other moving ref cannot be integrity-checked, so they are impossible to
 * pin. The download URL is derived and therefore always carries the pinned revision.
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
    }

    /** Immutable, fully pinned download URL derived from [repoId] + [revision] + [filename]. */
    val downloadUrl: String
        get() = "https://huggingface.co/$repoId/resolve/$revision/$filename?download=true"

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

/**
 * The only zero-configuration downloadable model Halo ships right now.
 *
 * Gemma is intentionally absent: the upstream repository is license-gated and Halo has no
 * Hugging Face authentication or gated-license acceptance flow, so it is not a valid
 * built-in download. See [LiteRtModelCatalog.WITHHELD_GATED_REPO_IDS].
 */
object LiteRtModelCatalog {

    const val QWEN3_0_6B_DYNAMIC_INT4_ID = "qwen3-0.6b-dynamic-int4"

    /**
     * Qwen 3 0.6B Dynamic INT4 — official LiteRT Community Qwen repository.
     * Revision and SHA are pinned; the artifact was published for LiteRT-LM 0.17.1.
     */
    val QWEN3_0_6B_DYNAMIC_INT4: LiteRtCatalogModel = LiteRtCatalogModel(
        id = QWEN3_0_6B_DYNAMIC_INT4_ID,
        displayName = "Qwen 3 0.6B Dynamic INT4",
        repoId = "litert-community/Qwen3-0.6B",
        revision = "a3c5d805ae362dff7f580bc25f2dfb9a5a7eaa76",
        filename = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        expectedBytes = 344_671_744L,
        sha256 = "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856",
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
    )

    val BUILT_IN_MODELS: List<LiteRtCatalogModel> = listOf(QWEN3_0_6B_DYNAMIC_INT4)

    /**
     * Upstream repositories that require a gated license acceptance flow. Exposed as documentation
     * only — never as a downloadable entry and never through a third-party mirror.
     */
    val WITHHELD_GATED_REPO_IDS: Set<String> = setOf("litert-community/Gemma3-1B-IT")

    fun byId(modelId: String): LiteRtCatalogModel? =
        BUILT_IN_MODELS.firstOrNull { it.id == modelId }

    fun bySha256(sha256: String?): LiteRtCatalogModel? {
        val normalized = sha256?.trim()?.lowercase() ?: return null
        if (normalized.isEmpty()) return null
        return BUILT_IN_MODELS.firstOrNull { it.sha256 == normalized }
    }

    fun byFilename(filename: String): LiteRtCatalogModel? =
        BUILT_IN_MODELS.firstOrNull { it.filename.equals(filename, ignoreCase = true) }
}
