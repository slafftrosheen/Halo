package com.halo.engines.planning.planner

import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.Goal

interface Planner {
    fun canPlan(goal: Goal): Boolean
    suspend fun createPlan(goal: Goal): ExecutionPlan
}
