package com.halo.engines.planning.planner

import com.halo.runtime.cognition.*
import kotlin.time.Duration.Companion.milliseconds

class AutomationPlanner : Planner {
    override fun canPlan(goal: Goal): Boolean = goal is AutomationGoal

    override suspend fun createPlan(goal: Goal): ExecutionPlan {
        require(goal is AutomationGoal)
        return ExecutionPlan(
            goal = goal,
            rootTask = SystemTask(command = "Execute automation: ${goal.action}"),
            estimatedDuration = 100.milliseconds,
            cost = CostEstimate(),
            risks = emptyList()
        )
    }
}
