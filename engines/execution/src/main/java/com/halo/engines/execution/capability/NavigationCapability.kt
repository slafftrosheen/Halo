package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome

class NavigationCapability : Capability {
    override val id: String = "capability.navigation"

    override suspend fun supports(task: Task): Boolean {
        return task is NavigationTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is NavigationTask)
        return Outcome.Success("Navigating to ${task.coordinates}")
    }
}
