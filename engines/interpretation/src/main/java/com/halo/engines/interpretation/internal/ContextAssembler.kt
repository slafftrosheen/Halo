package com.halo.engines.interpretation.internal

import com.halo.engines.interpretation.api.InterpretationContext
import com.halo.runtime.cognition.WorkingMemory
import com.halo.sdk.runtime.Observation

class ContextAssembler(
    private val workingMemory: WorkingMemory
) {
    suspend fun assemble(
        observation: Observation
    ): InterpretationContext = InterpretationContext(
        observation = observation,
        workingMemory = workingMemory,
        activeGoal = workingMemory.currentGoal(),
        activeTasks = workingMemory.activeTasks()
    )
}
