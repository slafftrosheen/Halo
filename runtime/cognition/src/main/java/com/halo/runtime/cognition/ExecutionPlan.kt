package com.halo.runtime.cognition

import java.util.UUID
import kotlin.time.Duration
import com.halo.icl.ontology.Goal

data class ExecutionPlan(
    val id: UUID = UUID.randomUUID(),
    val goal: Goal,
    val tasks: List<Task>
)
