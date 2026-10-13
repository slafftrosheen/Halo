package com.halo.host.android.capability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.halo.host.android.resolver.AndroidApplicationResolver
import com.halo.host.android.resolver.ApplicationResolution
import com.halo.runtime.cognition.ActionFailureCode
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task

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
            val query = task.packageName.ifBlank { task.displayName }
            when (val resolution = appResolver.resolveApplication(query)) {
                is ApplicationResolution.Resolved -> {
                    val app = resolution.app
                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        context.startActivity(intent)
                        Outcome.Dispatched("Application launch requested", app.label)
                    } else {
                        Outcome.PermanentFailure(
                            reason = "Application ${app.label} is not launchable",
                            code = ActionFailureCode.APP_NOT_LAUNCHABLE
                        )
                    }
                }
                is ApplicationResolution.Ambiguous -> {
                    val candidateNames = resolution.candidates.take(3).joinToString(", ") { it.label }
                    Outcome.NeedsClarification(
                        prompt = "Please specify the exact app or package. Matching apps: $candidateNames.",
                        field = "packageName"
                    )
                }
                ApplicationResolution.VisibilityUnavailable -> Outcome.PermanentFailure(
                    "Android app visibility is unavailable; open Halo and check Android access.",
                    code = ActionFailureCode.APP_VISIBILITY_UNAVAILABLE)
                ApplicationResolution.NotFound -> {
                    Outcome.PermanentFailure(
                        reason = "I couldn't find a launchable app matching '$query'",
                        code = ActionFailureCode.APP_NOT_FOUND
                    )
                }
            }
        } catch (e: ActivityNotFoundException) {
            Outcome.PermanentFailure(
                reason = "Application ${task.packageName} is not installed",
                error = e,
                code = ActionFailureCode.APP_NOT_FOUND
            )
        } catch (e: Exception) {
            Outcome.RetryableFailure(
                reason = e.message ?: "Failed to launch application",
                error = e,
                code = ActionFailureCode.UNKNOWN
            )
        }
    }
}
