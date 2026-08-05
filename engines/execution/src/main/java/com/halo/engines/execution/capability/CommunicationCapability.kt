package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.TaskResult

class CommunicationCapability : Capability {
    override val id: String = "capability.communication"

    override suspend fun supports(task: Task): Boolean {
        return task is CommunicationTask
    }

    override suspend fun execute(task: Task): TaskResult {
        require(task is CommunicationTask)
        return TaskResult.Success("Sent message to ${task.targetId}")
    }
}
