package com.halo.engines.planning.planner

import com.halo.runtime.cognition.*
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.NavigationPayload
import kotlin.time.Duration.Companion.milliseconds

class NavigationPlanner : Planner {
    override fun canPlan(goal: Goal): Boolean = goal.type == GoalType.NAVIGATION

    override suspend fun createPlan(goal: Goal): ExecutionPlan {
        require(goal.type == GoalType.NAVIGATION)
        val payload = goal.payload as? NavigationPayload
        return ExecutionPlan(
            goal = goal,
            rootTask = NavigationTask(coordinates = payload?.destination ?: ""),
            estimatedDuration = 500.milliseconds,
            cost = CostEstimate(),
            risks = emptyList()
        )
    }
}
