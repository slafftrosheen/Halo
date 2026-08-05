package com.halo.engines.reasoning.strategy

import com.halo.runtime.cognition.ReasoningContext
import com.halo.runtime.cognition.Thought
import com.halo.runtime.cognition.ThoughtSource
import kotlinx.datetime.Clock

class LLMStrategy : ReasoningStrategy {
    override suspend fun execute(context: ReasoningContext): Thought {
        return Thought(
            reasoning = "Resolved using LLM generation",
            confidence = 0.9f,
            source = ThoughtSource.AI,
            timestamp = Clock.System.now()
        )
    }
}
