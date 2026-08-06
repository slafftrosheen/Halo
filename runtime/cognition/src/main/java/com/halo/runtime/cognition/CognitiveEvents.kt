package com.halo.runtime.cognition

import com.halo.icl.ontology.Goal
import com.halo.sdk.runtime.HaloEvent

data class GoalCreated(
    val goal: Goal,
    override val timestamp: Long = System.currentTimeMillis()
) : HaloEvent
