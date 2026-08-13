package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome

class RangeSensingCapability : Capability {
    override val id: String = "capability.range_sensing"

    override suspend fun supports(task: Task): Boolean {
        // Range sensing might be mostly passive, but we could request a scan
        return task is DeviceTask && task.action == "SCAN_RANGE"
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is DeviceTask)
        return Outcome.Success("Executed range sensing action ${task.action}")
    }
}
