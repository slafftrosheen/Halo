package com.halo.runtime.cognition

import kotlinx.datetime.Instant
import java.util.UUID

enum class ThoughtSource {
    RULE,
    MEMORY,
    AI,
    SENSOR
}

data class Thought(
    val id: UUID = UUID.randomUUID(),
    val reasoning: String,
    val confidence: Float,
    val source: ThoughtSource,
    val timestamp: Instant
)
