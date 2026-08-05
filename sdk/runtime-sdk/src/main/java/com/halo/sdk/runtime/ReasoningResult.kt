package com.halo.sdk.runtime

data class ReasoningResult(
    val intent: HaloIntent,
    val confidence: Float,
    val explanation: String
)

data class ReasoningCompleted(
    val result: ReasoningResult,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
