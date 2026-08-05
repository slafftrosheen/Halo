package com.halo.engines.interpretation.api

import com.halo.engines.interpretation.model.Entity
import com.halo.engines.interpretation.model.Intent

data class RuleMatch(
    val confidence: Float,
    val intent: Intent? = null,
    val entities: List<Entity> = emptyList(),
    val consumedTokens: Int = 0,
    val diagnostics: String? = null,
    val ruleName: String = "",
    val priority: Int = 0,
    val latencyMillis: Long = 0L
) {
    init {
        require(confidence in 0f..1f) { "confidence must be between zero and one" }
        require(consumedTokens >= 0) { "consumedTokens must not be negative" }
        require(latencyMillis >= 0) { "latencyMillis must not be negative" }
    }
}
