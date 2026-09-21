package com.halo.runtime.cognition.learning

import java.util.UUID

@Deprecated("Use SkillDescriptor for user-facing skills")
typealias Skill = LearnedSkillPerformance

data class LearnedSkillPerformance(
    val id: UUID = UUID.randomUUID(),
    val name: String,
    val capabilityId: String,
    var experiencePoints: Int = 0,
    var confidence: Float = 0.5f,
    var averageLatencyMs: Long = 0L,
    var successRate: Float = 0.0f,
    var executionCount: Int = 0
) {
    fun recordExecution(success: Boolean, latencyMs: Long) {
        executionCount++
        if (success) experiencePoints += 10
        
        // Update rolling averages
        averageLatencyMs = ((averageLatencyMs * (executionCount - 1)) + latencyMs) / executionCount
        
        val successfulExecutions = successRate * (executionCount - 1) + if (success) 1 else 0
        successRate = successfulExecutions / executionCount
        
        // Simple confidence correlation with success rate
        confidence = successRate
    }
}
