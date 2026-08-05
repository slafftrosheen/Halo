package com.halo.runtime.cognition.learning

import java.util.UUID

data class Prediction(
    val id: UUID = UUID.randomUUID(),
    val predictedGoalType: String,
    val confidence: Float,
    val context: String
)
