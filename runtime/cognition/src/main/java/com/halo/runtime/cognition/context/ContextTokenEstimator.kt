package com.halo.runtime.cognition.context

/**
 * Approximate budgeting only. This is intentionally not named or reported as a
 * provider tokenizer measurement.
 */
fun interface ContextTokenEstimator {
    fun estimate(text: String): Int
}

/**
 * Conservative generic fallback for providers without a tokenizer bridge.
 * Provider-specific estimators may replace it later.
 */
class ConservativeContextTokenEstimator : ContextTokenEstimator {
    override fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        return ((text.length + 2) / 3).coerceAtLeast(1)
    }
}


data class ContextBudget(
    val totalEstimatedTokens: Int,
    val reservedOutputTokens: Int,
    val reservedToolTokens: Int,
    val reservedFramingTokens: Int,
    val availableContextTokens: Int,
)
