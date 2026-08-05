package com.halo.runtime.cognition.learning

import java.util.UUID

enum class FeedbackSentiment {
    Positive,
    Negative,
    Correction
}

data class Feedback(
    val id: UUID = UUID.randomUUID(),
    val sentiment: FeedbackSentiment,
    val targetGoalId: UUID?,
    val instruction: String?
)
