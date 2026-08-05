package com.halo.runtime.cognition.knowledge

data class Interpretation(
    val entities: List<Entity> = emptyList(),
    val candidates: List<IntentCandidate> = emptyList(),
    val slots: List<Slot> = emptyList(),
    val confidence: Confidence = Confidence.None
)
