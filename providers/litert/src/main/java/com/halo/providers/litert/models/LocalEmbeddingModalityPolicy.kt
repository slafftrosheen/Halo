package com.halo.providers.litert.models

/**
 * Distinguishes a publisher's modalities from what Halo's native EmbeddingEngine
 * adapter actually accepts. Do not reuse generation-model supportsVision:
 * it means image-conditioned *generation*, not cross-modal retrieval.
 */
enum class EmbeddingInputModality { TEXT, IMAGE, AUDIO, VIDEO }

data class LocalEmbeddingModalityStatus(
    val publisherModalities: Set<EmbeddingInputModality>,
    /** Product-ready source/index/retrieval path, not merely an SDK input class. */
    val wiredModalities: Set<EmbeddingInputModality>,
    val userDescription: String,
    /** Native adapter source exists; modalities not in wired still need a consumer workflow or device proof. */
    val nativeAdapterModalities: Set<EmbeddingInputModality> = wiredModalities,
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
            model.sha256 == LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.sha256 &&
            model.revision == LiteRtModelCatalog.EMBEDDINGGEMMA2_740M.revision
        return if (isGemma2) LocalEmbeddingModalityStatus(
            publisherModalities = setOf(EmbeddingInputModality.TEXT, EmbeddingInputModality.IMAGE,
                EmbeddingInputModality.AUDIO, EmbeddingInputModality.VIDEO),
            wiredModalities = setOf(EmbeddingInputModality.TEXT,EmbeddingInputModality.IMAGE),
            userDescription = "EmbeddingGemma 2 can represent text, pictures, audio and video. " +
                "Halo can search text and explicitly selected Notebook pictures locally. Picture search " +
                "uses full PNG/JPEG images and requires a compatible image CPU encoder on this phone; " +
                "device qualification is still pending. Audio has a developer-only adapter without " +
                "a consumer index; video remains planned. " +
                "This model finds related content, but does not generate image descriptions.",
            nativeAdapterModalities = setOf(EmbeddingInputModality.TEXT,
                EmbeddingInputModality.IMAGE,EmbeddingInputModality.AUDIO),
        ) else LocalEmbeddingModalityStatus(
            publisherModalities = emptySet(),
            wiredModalities = emptySet(),
            userDescription = "This embedding artifact has not been qualified for Halo. " +
                "Inspect its exact model metadata and verify the supported input types on this device.",
        )
    }
}
