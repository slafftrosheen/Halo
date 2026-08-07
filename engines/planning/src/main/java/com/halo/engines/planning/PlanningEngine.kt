package com.halo.engines.planning

import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.services.Services
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.Task
import kotlinx.coroutines.launch

class PlanningEngine {
    fun createPlan(goal: Goal): ExecutionPlan {
        // Alpha: Map Goal to appropriate ExecutionPlan task
        val task = when (goal.type) {
            com.halo.icl.ontology.GoalType.CONVERSATION -> {
                val topic = (goal.payload as? com.halo.icl.ontology.ConversationPayload)?.topic ?: ""
                com.halo.runtime.cognition.CommunicationTask(
                    targetId = "user",
                    payload = topic
                )
            }
            else -> com.halo.runtime.cognition.SystemTask(
                command = "Execute Goal ${goal.type} ${goal.payload}"
            )
        }
        return ExecutionPlan(
            goal = goal,
            tasks = listOf(task)
        )
    }
}
