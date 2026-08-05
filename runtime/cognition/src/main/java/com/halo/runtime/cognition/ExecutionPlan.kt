package com.halo.runtime.cognition

import java.util.UUID
import kotlin.time.Duration

data class CostEstimate(
    val timeMs: Long = 0L,
    val batteryImpact: Float = 0.0f,
    val networkBytes: Long = 0L,
    val cpuImpact: Float = 0.0f
)

data class PlanRisk(val description: String, val probability: Float)

data class ExecutionPlan(
    val id: UUID = UUID.randomUUID(),
    val goal: Goal,
    val rootTask: Task,
    val estimatedDuration: Duration?,
    val cost: CostEstimate,
    val risks: List<PlanRisk>
)
