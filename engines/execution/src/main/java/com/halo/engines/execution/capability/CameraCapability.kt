package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome

import com.halo.sdk.runtime.embodiment.EmbodimentGateway
import com.halo.sdk.runtime.embodiment.EmbodimentIntent
import com.halo.sdk.runtime.embodiment.IntentKind
import java.util.UUID

class CameraCapability(private val gateway: EmbodimentGateway) : Capability {
    override val id: String = "capability.camera"

    override suspend fun supports(task: Task): Boolean {
        return task is DeviceTask && task.action in listOf("CAPTURE_PHOTO", "START_VIDEO", "STOP_VIDEO")
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is DeviceTask)
        
        val intent = when (task.action) {
            "CAPTURE_PHOTO" -> EmbodimentIntent(intentId = UUID.randomUUID().toString(), kind = IntentKind.CAPTURE_PHOTO)
            "START_VIDEO" -> EmbodimentIntent(intentId = UUID.randomUUID().toString(), kind = IntentKind.START_VIDEO)
            "STOP_VIDEO" -> EmbodimentIntent(intentId = UUID.randomUUID().toString(), kind = IntentKind.STOP_VIDEO)
            else -> return Outcome.PermanentFailure("Unknown camera action")
        }

        gateway.dispatch(intent)
        return Outcome.Success("Executed camera action ${task.action}")
    }
}
