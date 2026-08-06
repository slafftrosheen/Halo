package com.halo.engines.reasoning.service

import com.halo.cognition.language.Intent
import com.halo.icl.ontology.*
import com.halo.icl.core.*
import kotlinx.datetime.Clock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * GoalManager tracks the lifecycle of Goals within the Alpha Runtime.
 *
 * Alpha Architecture constraints:
 * - Deterministic creation: ONLY the [RuntimeOrchestrator] (via the Interpretation layer) is allowed to generate Goals.
 * - No redundant goal generators allowed in the wild.
 * - Thread Safety: Uses [ConcurrentHashMap] to prevent race conditions during concurrent goal operations under high coroutine load.
 */
class GoalManager {
    private val goals = ConcurrentHashMap<String, Goal>()

    fun register(goal: Goal) {
        goals[goal.id.value.toString()] = goal
    }

    fun getGoal(id: String): Goal? = goals[id]
    
    fun getActiveGoals(): List<Goal> = goals.values.toList()
    
    fun createGoal(
        type: GoalType,
        intent: Intent?,
        payload: GoalPayload,
        priority: Priority = Priority.NORMAL,
        confidence: Float = 1.0f
    ): Goal {
        val goalId = CognitiveId()
        val goal = Goal(
            id = goalId,
            created = Clock.System.now(),
            confidence = Confidence(confidence),
            provenance = Provenance(generatorId = "engine.reasoning.core", parentId = null),
            edges = emptyList(),
            type = type,
            priority = priority,
            payload = payload
        )
        register(goal)
        return goal
    }
}
