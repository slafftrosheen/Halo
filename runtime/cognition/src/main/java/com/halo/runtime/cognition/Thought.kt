package com.halo.runtime.cognition

enum class ThoughtSource {
    RULE,
    MEMORY,
    AI,
    SENSOR
}

data class Thought(
    val text: String,
    val confidence: Float,
    val source: ThoughtSource
)
