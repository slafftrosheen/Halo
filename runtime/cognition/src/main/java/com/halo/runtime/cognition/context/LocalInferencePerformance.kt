package com.halo.runtime.cognition.context

internal data class LocalInferencePerformance(val history: InferenceContextBudget, val outputTokens: Int)
internal fun localInferencePerformance(onDevice: Boolean, contextLength: Long?, voice: Boolean): LocalInferencePerformance? =
    if (onDevice && (contextLength ?: Long.MAX_VALUE) <= 4096L)
        LocalInferencePerformance(InferenceContextBudget(if (voice) 512 else 1024), if (voice) 128 else 256)
    else null
