package com.halo.engines.planning.planner

import com.halo.runtime.cognition.*
import kotlin.time.Duration.Companion.milliseconds

class RulePlanner : Planner {
    override fun canPlan(goal: Goal): Boolean = true

    override suspend fun createPlan(goal: Goal): ExecutionPlan {
        return ExecutionPlan(
            goal = goal,
            rootTask = SystemTask(command = "Rule-based command"),
            estimatedDuration = 100.milliseconds,
            cost = CostEstimate(),
            risks = emptyList()
        )
    }
}
