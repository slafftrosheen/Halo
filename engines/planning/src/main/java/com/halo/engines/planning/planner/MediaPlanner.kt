package com.halo.engines.planning.planner

import com.halo.runtime.cognition.*
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.MediaPayload
import kotlin.time.Duration.Companion.milliseconds

class MediaPlanner : Planner {
    override fun canPlan(goal: Goal): Boolean = goal.type == GoalType.MEDIA

    override suspend fun createPlan(goal: Goal): ExecutionPlan {
        require(goal.type == GoalType.MEDIA)
        val payload = goal.payload as? MediaPayload
        return ExecutionPlan(
            goal = goal,
            rootTask = MediaTask(mediaAction = payload?.action ?: "PLAY", mediaId = payload?.query ?: ""),
            estimatedDuration = 300.milliseconds,
            cost = CostEstimate(),
            risks = emptyList()
        )
    }
}
