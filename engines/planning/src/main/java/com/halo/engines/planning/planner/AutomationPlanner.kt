package com.halo.engines.planning.planner

import com.halo.runtime.cognition.*
import com.halo.icl.ontology.Goal
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.AutomationPayload
import kotlin.time.Duration.Companion.milliseconds

class AutomationPlanner : Planner {
    override fun canPlan(goal: Goal): Boolean = goal.type == GoalType.AUTOMATION

    override suspend fun createPlan(goal: Goal): ExecutionPlan {
        require(goal.type == GoalType.AUTOMATION)
        val payload = goal.payload as? AutomationPayload
        return ExecutionPlan(
            goal = goal,
            rootTask = SystemTask(command = "Execute automation: ${payload?.routine ?: "unknown"}"),
            estimatedDuration = 100.milliseconds,
            cost = CostEstimate(),
            risks = emptyList()
        )
    }
}
