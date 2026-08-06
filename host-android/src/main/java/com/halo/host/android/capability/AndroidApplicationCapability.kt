package com.halo.host.android.capability

import android.content.Context
import android.content.Intent
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.Outcome

class AndroidApplicationCapability(private val context: Context) : Capability {
    override val id: String = "capability.application.android"

    override suspend fun supports(task: Task): Boolean {
        return task is ApplicationTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is ApplicationTask)
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(task.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Outcome.Success("Launched ${task.packageName}")
            } else {
                Outcome.Failure("Application ${task.packageName} not found")
            }
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to launch application")
        }
    }
}
