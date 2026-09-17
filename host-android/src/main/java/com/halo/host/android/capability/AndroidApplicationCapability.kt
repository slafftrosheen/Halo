package com.halo.host.android.capability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.halo.host.android.resolver.AndroidApplicationResolver
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.Outcome

class AndroidApplicationCapability(
    private val context: Context,
    private val appResolver: AndroidApplicationResolver = AndroidApplicationResolver(context)
) : Capability {
    override val id: String = "capability.application.android"

    override suspend fun supports(task: Task): Boolean {
        return task is ApplicationTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is ApplicationTask)
        return try {
            val resolved = appResolver.resolve(task.packageName)
            val pkgToLaunch = resolved?.packageName ?: task.packageName
            val label = resolved?.label ?: task.packageName

            val intent = context.packageManager.getLaunchIntentForPackage(pkgToLaunch)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
                Outcome.Success(label)
            } else {
                Outcome.PermanentFailure("Application $label not found")
            }
        } catch (e: ActivityNotFoundException) {
            Outcome.PermanentFailure("Application ${task.packageName} not installed")
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to launch application")
        }
    }
}
