package com.halo.runtime.cognition.learning

import java.util.UUID

data class Habit(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val triggerContext: Map<String, String>, // e.g., "Time" -> "Morning", "App" -> "Spotify"
    val predictedActions: List<String>,
    val confidence: Float
)
