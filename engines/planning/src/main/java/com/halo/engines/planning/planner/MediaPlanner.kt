package com.halo.engines.planning.planner

import com.halo.runtime.cognition.*
import kotlin.time.Duration.Companion.milliseconds

class MediaPlanner : Planner {
    override fun canPlan(goal: Goal): Boolean = goal is MediaGoal

    override suspend fun createPlan(goal: Goal): ExecutionPlan {
        require(goal is MediaGoal)
        return ExecutionPlan(
            goal = goal,
            rootTask = MediaTask(mediaAction = "PLAY", mediaId = goal.query),
            estimatedDuration = 300.milliseconds,
            cost = CostEstimate(),
            risks = emptyList()
        )
    }
}
