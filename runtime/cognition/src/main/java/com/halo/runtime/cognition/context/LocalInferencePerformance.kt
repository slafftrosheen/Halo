package com.halo.runtime.cognition.context

import com.halo.sdk.runtime.NativeMemoryPressure

enum class LocalPerformanceTier { UNKNOWN, GOOD, SLOW, VERY_SLOW }

data class LocalInferencePerformance(
    val history: InferenceContextBudget,
    val outputTokens: Int,
    val tier: LocalPerformanceTier,
    val measured: Boolean,
)

/** Provisional latency controls for history, not model context limits. Never expands baseline. */
fun localInferencePerformance(
    onDevice: Boolean,
    contextLength: Long?,
    voice: Boolean,
    profile: LocalInferenceRuntimeProfile? = null,
    pressure: NativeMemoryPressure = NativeMemoryPressure.NORMAL,
): LocalInferencePerformance? {
    if (!onDevice || (contextLength ?: Long.MAX_VALUE) > 4096L) return null
    val measured = profile?.stable == true && profile.ttftMs?.let { it >= 0 } == true
    val normal = when {
        !measured -> LocalPerformanceTier.UNKNOWN
        profile!!.ttftMs!! <= 4000 -> LocalPerformanceTier.GOOD
        profile.ttftMs!! <= 8000 -> LocalPerformanceTier.SLOW
        else -> LocalPerformanceTier.VERY_SLOW
    }
    val tier = when (pressure) {
        NativeMemoryPressure.CRITICAL -> LocalPerformanceTier.VERY_SLOW
        NativeMemoryPressure.LOW -> when (normal) {
            LocalPerformanceTier.UNKNOWN, LocalPerformanceTier.GOOD -> LocalPerformanceTier.SLOW
            else -> LocalPerformanceTier.VERY_SLOW
        }
        NativeMemoryPressure.NORMAL -> normal
    }
    val (history, output) = when (tier) {
        LocalPerformanceTier.UNKNOWN, LocalPerformanceTier.GOOD -> if (voice) 512 to 128 else 1024 to 256
        LocalPerformanceTier.SLOW -> if (voice) 384 to 112 else 768 to 224
        LocalPerformanceTier.VERY_SLOW -> if (voice) 256 to 96 else 512 to 192
    }
    return LocalInferencePerformance(InferenceContextBudget(history), output, tier, measured)
}
