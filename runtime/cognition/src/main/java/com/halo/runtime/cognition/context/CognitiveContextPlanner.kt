package com.halo.runtime.cognition.context

import com.halo.sdk.provider.AiContextPolicy

data class CognitiveContextPlan(
    val totalTokens: Int,
    val responseReserveTokens: Int,
    val framingReserveTokens: Int,
    val toolBudgetTokens: Int,
    val explicitContextBudgetTokens: Int,
    val memoryBudgetTokens: Int,
    val conversationBudgetTokens: Int,
)

class CognitiveContextPlanner {

    fun computePlan(
        modelContextWindowTokens: Int,
        mode: CognitiveOperatingMode,
        policy: AiContextPolicy,
    ): CognitiveContextPlan {
        val total = modelContextWindowTokens.coerceAtLeast(1024)
        val responseReserve = (total * 0.20).toInt().coerceIn(256, 2048)
        val framingReserve = (total * 0.10).toInt().coerceIn(128, 1024)

        val available = total - responseReserve - framingReserve

        val toolBudget = if (policy.includeAvailableTools) (available * 0.20).toInt().coerceIn(256, 2048) else 0
        val explicitContextBudget = (available * 0.25).toInt().coerceIn(256, 3072)
        val memoryBudget = if (policy.includeMemories) (available * 0.15).toInt().coerceIn(128, 1500) else 0
        val conversationBudget = (available - toolBudget - explicitContextBudget - memoryBudget).coerceAtLeast(512)

        return CognitiveContextPlan(
            totalTokens = total,
            responseReserveTokens = responseReserve,
            framingReserveTokens = framingReserve,
            toolBudgetTokens = toolBudget,
            explicitContextBudgetTokens = explicitContextBudget,
            memoryBudgetTokens = memoryBudget,
            conversationBudgetTokens = conversationBudget,
        )
    }
}
