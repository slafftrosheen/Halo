package com.halo.runtime.cognition

import java.util.UUID
import com.halo.icl.ontology.Goal
import com.halo.sdk.runtime.ResponseDelivery

data class ExecutionPlan(
    val id: UUID = UUID.randomUUID(),
    val goal: Goal,
    val tasks: List<Task>,
    val responseDelivery: ResponseDelivery = ResponseDelivery.VISUAL_ONLY
)
