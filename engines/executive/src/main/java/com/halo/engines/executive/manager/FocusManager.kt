package com.halo.engines.executive.manager

import com.halo.runtime.cognition.AttentionState
import com.halo.runtime.cognition.Goal
import java.util.UUID

class FocusManager {
    private var totalTokens = 100

    fun updateTokens(availableTokens: Int) {
        totalTokens = availableTokens
    }

    fun allocateAttention(goals: List<Goal>): AttentionState {
        // Allocate tokens among goals based on their current priority
        // The more tokens, the more processing budget the goal receives from the kernel
        val activeGoals = goals.take(3) // Simple example
        return AttentionState(activeGoals)
    }

    fun handleInterruption(currentGoals: List<Goal>, interruption: Goal): AttentionState {
        // Evaluate if interruption displaces current primary focus
        val newGoals = mutableListOf(interruption)
        newGoals.addAll(currentGoals.take(2)) // Keep max 3
        return AttentionState(newGoals)
    }
}
