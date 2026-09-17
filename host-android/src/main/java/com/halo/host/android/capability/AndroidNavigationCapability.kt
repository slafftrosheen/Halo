package com.halo.host.android.capability

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task

class AndroidNavigationCapability(private val context: Context) : Capability {
    override val id: String = "capability.navigation.android"

    override suspend fun supports(task: Task): Boolean {
        return task is NavigationTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is NavigationTask)
        val destination = task.coordinates.trim()
        if (destination.isEmpty()) {
            return Outcome.PermanentFailure("No navigation destination was provided")
        }

        return try {
            val mapIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(mapIntent)
            Outcome.Success("Opened map for $destination")
        } catch (_: ActivityNotFoundException) {
            try {
                val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, "$destination map")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(searchIntent)
                Outcome.Success("Searched for $destination")
            } catch (_: ActivityNotFoundException) {
                Outcome.PermanentFailure("No navigation or web search app is available")
            }
        } catch (_: SecurityException) {
            Outcome.PermanentFailure("Android blocked the navigation request")
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to execute navigation action")
        }
    }
}
