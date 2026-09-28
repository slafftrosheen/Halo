package com.halo.providers.litert.models

import com.halo.sdk.provider.LocalComputeBackend

/**
 * The built-in LiteRT-LM development catalog.
 *
 * The single-artifact catalog could not distinguish "the model is broken" from "the LiteRT runtime
 * is broken" from "the GPU driver is broken". This catalog therefore exposes several *real*,
 * byte-pinned text artifacts plus one explicitly gated one, so runtime behaviour can be compared
 * across model families, sizes and quantization paths.
 *
 * Rules:
 *  - Every downloadable entry pins an immutable 40-character revision, an exact byte count and a
 *    64-character SHA-256. No mutable refs, no rounded sizes, no placeholder hashes.
 *  - Capability flags describe the *artifact* as Halo can prove it, never the base model family.
 *  - Gated artifacts (Gemma) are catalogued for identity/import canonicalization but are never
 *    exposed to the built-in downloader and are never mirrored.
 */
object LiteRtModelCatalog {

    // -----------------------------------------------------------------------------------------
    // Control model
    // -----------------------------------------------------------------------------------------

    const val QWEN3_0_6B_DYNAMIC_INT4_ID = "qwen3-0.6b-dynamic-int4"

    /**
     * Qwen 3 0.6B Dynamic INT4 — official LiteRT Community Qwen repository.
     * Revision and SHA are pinned; the artifact was published for LiteRT-LM 0.17.1.
     *
     * Baseline/control artifact: the only model Halo has actually exercised on a phone so far.
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
        family = "Qwen3",
        quantizationLabel = "dynamic_wi4b32_afp32",
        access = LiteRtCatalogAccess.OPEN_DIRECT,
        maturity = LiteRtModelMaturity.DEVELOPMENT,
        tier = LiteRtModelTier.SMALL,
        runtimeProfile = LiteRtRuntimeProfile(
            preferredBackend = LocalComputeBackend.AUTO,
            allowCpuFallback = true,
            knownContextTokens = 4096,
            defaultMaxOutputTokens = 512,
        ),
    )

    // -----------------------------------------------------------------------------------------
    // Development matrix
    // -----------------------------------------------------------------------------------------

    const val QWEN3_1_7B_DYNAMIC_INT4_ID = "qwen3-1.7b-dynamic-int4"

    /**
     * Qwen 3 1.7B Dynamic INT4.
     *
     * Same broad family and quantization recipe as the control model but substantially larger
     * weights, and upstream physical-device measurements show it behaving very differently on GPU
     * and CPU. Preferred backend is GPU with CPU fallback permitted.
     */
    val QWEN3_1_7B_DYNAMIC_INT4: LiteRtCatalogModel = LiteRtCatalogModel(
        id = QWEN3_1_7B_DYNAMIC_INT4_ID,
        displayName = "Qwen 3 1.7B Dynamic INT4",
        repoId = "litert-community/Qwen3-1.7B",
        revision = "73fbc3fe8271c162a603ee66f6e7ed25b6211195",
        filename = "Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm",
        expectedBytes = 977_184_032L,
        sha256 = "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
        family = "Qwen3",
        quantizationLabel = "dynamic_wi4b32_afp32",
        access = LiteRtCatalogAccess.OPEN_DIRECT,
        maturity = LiteRtModelMaturity.DEVELOPMENT,
        tier = LiteRtModelTier.MEDIUM,
        runtimeProfile = LiteRtRuntimeProfile(
            preferredBackend = LocalComputeBackend.GPU,
            allowCpuFallback = true,
            knownContextTokens = 4096,
            defaultMaxOutputTokens = 512,
        ),
    )

    const val QWEN2_5_1_5B_INSTRUCT_Q8_ID = "qwen2.5-1.5b-instruct-q8"

    /**
     * Qwen 2.5 1.5B Instruct Q8 — deliberately a different family and quantization path from the
     * Qwen3 INT4 artifacts. Upstream sampling profile: topK 20, topP 0.8, temperature 0.7.
     */
    val QWEN2_5_1_5B_INSTRUCT_Q8: LiteRtCatalogModel = LiteRtCatalogModel(
        id = QWEN2_5_1_5B_INSTRUCT_Q8_ID,
        displayName = "Qwen 2.5 1.5B Instruct Q8",
        repoId = "litert-community/Qwen2.5-1.5B-Instruct",
        revision = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
        filename = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        expectedBytes = 1_597_931_520L,
        sha256 = "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9",
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
        family = "Qwen2.5",
        quantizationLabel = "multi-prefill-seq_q8_ekv4096",
        access = LiteRtCatalogAccess.OPEN_DIRECT,
        maturity = LiteRtModelMaturity.DEVELOPMENT,
        tier = LiteRtModelTier.MEDIUM,
        runtimeProfile = LiteRtRuntimeProfile(
            preferredBackend = LocalComputeBackend.AUTO,
            allowCpuFallback = true,
            knownContextTokens = 4096,
            defaultMaxOutputTokens = 512,
            defaultTopK = 20,
            defaultTopP = 0.8,
            defaultTemperature = 0.7,
        ),
    )

    const val DEEPSEEK_R1_DISTILL_QWEN_1_5B_Q8_ID = "deepseek-r1-distill-qwen-1.5b-q8"

