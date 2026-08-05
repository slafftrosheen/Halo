package com.halo.engines.learning.service

import com.halo.runtime.cognition.learning.Strategy
import com.halo.runtime.cognition.learning.ExecutionResult

class StrategyOptimizer {
    fun optimize(currentStrategy: Strategy, result: ExecutionResult): Strategy {
        var newWeight = currentStrategy.weight
        if (result.success) {
            newWeight = minOf(1.0f, newWeight + 0.05f) // Reinforce
        } else {
            newWeight = maxOf(0.0f, newWeight - 0.1f) // Penalize
        }
        
        return currentStrategy.copy(weight = newWeight)
    }
}
