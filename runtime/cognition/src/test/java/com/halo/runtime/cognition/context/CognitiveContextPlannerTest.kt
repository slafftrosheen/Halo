package com.halo.runtime.cognition.context

import com.halo.sdk.provider.AiContextPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CognitiveContextPlannerTest {

    @Test
    fun `context planner computes valid token layer budgets`() {
        val planner = CognitiveContextPlanner()
        val policy = AiContextPolicy(includeAvailableTools = true, includeMemories = true)

        val plan = planner.computePlan(
            modelContextWindowTokens = 8192,
            mode = CognitiveOperatingMode.AUTO,
            policy = policy
        )

        assertEquals(8192, plan.totalTokens)
        assertTrue(plan.responseReserveTokens in 256..2048)
        assertTrue(plan.framingReserveTokens in 128..1024)
        assertTrue(plan.toolBudgetTokens > 0)
        assertTrue(plan.memoryBudgetTokens > 0)
        assertTrue(plan.conversationBudgetTokens >= 512)

        val sum = plan.responseReserveTokens + plan.framingReserveTokens +
            plan.toolBudgetTokens + plan.explicitContextBudgetTokens +
            plan.memoryBudgetTokens + plan.conversationBudgetTokens

        assertTrue("Layer budgets sum $sum must not exceed total window ${plan.totalTokens}", sum <= plan.totalTokens)
    }
}
