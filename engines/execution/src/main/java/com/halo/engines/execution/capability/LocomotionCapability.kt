package com.halo.engines.execution.capability

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.Outcome

import com.halo.sdk.runtime.embodiment.EmbodimentGateway
import com.halo.sdk.runtime.embodiment.EmbodimentIntent
import com.halo.sdk.runtime.embodiment.IntentKind
import com.halo.sdk.runtime.embodiment.DriveMode
import java.util.UUID

class LocomotionCapability(private val gateway: EmbodimentGateway) : Capability {
    override val id: String = "capability.locomotion"

    override suspend fun supports(task: Task): Boolean {
        return task is DeviceTask && task.action in listOf("MOVE", "STOP", "MANUAL_VECTOR")
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is DeviceTask)
        
        val intent = when (task.action) {
            "MOVE" -> EmbodimentIntent(intentId = UUID.randomUUID().toString(), kind = IntentKind.MOVE, driveMode = DriveMode.FORWARD)
            "STOP" -> EmbodimentIntent(intentId = UUID.randomUUID().toString(), kind = IntentKind.STOP)
            "MANUAL_VECTOR" -> {
                val x = task.parameters["vectorX"]?.toString()?.toFloatOrNull() ?: 0f
                val y = task.parameters["vectorY"]?.toString()?.toFloatOrNull() ?: 0f
                EmbodimentIntent(intentId = UUID.randomUUID().toString(), kind = IntentKind.MOVE, driveMode = DriveMode.MANUAL_VECTOR, vectorX = x, vectorY = y)
            }
            else -> return Outcome.PermanentFailure("Unknown locomotion action")
        }

        gateway.dispatch(intent)
        return Outcome.Success("Executed locomotion action ${task.action}")
    }
}
