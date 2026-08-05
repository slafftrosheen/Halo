package com.halo.runtime.kernel.service

import com.halo.runtime.kernel.CognitiveBudget

class BudgetManager {
    private val budgets = mutableMapOf<String, CognitiveBudget>()

    fun allocate(agentId: String, budget: CognitiveBudget) {
        budgets[agentId] = budget
    }

    fun getBudget(agentId: String): CognitiveBudget? {
        return budgets[agentId]
    }

    fun updateBudget(agentId: String, budget: CognitiveBudget) {
        budgets[agentId] = budget
    }
}
