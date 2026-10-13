package com.halo.providers.litert.models

/**
 * Artifact *task*, not input modality. A vision-capable generator is still GENERATION;
 * a text-capable embedding model is still EMBEDDING, never a conversational LLM.
 *
 * LiteRT-LM's ModelInfo is the authoritative runtime type check after explicit download.
 * Catalog hints are conservative routing constraints, NOT proof that an artifact runs.
 */
enum class LiteRtArtifactTask {
    GENERATION,
    EMBEDDING,
    NON_GENERATIVE,
}

/** Source-level triage used only for discovery and import guards. */
object LiteRtArtifactTaskClassifier {
    fun fromSource(repoId: String, filename: String, pipelineTag: String? = null): LiteRtArtifactTask {
        val identity = "$repoId/$filename".lowercase()
        val pipeline = pipelineTag.orEmpty().lowercase().trim()
        return when {
            "embeddinggemma" in identity || "embedder" in identity ||
                "sentence-transformers" in identity || "embedding-" in identity ||
                pipeline in setOf("feature-extraction", "sentence-similarity", "image-feature-extraction") ->
                LiteRtArtifactTask.EMBEDDING
            pipeline.isNotBlank() && pipeline !in setOf(
                "text-generation", "text2text-generation", "conversational",
                "image-text-to-text", "image-to-text", "visual-question-answering",
                "audio-text-to-text"
            ) -> LiteRtArtifactTask.NON_GENERATIVE
            else -> LiteRtArtifactTask.GENERATION
        }
    }

    fun eligibleForConversation(task: LiteRtArtifactTask): Boolean =
        task == LiteRtArtifactTask.GENERATION
}
