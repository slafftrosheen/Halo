package com.halo.runtime.cognition

import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.sdk.runtime.Engine

abstract class CognitiveCycle {
    abstract suspend fun assembleContext(): ReasoningContext
    abstract suspend fun selectStrategy(context: ReasoningContext): Any // Typed to Any until Strategy interface is created
    abstract suspend fun generateThought(context: ReasoningContext, strategy: Any): Thought
    abstract suspend fun produceDecision(thought: Thought): Decision
    abstract suspend fun createGoals(decision: Decision): List<Goal>
    abstract suspend fun publishKnowledge(goals: List<Goal>, blackboard: Blackboard)

    suspend fun runCycle(blackboard: Blackboard) {
        val context = assembleContext()
        val strategy = selectStrategy(context)
        val thought = generateThought(context, strategy)
        val decision = produceDecision(thought)
        val goals = createGoals(decision)
        publishKnowledge(goals, blackboard)
    }
}
