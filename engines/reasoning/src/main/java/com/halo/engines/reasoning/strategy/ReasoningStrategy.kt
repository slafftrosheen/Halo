package com.halo.engines.reasoning.strategy

import com.halo.runtime.cognition.ReasoningContext
import com.halo.runtime.cognition.Thought

interface ReasoningStrategy {
    suspend fun execute(context: ReasoningContext): Thought
}
