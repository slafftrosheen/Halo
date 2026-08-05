package com.halo.runtime.cognition.learning

import java.util.UUID

data class Strategy(
    val id: UUID = UUID.randomUUID(),
    val domain: String, // e.g., "Navigation", "Reasoning"
    val parameters: Map<String, Any>,
    var weight: Float = 1.0f // Increases with success, decreases with failure
)
