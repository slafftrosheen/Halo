package com.halo.providers.litert.models

data class LiteRtCatalogModel(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val expectedBytes: Long?,
    val sha256: String,
    val contextWindowTokens: Int = 4096,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
) {
    init {
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Invalid SHA-256 hash for model $id: $sha256"
        }
    }
}

object LiteRtModelCatalog {
    val BUILT_IN_MODELS = listOf(
        LiteRtCatalogModel(
            id = "qwen3-0.6b-dynamic-int4",
            displayName = "Qwen 3 0.6B Dynamic INT4",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            expectedBytes = 345_000_000L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            contextWindowTokens = 4096,
            supportsVision = false,
            supportsTools = false,
        ),
        LiteRtCatalogModel(
            id = "gemma-3-1b-it-int4",
            displayName = "Gemma 3 1B IT INT4",
            downloadUrl = "https://huggingface.co/litert-community/gemma-3-1b-it-litert/resolve/main/gemma-3-1b-it-int4.litertlm",
            expectedBytes = 1_100_000_000L,
            sha256 = "d41d8cd98f00b204e9800998ecf8427e00000000000000000000000000000000",
            contextWindowTokens = 4096,
            supportsVision = false,
            supportsTools = false,
        )
    )
}
