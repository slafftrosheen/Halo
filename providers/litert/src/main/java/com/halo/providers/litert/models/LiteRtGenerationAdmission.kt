package com.halo.providers.litert.models

/**
 * A model's file type (litertlm) is not its execution contract.
 * Keep embeddings and other non-generative artifacts OUT of the chat/router/tool and
 * generation-smoke-test paths. They can be retained in inventory for future task runtimes.
 */
object LiteRtGenerationAdmission {
    fun permitted(catalogModel: LiteRtCatalogModel?, filename: String?): Boolean {
        if (catalogModel != null && !LiteRtArtifactTaskClassifier.eligibleForConversation(catalogModel.task))
            return false
        return LiteRtArtifactTaskClassifier.eligibleForConversation(
            LiteRtArtifactTaskClassifier.fromSource(catalogModel?.repoId.orEmpty(),
                filename.orEmpty()))
    }

    fun explanation(catalogModel: LiteRtCatalogModel?): String = when (catalogModel?.task) {
        LiteRtArtifactTask.EMBEDDING ->
            "Embedding artifact: available for future local retrieval, not a chat or agentic generator."
        LiteRtArtifactTask.NON_GENERATIVE ->
            "Non-generative artifact: use the dedicated task runtime, not LiteRT chat."
        else -> "Artifact is not a supported conversational LiteRT language model."
    }
}
