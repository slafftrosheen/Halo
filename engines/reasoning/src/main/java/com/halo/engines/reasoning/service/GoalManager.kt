package com.halo.engines.reasoning.service

import com.halo.runtime.cognition.Goal
import java.util.UUID

class GoalManager {
    private val goals = mutableMapOf<UUID, Goal>()

    fun register(goal: Goal) {
        goals[goal.id] = goal
    }

    fun getGoal(id: UUID): Goal? = goals[id]
    
    fun getActiveGoals(): List<Goal> = goals.values.toList()
}
