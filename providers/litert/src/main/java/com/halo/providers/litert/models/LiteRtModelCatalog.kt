package com.halo.providers.litert.models

data class LiteRtCatalogModel(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val expectedBytes: Long?,
    val sha256: String,
    val contextWindowTokens: Int,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
)

object LiteRtModelCatalog {
    val BUILT_IN_MODELS = listOf(
        LiteRtCatalogModel(
            id = "gemma-3-1b-it-int4",
            displayName = "Gemma 3 1B IT (INT4)",
            downloadUrl = "https://huggingface.co/google/gemma-3-1b-it-litert/resolve/main/gemma-3-1b-it-int4.litertlm",
            expectedBytes = 1_100_000_000L,
            sha256 = "d41d8cd98f00b204e9800998ecf8427e",
            contextWindowTokens = 4096,
            supportsVision = false,
            supportsTools = false,
        ),
        LiteRtCatalogModel(
            id = "qwen-3-0.6b-int4",
            displayName = "Qwen 3 0.6B (INT4)",
            downloadUrl = "https://huggingface.co/Qwen/Qwen3-0.6B-LiteRT/resolve/main/qwen3-0.6b-int4.litertlm",
            expectedBytes = 450_000_000L,
            sha256 = "e2fc714c4727ee9395f324cd2e7f331f",
            contextWindowTokens = 4096,
            supportsVision = false,
            supportsTools = false,
        )
    )
}
