package com.halo.providers.litert.models

/**
 * Distinguishes a publisher's modalities from what Halo's native EmbeddingEngine
 * adapter actually accepts. Do not reuse generation-model supportsVision:
 * it means image-conditioned *generation*, not cross-modal retrieval.
 */
enum class EmbeddingInputModality { TEXT, IMAGE, AUDIO, VIDEO }

data class LocalEmbeddingModalityStatus(
    val publisherModalities: Set<EmbeddingInputModality>,
    val wiredModalities: Set<EmbeddingInputModality>,
    val userDescription: String,
) {
    fun canEmbed(modality: EmbeddingInputModality): Boolean = modality in wiredModalities
}

object LocalEmbeddingModalityPolicy {
    fun forCatalog(model: LiteRtCatalogModel): LocalEmbeddingModalityStatus {
        if (model.task != LiteRtArtifactTask.EMBEDDING) return LocalEmbeddingModalityStatus(
            emptySet(), emptySet(), "This model does not provide semantic embeddings."
        )
        // Exact known publisher family. An imported model with a similar name
        // must not inherit permissions or capabilities from this catalog entry.
        val isGemma2 = model.id == LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.id &&
            model.sha256 == LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.sha256
        return if (isGemma2) LocalEmbeddingModalityStatus(
            publisherModalities = setOf(EmbeddingInputModality.TEXT, EmbeddingInputModality.IMAGE,
                EmbeddingInputModality.AUDIO, EmbeddingInputModality.VIDEO),
            wiredModalities = setOf(EmbeddingInputModality.TEXT),
            userDescription = "EmbeddingGemma 2 can represent text, pictures, audio and video. " +
                "Halo currently connects text search only; image, audio and video retrieval " +
                "require separate native adapters and real-device verification. " +
                "It finds related content, but cannot describe or edit an image.",
        ) else LocalEmbeddingModalityStatus(
            publisherModalities = setOf(EmbeddingInputModality.TEXT),
            wiredModalities = setOf(EmbeddingInputModality.TEXT),
            userDescription = "Text embeddings for local search. Other input types are not verified for this artifact.",
        )
    }
}
