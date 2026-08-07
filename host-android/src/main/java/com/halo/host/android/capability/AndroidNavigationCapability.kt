package com.halo.host.android.capability

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome

class AndroidNavigationCapability(private val context: Context) : Capability {
    override val id: String = "capability.navigation.android"

    override suspend fun supports(task: Task): Boolean {
        return task is NavigationTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is NavigationTask)
        return try {
            val gmmIntentUri = Uri.parse("geo:0,0?q=${Uri.encode(task.coordinates)}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                Outcome.Success("Navigating to ${task.coordinates}")
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(task.coordinates)}"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(webIntent)
                Outcome.Success("Navigating to ${task.coordinates} via web")
            }
        } catch (e: android.content.ActivityNotFoundException) {
            Outcome.PermanentFailure("Navigation app not found")
        } catch (e: SecurityException) {
            Outcome.PermanentFailure("Permission denied to launch navigation")
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to execute navigation action")
        }
    }
}
