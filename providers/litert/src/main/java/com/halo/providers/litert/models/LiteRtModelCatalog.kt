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
    // Downloads must only be published with an immutable artifact URL, exact byte
    // length, and a verified SHA-256. The former entries used mutable revisions and
    // placeholder hashes, so importing a user-supplied LiteRT model remains the only
    // supported installation path until verified catalog metadata is available.
    val BUILT_IN_MODELS: List<LiteRtCatalogModel> = emptyList()
}
