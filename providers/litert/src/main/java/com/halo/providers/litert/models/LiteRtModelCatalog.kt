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
    val BUILT_IN_MODELS: List<LiteRtCatalogModel> = listOf(
        LiteRtCatalogModel(
            id = "qwen3-0.6b-dynamic-int4",
            displayName = "Qwen 3 0.6B Dynamic INT4",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/d12e8412674e2a868c2fdfbc0a3195022e519208/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            expectedBytes = 345_000_000L,
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            contextWindowTokens = 4096,
            supportsVision = false,
            supportsTools = false,
        ),
        LiteRtCatalogModel(
            id = "gemma-3-1b-it-int4",
            displayName = "Gemma 3 1B IT INT4",
            downloadUrl = "https://huggingface.co/litert-community/gemma-3-1b-it-litert/resolve/b6f53a1a9e32a67e1a38bc1b9134b9281a8b9e11/gemma-3-1b-it-int4.litertlm",
            expectedBytes = 1_100_000_000L,
            sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            contextWindowTokens = 4096,
            supportsVision = false,
            supportsTools = false,
        )
    )
}
