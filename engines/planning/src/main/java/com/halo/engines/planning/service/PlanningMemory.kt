package com.halo.engines.planning.service

import com.halo.runtime.cognition.ExecutionPlan
import java.util.UUID

class PlanningMemory {
    var currentPlan: ExecutionPlan? = null
        private set
    var lastPlan: ExecutionPlan? = null
        private set

    fun save(plan: ExecutionPlan) {
        lastPlan = currentPlan
        currentPlan = plan
    }
}
