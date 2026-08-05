package com.halo.runtime.cognition

import com.halo.sdk.runtime.Observation

class WorkingMemory(
    val observations: MutableList<Observation> = mutableListOf(),
    val activeGoals: MutableList<Goal> = mutableListOf(),
    val thoughts: MutableList<Thought> = mutableListOf()
)
