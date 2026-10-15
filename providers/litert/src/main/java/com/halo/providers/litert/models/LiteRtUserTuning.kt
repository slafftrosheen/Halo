package com.halo.providers.litert.models

enum class LiteRtThinkingMode { AUTO, OFF, ON }
enum class LiteRtTuningPreset { BALANCED, FAST, PRECISE }

/** One validation point for persisted model-scoped controls. Null means existing fallback. */
data class LiteRtUserTuning(
    val temperature: Double? = null,
    val topP: Double? = null,
    val topK: Int? = null,
    val maxOutputTokens: Int? = null,
    val thinkingMode: LiteRtThinkingMode = LiteRtThinkingMode.AUTO,
    val thinkingTokenBudget: Int? = null,
    val cpuThreads: Int? = null,
    val streaming: Boolean? = null,
) {
    init {
        require(temperature == null || temperature.isFinite() && temperature in 0.0..2.0)
        require(topP == null || topP.isFinite() && topP > 0 && topP <= 1)
        require(topK == null || topK in 1..200)
        require(maxOutputTokens == null || maxOutputTokens in 16..2048)
        require(thinkingTokenBudget == null || thinkingTokenBudget in 0..1024)
        require(cpuThreads == null || cpuThreads in 1..32)
    }
    companion object {
        fun preset(preset: LiteRtTuningPreset): LiteRtUserTuning = when (preset) {
            LiteRtTuningPreset.BALANCED -> LiteRtUserTuning(temperature = .7, topP = .9, topK = 40, maxOutputTokens = 512)
            LiteRtTuningPreset.FAST -> LiteRtUserTuning(temperature = .3, topP = .8, topK = 20, maxOutputTokens = 256, thinkingMode = LiteRtThinkingMode.OFF)
            LiteRtTuningPreset.PRECISE -> LiteRtUserTuning(temperature = .1, topP = .9, topK = 20, maxOutputTokens = 512)
        }
    }
}
