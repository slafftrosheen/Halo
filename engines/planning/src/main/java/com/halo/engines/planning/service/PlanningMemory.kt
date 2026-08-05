package com.halo.engines.planning.service

import com.halo.runtime.cognition.ExecutionPlan
import java.util.UUID

class PlanningMemory {
    private val plans = mutableMapOf<UUID, ExecutionPlan>()

    fun save(plan: ExecutionPlan) {
        plans[plan.id] = plan
    }

    fun getPlan(id: UUID): ExecutionPlan? = plans[id]

    fun getAllPlans(): List<ExecutionPlan> = plans.values.toList()
}
