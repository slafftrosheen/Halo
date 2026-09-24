package com.halo.engines.executive.manager

import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.executive.CognitiveMode
import com.halo.icl.ontology.GoalType
import com.halo.icl.ontology.Priority

class PriorityManager {
    fun calculateScore(goal: Goal, mode: CognitiveMode): Float {
        val priority = when (goal.priority) {
            Priority.CRITICAL -> 1.0f
            Priority.HIGH -> 0.8f
            Priority.NORMAL -> 0.55f
            Priority.BACKGROUND -> 0.25f
        }
        val confidence = goal.confidence.value.coerceIn(0f, 1f)
        val context = when (mode) {
            CognitiveMode.Driving -> when (goal.type) {
                GoalType.NAVIGATION, GoalType.COMMUNICATION -> 1.0f
                GoalType.MEDIA -> 0.45f
                else -> 0.7f
            }
            CognitiveMode.Emergency -> when (goal.type) {
                GoalType.COMMUNICATION, GoalType.NAVIGATION -> 1.0f
                else -> 0.15f
            }
            CognitiveMode.Silent, CognitiveMode.Meeting -> when (goal.type) {
                GoalType.COMMUNICATION -> 0.8f
                GoalType.MEDIA -> 0.25f
                else -> 0.6f
            }
            CognitiveMode.Automation -> if (goal.type == GoalType.AUTOMATION) 1.0f else 0.6f
            CognitiveMode.Learning -> if (goal.type == GoalType.MEMORY) 1.0f else 0.8f
            CognitiveMode.Normal, CognitiveMode.Developer -> 1.0f
        }
        return (priority * (0.5f + confidence * 0.5f) * context).coerceIn(0f, 1f)
    }
}
