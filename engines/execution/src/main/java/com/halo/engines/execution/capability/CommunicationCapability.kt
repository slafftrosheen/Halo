package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.CommunicationTask
import com.halo.runtime.cognition.Outcome

class CommunicationCapability : Capability {
    override val id: String = "capability.communication"

    override suspend fun supports(task: Task): Boolean {
        return task is CommunicationTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is CommunicationTask)
        return Outcome.Success("Sent message to ${task.targetId}")
    }
}
