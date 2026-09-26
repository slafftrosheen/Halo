package com.halo.providers.llamacpp

/**
 * Canonical classification for Hugging Face `.gguf` siblings.
 *
 * A Hugging Face repository is a bag of files, not a bag of models.
 * Every sibling must be classified BEFORE it becomes selectable as a model.
 *
 * Classification is conservative. A valid GGUF container is never automatically a
 * runnable base model. Even a correctly classified BASE_MODEL may still be rejected
 * by native llama.cpp at load time.
 */
enum class GgufArtifactRole {
    BASE_MODEL,
    MULTIMODAL_PROJECTOR,
    IMATRIX,
    MTP_AUXILIARY,
    DRAFT_MODEL,
    SPLIT_MODEL_SHARD,
    UNKNOWN_AUXILIARY,
}
