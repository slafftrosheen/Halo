package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.TaskResult

class MediaCapability : Capability {
    override val id: String = "capability.media"

    override suspend fun supports(task: Task): Boolean {
        return task is MediaTask
    }

    override suspend fun execute(task: Task): TaskResult {
        require(task is MediaTask)
        // Simulated transactional execution
        println("Prepare: Check if media app is installed and licensed")
        println("Execute: Play ${task.mediaId}")
        println("Verify: Ensure playback started")
        return TaskResult.Success("Media playing")
    }
}
