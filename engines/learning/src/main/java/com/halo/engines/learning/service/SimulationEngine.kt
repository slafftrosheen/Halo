package com.halo.engines.learning.service

import com.halo.runtime.cognition.learning.Strategy
import com.halo.runtime.cognition.learning.LearningContext
import com.halo.runtime.cognition.learning.FeedbackSentiment

class SimulationEngine {
    fun simulate(strategy: Strategy, context: LearningContext): Float {
        var score = strategy.weight.coerceIn(0f, 1f)
        context.execution?.let { execution ->
            score += if (execution.success) 0.2f else -0.3f
            if (execution.latencyMs > 10_000L) score -= 0.1f
            if (!execution.error.isNullOrBlank()) score -= 0.1f
        }
        context.userFeedback?.let { feedback ->
            score += when (feedback.sentiment) {
                FeedbackSentiment.Positive -> 0.2f
                FeedbackSentiment.Negative -> -0.25f
                FeedbackSentiment.Correction -> -0.15f
            }
        }
        if (context.memories.isNotEmpty()) {
            val evidence = context.memories.map { it.confidence.coerceIn(0f, 1f) }.average().toFloat()
            score = score * 0.8f + evidence * 0.2f
        }
        return score.coerceIn(0f, 1f)
    }
}
