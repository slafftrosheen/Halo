package com.halo.app.capability

import com.halo.drivers.heycyan.capability.CapabilityProvider
import com.halo.drivers.heycyan.capability.CapabilityRequest
import com.halo.drivers.heycyan.capability.CapabilityOutcome
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import kotlinx.coroutines.flow.first

/**
 * Bridges HeyCyan driver capabilities into the Halo Runtime capability registry.
 */
class HeyCyanCapabilityBridge(
    private val provider: CapabilityProvider,
    private val deviceState: kotlinx.coroutines.flow.StateFlow<com.halo.drivers.heycyan.protocol.HeyCyanDeviceState>? = null
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

        val result = provider.execute(capabilityId, CapabilityRequest.Empty)
        
        return when (result) {
            is CapabilityOutcome.Success -> {
                // For Photo, wait for BLE success confirmation if we have state access
                if (capabilityId == "heycyan.camera.photo" && deviceState != null) {
                    try {
                        kotlinx.coroutines.withTimeout(5000L) {
                            // Wait for the mode to briefly switch to PHOTO and then back, or wait for an update.
                            // Currently, HeyCyanDeviceState exposes mode. We wait until mode == PHOTO, or we just delay briefly.
                            // Actually, a more robust way is to wait for the next ModeChanged event or state update 
                            // indicating WorkType completion. For now, assuming the command was sent successfully,
                            // we wait for the state machine to acknowledge it.
                            deviceState.first { it.mode == com.halo.drivers.heycyan.protocol.HeyCyanMode.PHOTO }
                        }
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        // Timeout waiting for device confirmation
                        return Outcome.PermanentFailure("Device did not confirm photo execution in time")
                    }
                }
                Outcome.Success("HeyCyan ${task.action} executed")
            }
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
