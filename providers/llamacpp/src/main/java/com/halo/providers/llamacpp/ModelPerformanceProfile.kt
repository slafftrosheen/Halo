package com.halo.providers.llamacpp

import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.provider.LocalModelRuntime

enum class VoiceModelSuitability {
    EXCELLENT,
    GOOD,
    SLOW,
    UNSUITABLE,
    UNKNOWN,
}

fun calculateVoiceSuitability(
    loadMs: Long,
    generationTokensPerSecond: Float,
    ttftMs: Long = loadMs,
): VoiceModelSuitability = when {
    ttftMs <= 750 && generationTokensPerSecond >= 15f -> VoiceModelSuitability.EXCELLENT
    ttftMs <= 1500 && generationTokensPerSecond >= 8f -> VoiceModelSuitability.GOOD
    ttftMs <= 3000 && generationTokensPerSecond >= 4f -> VoiceModelSuitability.SLOW
    else -> VoiceModelSuitability.UNSUITABLE
}

data class ModelPerformanceProfile(
    val artifactId: String,
    val artifactFingerprint: String,
    val deviceFingerprint: String,
    val runtime: LocalModelRuntime,
    val backend: LocalComputeBackend,
    val contextSize: Int,
    val threads: Int,
    val gpuLayers: Int,
    val loadMs: Long,
    val ttftMs: Long,
    val promptTokensPerSecond: Float,
    val generationTokensPerSecond: Float,
    val peakEstimatedMemoryBytes: Long? = null,
    val testedAtEpochMs: Long = System.currentTimeMillis(),
    val appVersion: String,
    val runtimeVersion: String,
    val compatibilityEpoch: Int,
    val stable: Boolean,
    val failureReason: String? = null,
) {
    val voiceSuitability: VoiceModelSuitability get() =
        calculateVoiceSuitability(loadMs, generationTokensPerSecond, ttftMs)
}
