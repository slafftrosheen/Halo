package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.TaskResult

class ApplicationCapability : Capability {
    override val id: String = "capability.application"

    override suspend fun supports(task: Task): Boolean {
        return task is ApplicationTask
    }

    override suspend fun execute(task: Task): TaskResult {
        require(task is ApplicationTask)
        return TaskResult.Success("Launched ${task.packageName}")
    }
}
