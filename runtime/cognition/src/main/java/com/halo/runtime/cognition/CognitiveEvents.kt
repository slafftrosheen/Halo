package com.halo.runtime.cognition

import com.halo.icl.ontology.Goal
import com.halo.sdk.runtime.HaloEvent

import java.util.UUID

data class GoalCreated(
    override val id: String = UUID.randomUUID().toString(),
    val goal: Goal,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
