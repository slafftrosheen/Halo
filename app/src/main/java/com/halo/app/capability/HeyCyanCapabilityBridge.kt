package com.halo.app.capability

import com.halo.drivers.heycyan.capability.CapabilityProvider
import com.halo.drivers.heycyan.capability.CapabilityRequest
import com.halo.drivers.heycyan.capability.CapabilityOutcome
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task

/**
 * Bridges HeyCyan driver capabilities into the Halo Runtime capability registry.
 */
class HeyCyanCapabilityBridge(
    private val provider: CapabilityProvider
) : Capability {
    override val id: String = "capability.heycyan"

    override suspend fun supports(task: Task): Boolean {
        if (task !is DeviceTask) return false
        val capabilityId = taskToAction(task.action) ?: return false
        return provider.capabilities().any { it.id == capabilityId }
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is DeviceTask)
        val capabilityId = taskToAction(task.action) 
            ?: return Outcome.PermanentFailure("Unsupported HeyCyan action: ${task.action}")

        return when (val result = provider.execute(capabilityId, CapabilityRequest.Empty)) {
            is CapabilityOutcome.Success -> Outcome.Success("HeyCyan ${task.action} executed")
            is CapabilityOutcome.Failure -> Outcome.PermanentFailure(result.message)
        }
    }

    private fun taskToAction(action: String): String? = when(action.lowercase()) {
        "takephoto", "photo" -> "heycyan.camera.photo"
        "startvideo", "video" -> "heycyan.camera.video"
        "startaudio", "audio" -> "heycyan.audio.recording"
        "battery" -> "heycyan.battery"
        else -> null
    }
}
