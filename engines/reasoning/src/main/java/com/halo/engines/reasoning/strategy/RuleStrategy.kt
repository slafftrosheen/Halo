package com.halo.engines.reasoning.strategy

import com.halo.runtime.cognition.ReasoningContext
import com.halo.runtime.cognition.Thought
import com.halo.runtime.cognition.ThoughtSource
import kotlinx.datetime.Clock

class RuleStrategy : ReasoningStrategy {
    override suspend fun execute(context: ReasoningContext): Thought {
        return Thought(
            reasoning = "Resolved using deterministic rules",
            confidence = 1.0f,
            source = ThoughtSource.RULE,
            timestamp = Clock.System.now()
        )
    }
}
