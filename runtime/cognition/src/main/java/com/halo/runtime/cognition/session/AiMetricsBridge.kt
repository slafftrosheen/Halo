package com.halo.runtime.cognition.session

import com.halo.sdk.provider.AiGenerationMetrics
import com.halo.sdk.runtime.CognitiveGenerationMetrics

/**
 * Maps provider-owned generation metrics onto the provider-neutral runtime event type.
 *
 * Runtime events must not expose sdk.provider types (see [CognitiveGenerationMetrics]), so the
 * conversion belongs here, in the module that already sees both sides.
 */
internal fun AiGenerationMetrics.toRuntimeMetrics(): CognitiveGenerationMetrics = CognitiveGenerationMetrics(
    startedAtMs = startedAtMs,
    firstTokenAtMs = firstTokenAtMs,
    completedAtMs = completedAtMs,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
)
