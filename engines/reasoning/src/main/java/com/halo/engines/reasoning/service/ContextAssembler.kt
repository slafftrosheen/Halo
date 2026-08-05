package com.halo.engines.reasoning.service

import com.halo.runtime.cognition.ReasoningContext
import com.halo.runtime.cognition.blackboard.Interpretation
import kotlinx.datetime.Clock

class ContextAssembler(
    private val goalManager: GoalManager,
    private val thoughtRepository: ThoughtRepository
) {
    fun assemble(interpretation: Interpretation): ReasoningContext {
        return ReasoningContext(
            interpretation = interpretation,
            goals = goalManager.getActiveGoals(),
            memories = emptyList(), // Hook up to memory engine later
            deviceState = Any(), // Hook up to device state later
            environment = Any(), // Hook up to environment later
            conversation = Any(), // Hook up to conversation context later
            timestamp = Clock.System.now()
        )
    }
}
