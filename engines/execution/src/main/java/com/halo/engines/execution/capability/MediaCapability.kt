package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.Outcome

class MediaCapability : Capability {
    override val id: String = "capability.media"

    override suspend fun supports(task: Task): Boolean {
        return task is MediaTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is MediaTask)
        // Simulated transactional execution
        println("MediaCapability: Prepare: Check if media app is installed and licensed")
        println("MediaCapability: Execute: Play ${task.mediaId}")
        println("MediaCapability: Verify: Ensure playback started")
        return Outcome.Success("Media playing")
    }
}
