package com.halo.engines.planning.planner

import com.halo.runtime.cognition.*
import kotlin.time.Duration.Companion.milliseconds

class LLMPlanner : Planner {
    override fun canPlan(goal: Goal): Boolean = true

    override suspend fun createPlan(goal: Goal): ExecutionPlan {
        return ExecutionPlan(
            goal = goal,
            rootTask = SystemTask(command = "LLM Generated plan"),
            estimatedDuration = 1000.milliseconds,
            cost = CostEstimate(cpuImpact = 0.8f),
            risks = emptyList()
        )
    }
}
