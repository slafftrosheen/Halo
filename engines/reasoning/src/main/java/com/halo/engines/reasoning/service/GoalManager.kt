package com.halo.engines.reasoning.service

import com.halo.cognition.language.Intent
import com.halo.icl.ontology.*
import com.halo.icl.core.*
import kotlinx.datetime.Clock
import java.util.UUID

class GoalManager {
    private val goals = mutableMapOf<String, Goal>()

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
