package com.halo.runtime.cognition.learning

import com.halo.runtime.cognition.blackboard.KnowledgePayload
import kotlinx.datetime.Instant

data class PatternKnowledge(
    val detectedHabits: List<Habit>,
    val timestamp: Instant
) : KnowledgePayload

data class StrategyKnowledge(
    val updatedStrategies: List<Strategy>,
    val timestamp: Instant
) : KnowledgePayload

data class LearningKnowledge(
    val patterns: PatternKnowledge,
    val strategies: StrategyKnowledge,
    val summary: String,
    val timestamp: Instant
) : KnowledgePayload
