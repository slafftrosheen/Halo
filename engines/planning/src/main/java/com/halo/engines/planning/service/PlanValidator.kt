package com.halo.engines.planning.service

import com.halo.runtime.cognition.ExecutionPlan
import com.halo.runtime.cognition.Task

class PlanValidator {
    fun validate(plan: ExecutionPlan): Boolean {
        // Placeholder for validating circular dependencies, missing preconditions, etc.
        val visited = mutableSetOf<Task>()
        val recursionStack = mutableSetOf<Task>()
        
        return !hasCycle(plan.rootTask, visited, recursionStack)
    }

    private fun hasCycle(task: Task, visited: MutableSet<Task>, recursionStack: MutableSet<Task>): Boolean {
        if (recursionStack.contains(task)) return true
        if (visited.contains(task)) return false

        visited.add(task)
        recursionStack.add(task)

        for (dep in task.dependencies) {
            if (hasCycle(dep, visited, recursionStack)) {
                return true
            }
        }

        recursionStack.remove(task)
        return false
    }
}
