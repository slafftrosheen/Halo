package com.halo.runtime.cognition

data class Decision(
    val goal: Goal,
    val rationale: String,
    val confidence: Float
)
