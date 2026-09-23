package com.halo.providers.llamacpp

enum class VoiceModelSuitability {
    EXCELLENT,
    GOOD,
    SLOW,
    UNSUITABLE,
    UNKNOWN,
}

fun calculateVoiceSuitability(
    loadMs: Long,
    generationTokensPerSecond: Float
): VoiceModelSuitability = when {
    loadMs <= 1500 && generationTokensPerSecond >= 15f -> VoiceModelSuitability.EXCELLENT
    loadMs <= 3000 && generationTokensPerSecond >= 8f -> VoiceModelSuitability.GOOD
    generationTokensPerSecond >= 4f -> VoiceModelSuitability.SLOW
    else -> VoiceModelSuitability.UNSUITABLE
}

data class ModelPerformanceProfile(
    val artifactId: String,
    val deviceFingerprint: String,
    val contextSize: Int,
    val threads: Int,
    val loadMs: Long,
    val promptTokensPerSecond: Float,
    val generationTokensPerSecond: Float,
    val peakEstimatedMemoryBytes: Long? = null,
    val testedAtEpochMs: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
) {
    val voiceSuitability: VoiceModelSuitability get() =
        calculateVoiceSuitability(loadMs, generationTokensPerSecond)
}
