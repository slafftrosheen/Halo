package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.TaskResult

class DeviceCapability : Capability {
    override val id: String = "capability.device"

    override suspend fun supports(task: Task): Boolean {
        return task is DeviceTask
    }

    override suspend fun execute(task: Task): TaskResult {
        require(task is DeviceTask)
        return TaskResult.Success("Executed device action ${task.action}")
    }
}
