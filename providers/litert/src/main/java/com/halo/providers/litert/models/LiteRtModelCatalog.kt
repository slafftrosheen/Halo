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
        supportsThinking = true,
        evaluationPurposes = setOf(LocalModelEvaluationPurpose.LATENCY, LocalModelEvaluationPurpose.GENERAL_CHAT),
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
        supportsThinking = true,
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
        evaluationPurposes = setOf(LocalModelEvaluationPurpose.REASONING),
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

    const val SMOLLM2_135M_INSTRUCT_ID = "smollm2-135m-instruct"
    val SMOLLM2_135M_INSTRUCT = LiteRtCatalogModel(
        id = SMOLLM2_135M_INSTRUCT_ID, displayName = "SmolLM2 135M Instruct",
        repoId = "litert-community/SmolLM2-135M-Instruct", revision = "749fc23bc8d7ef89db031d075004289c88f41c2f",
        filename = "SmolLM2_135M_Instruct.litertlm", expectedBytes = 142819328L,
        sha256 = "ccdc5c85735743f081b7d44ca309cab569f76c0f2f0e8e163449a63721969c37", contextWindowTokens = 4096,
        supportsVision = false, supportsTools = false, minLiteRtLmVersion = "0.17.1",
        family = "SmolLM2", license = "Apache-2.0",
        maturity = LiteRtModelMaturity.EXPERIMENTAL, tier = LiteRtModelTier.TINY,
        evaluationPurposes = setOf(LocalModelEvaluationPurpose.LATENCY),
        runtimeProfile = LiteRtRuntimeProfile(preferredBackend = LocalComputeBackend.CPU,
            knownContextTokens = 4096, defaultMaxOutputTokens = 192),
    )

    const val SMOLLM2_360M_INSTRUCT_ID = "smollm2-360m-instruct"
    val SMOLLM2_360M_INSTRUCT = LiteRtCatalogModel(
        id = SMOLLM2_360M_INSTRUCT_ID, displayName = "SmolLM2 360M Instruct",
        repoId = "litert-community/SmolLM2-360M-Instruct", revision = "507c99cfe6541ba2bcd84818786f7b025935e5e1",
        filename = "SmolLM2_360M_instruct.litertlm", expectedBytes = 373719040L,
        sha256 = "8e2834da211b439751af968ed650febdde5a8cb8d88bc6c1a3059f049caa5c2e", contextWindowTokens = 8192,
        supportsVision = false, supportsTools = false, minLiteRtLmVersion = "0.17.1",
        family = "SmolLM2", license = "Apache-2.0",
        maturity = LiteRtModelMaturity.DEVELOPMENT, tier = LiteRtModelTier.SMALL,
        evaluationPurposes = setOf(LocalModelEvaluationPurpose.LATENCY, LocalModelEvaluationPurpose.GENERAL_CHAT),
        runtimeProfile = LiteRtRuntimeProfile(preferredBackend = LocalComputeBackend.CPU,
            knownContextTokens = 8192, defaultMaxOutputTokens = 192),
    )

    /** Official Android multimodal artifacts; pins checked against Hub LFS metadata and Gallery. */
    val GEMMA3N_E2B = multimodalGemma(
        "gemma-3n-e2b-int4", "Gemma 3n E2B · Image & audio", "E2B",
        "ba9ca88da013b537b6ed38108be609b8db1c3a16", 3_655_827_456L,
        "2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6", 8,
    )
    val GEMMA3N_E4B = multimodalGemma(
        "gemma-3n-e4b-int4", "Gemma 3n E4B · Image & audio", "E4B",
        "297ed75955702dec3503e00c2c2ecbbf475300bc", 4_919_541_760L,
        "2e67a6cd51dfe0f793431e6bd4ed8d029c88e10f52ca0469ad38445e3cd3c1f4", 12,
    )

    private fun multimodalGemma(id: String, name: String, variant: String, revision: String,
        bytes: Long, hash: String, ramGb: Int) = LiteRtCatalogModel(
        id = id, displayName = name, repoId = "google/gemma-3n-$variant-it-litert-lm",
        revision = revision, filename = "gemma-3n-$variant-it-int4.litertlm",
        expectedBytes = bytes, sha256 = hash, contextWindowTokens = 4096,
        supportsVision = true, supportsAudio = true, supportsTools = false,
        minLiteRtLmVersion = "0.17.1", family = "Gemma3n", license = "gemma",
        access = LiteRtCatalogAccess.GATED_IMPORT_ONLY, maturity = LiteRtModelMaturity.EXPERIMENTAL,
        tier = LiteRtModelTier.LARGE, minDeviceRamGb = ramGb,
        toolCallPreference = LiteRtToolCallPreference.NONE,
        runtimeProfile = LiteRtRuntimeProfile(preferredBackend = LocalComputeBackend.AUTO,
            knownContextTokens = 4096, defaultMaxOutputTokens = 512,
            defaultTopK = 64, defaultTopP = 0.95, defaultTemperature = 1.0),
    )

    /** Current official open multimodal artifacts, pinned to Gallery's stable non-MTP files. */
    val GEMMA4_E2B = multimodalGemma4("E2B", "6e5c4f1e395deb959c494953478fa5cec4b8008f", 2_588_147_712L,
        "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", 8)
    val GEMMA4_E4B = multimodalGemma4("E4B", "28299f30ee4d43294517a4ac93abd6163412f07f", 3_659_530_240L,
        "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0", 12)

    private fun multimodalGemma4(variant: String, revision: String, bytes: Long, hash: String, ramGb: Int) = LiteRtCatalogModel(
        id = "gemma-4-${variant.lowercase()}", displayName = "Gemma 4 $variant · Image & audio",
        repoId = "litert-community/gemma-4-$variant-it-litert-lm", revision = revision,
        filename = "gemma-4-$variant-it.litertlm", expectedBytes = bytes, sha256 = hash,
        contextWindowTokens = 32000, supportsVision = true, supportsAudio = true, supportsThinking = true,
        supportsTools = false, minLiteRtLmVersion = "0.18.0", family = "Gemma4", license = "Apache-2.0",
        maturity = LiteRtModelMaturity.EXPERIMENTAL, tier = LiteRtModelTier.LARGE, minDeviceRamGb = ramGb,
        toolCallPreference = LiteRtToolCallPreference.NONE,
        runtimeProfile = LiteRtRuntimeProfile(preferredBackend = LocalComputeBackend.AUTO,
            knownContextTokens = 4096, defaultMaxOutputTokens = 512,
            defaultTopK = 64, defaultTopP = 0.95, defaultTemperature = 1.0),
    )

    /** Every built-in catalog entry, including licensed artifacts. */
    val BUILT_IN_MODELS: List<LiteRtCatalogModel> = listOf(
        QWEN3_0_6B_DYNAMIC_INT4,
        QWEN3_1_7B_DYNAMIC_INT4,
        QWEN2_5_1_5B_INSTRUCT_Q8,
        DEEPSEEK_R1_DISTILL_QWEN_1_5B_Q8,
        GEMMA3_1B_IT_INT4,
        SMOLLM2_135M_INSTRUCT,
        SMOLLM2_360M_INSTRUCT,
        GEMMA3N_E2B,
        GEMMA3N_E4B,
        GEMMA4_E2B,
        GEMMA4_E4B,
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
