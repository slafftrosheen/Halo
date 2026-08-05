package com.halo.runtime.cognition.executive

data class RiskAssessment(
    val score: Float, // 0.0 to 1.0
    val requiresConfirmation: Boolean,
    val reasoning: String
)
