package com.halo.engines.planning.planner

import com.halo.runtime.cognition.*
import kotlin.time.Duration.Companion.milliseconds

class NavigationPlanner : Planner {
    override fun canPlan(goal: Goal): Boolean = goal is NavigationGoal

    override suspend fun createPlan(goal: Goal): ExecutionPlan {
        require(goal is NavigationGoal)
        return ExecutionPlan(
            goal = goal,
            rootTask = NavigationTask(coordinates = goal.destination),
            estimatedDuration = 500.milliseconds,
            cost = CostEstimate(),
            risks = emptyList()
        )
    }
}