    /**
     * DeepSeek R1 Distill Qwen 1.5B Q8.
     *
     * Purpose: exercise a reasoning-oriented model and another 1.5B Q8 artifact through exactly the
     * same Halo LiteRT runtime. Marked EXPERIMENTAL because Halo has not yet proven generation.
     */
    val DEEPSEEK_R1_DISTILL_QWEN_1_5B_Q8: LiteRtCatalogModel = LiteRtCatalogModel(
        id = DEEPSEEK_R1_DISTILL_QWEN_1_5B_Q8_ID,
        displayName = "DeepSeek R1 Distill Qwen 1.5B Q8",
        repoId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
        revision = "e34bb88632342d1f9640bad579a45134eb1cf988",
        filename = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        expectedBytes = 1_833_451_520L,
        sha256 = "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
        family = "DeepSeek-R1-Distill-Qwen",
        quantizationLabel = "multi-prefill-seq_q8_ekv4096",
        access = LiteRtCatalogAccess.OPEN_DIRECT,
        maturity = LiteRtModelMaturity.EXPERIMENTAL,
        tier = LiteRtModelTier.MEDIUM,
        // R1 distills are reasoning-oriented; internal traces must never reach Android TTS.
        supportsThinking = true,
        runtimeProfile = LiteRtRuntimeProfile(
            preferredBackend = LocalComputeBackend.AUTO,
            allowCpuFallback = true,
            knownContextTokens = 4096,
            defaultMaxOutputTokens = 512,
        ),
    )

    // -----------------------------------------------------------------------------------------
    // Gated (catalogued, import-only)
    // -----------------------------------------------------------------------------------------

    const val GEMMA3_1B_IT_INT4_ID = "gemma3-1b-it-int4"

    /** Upstream Edge Gallery commit reference currently used to identify the official artifact. */
    const val GEMMA3_1B_IT_INT4_EDGE_GALLERY_COMMIT = "42d538a932e8d5b12e6b3b455f5572560bd60b2c"

    /**
     * Gemma 3 1B IT INT4 — gated.
     *
     * Halo does not mirror Gemma and does not pretend the model does not exist. The entry exists so
     * a manually imported, already-licensed artifact that matches the official SHA and size is
     * canonicalized to this identity instead of being treated as an unknown user import.
     */
    val GEMMA3_1B_IT_INT4: LiteRtCatalogModel = LiteRtCatalogModel(
        id = GEMMA3_1B_IT_INT4_ID,
        displayName = "Gemma 3 1B IT INT4",
        repoId = "litert-community/Gemma3-1B-IT",
        revision = GEMMA3_1B_IT_INT4_EDGE_GALLERY_COMMIT,
        filename = "gemma3-1b-it-int4.litertlm",
        expectedBytes = 584_417_280L,
        sha256 = "1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be",
        contextWindowTokens = 4096,
        supportsVision = false,
        supportsTools = false,
        minLiteRtLmVersion = "0.17.1",
        family = "Gemma3",
        quantizationLabel = "int4",
        access = LiteRtCatalogAccess.GATED_IMPORT_ONLY,
        maturity = LiteRtModelMaturity.DEVELOPMENT,
        tier = LiteRtModelTier.SMALL,
        runtimeProfile = LiteRtRuntimeProfile(
            preferredBackend = LocalComputeBackend.AUTO,
            allowCpuFallback = true,
            knownContextTokens = null,
            // Halo has no verified artifact profile for this model: let the engine decide.
            useModelDefaultContext = true,
            defaultMaxOutputTokens = 512,
        ),
    )

    // -----------------------------------------------------------------------------------------
    // Lookup
    // -----------------------------------------------------------------------------------------

    /** Every built-in catalog entry, including import-only artifacts. */
    val BUILT_IN_MODELS: List<LiteRtCatalogModel> = listOf(
        QWEN3_0_6B_DYNAMIC_INT4,
        QWEN3_1_7B_DYNAMIC_INT4,
        QWEN2_5_1_5B_INSTRUCT_Q8,
        DEEPSEEK_R1_DISTILL_QWEN_1_5B_Q8,
        GEMMA3_1B_IT_INT4,
    )

    /** The development matrix: everything the built-in downloader is allowed to fetch. */
    val DIRECT_DOWNLOAD_MODELS: List<LiteRtCatalogModel> =
        BUILT_IN_MODELS.filter { it.directDownloadAllowed }

    /** Artifacts that may only be acquired by importing a licensed file from the device. */
    val IMPORT_ONLY_MODELS: List<LiteRtCatalogModel> =
        BUILT_IN_MODELS.filterNot { it.directDownloadAllowed }

    /**
     * Upstream repositories that require a gated license acceptance flow. Exposed as documentation
     * only — never as a downloadable entry and never through a third-party mirror.
     */
    val WITHHELD_GATED_REPO_IDS: Set<String> =
        IMPORT_ONLY_MODELS.mapTo(LinkedHashSet()) { it.repoId }

    init {
        validateCatalog(BUILT_IN_MODELS)
        DIRECT_DOWNLOAD_MODELS.forEach { model ->
            require(model.access == LiteRtCatalogAccess.OPEN_DIRECT) {
                "Gated/manual artifact '${model.id}' must never enter the direct downloader"
            }
        }
    }

    /**
     * Rejects duplicate identity within the catalog: a duplicate id, filename or SHA-256 would make
     * inventory canonicalization ambiguous.
     */
    internal fun validateCatalog(models: List<LiteRtCatalogModel>) {
        fun rejectDuplicates(selector: (LiteRtCatalogModel) -> String, label: String) {
            val duplicates = models.groupBy(selector).filterValues { it.size > 1 }.keys
            require(duplicates.isEmpty()) {
                "LiteRT catalog declares duplicate $label: ${duplicates.joinToString(", ")}"
            }
        }
        rejectDuplicates({ it.id }, "id")
        rejectDuplicates({ it.filename.lowercase() }, "filename")
        rejectDuplicates({ it.sha256 }, "sha256")
    }

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
