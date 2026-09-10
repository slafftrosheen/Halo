package com.halo.runtime.cognition.session

/**
 * Immutable provider/model binding for one cognitive request.
 *
 * Once a CognitiveSession resolves this target, every generation round
 * belonging to the request must use the same provider and model.
 */
data class AiExecutionTarget(
    val providerId: String,
    val modelId: String
)
