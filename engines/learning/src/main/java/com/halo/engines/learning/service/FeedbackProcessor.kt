package com.halo.engines.learning.service

import com.halo.runtime.cognition.learning.Feedback
import com.halo.runtime.cognition.learning.FeedbackSentiment
import com.halo.runtime.cognition.learning.Strategy

class FeedbackProcessor {
    fun process(feedback: Feedback, strategy: Strategy): Strategy {
        return when (feedback.sentiment) {
            FeedbackSentiment.Positive -> strategy.copy(weight = minOf(1.0f, strategy.weight + 0.2f))
            FeedbackSentiment.Negative -> strategy.copy(weight = maxOf(0.0f, strategy.weight - 0.5f))
            FeedbackSentiment.Correction -> {
                // In a real system, parsing the instruction modifies the parameters
                strategy.copy(weight = 1.0f) // The corrected strategy becomes absolute priority
            }
        }
    }
}
