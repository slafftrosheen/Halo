package com.halo.engines.executive.manager

import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.executive.CognitiveMode

class PriorityManager {
    fun calculateScore(goal: Goal, mode: CognitiveMode): Float {
        // Priority Score = Importance × Urgency × Confidence × User Preference × Context
        
        var score = 1.0f // Baseline
        
        // Mock contextual modifications
        if (mode == CognitiveMode.Driving && goal.javaClass.simpleName == "MediaGoal") {
            score *= 0.5f // Less important than navigation
        }
        
        if (mode == CognitiveMode.Emergency) {
            if (goal.javaClass.simpleName == "CommunicationGoal") {
                score *= 5.0f
            } else {
                score *= 0.1f
            }
        }
        
        return score
    }
}
