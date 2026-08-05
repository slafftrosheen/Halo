package com.halo.engines.interpretation.api

import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.WorkingMemory
import com.halo.sdk.runtime.Observation

data class InterpretationContext(
    val observation: Observation,
    val workingMemory: WorkingMemory,
    val activeGoal: Goal?,
    val activeTasks: List<Task>
)
