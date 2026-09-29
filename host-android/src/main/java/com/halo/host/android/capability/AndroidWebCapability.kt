package com.halo.host.android.capability

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.WebTask

class AndroidWebCapability(private val context: Context) : Capability {
    override val id: String = "capability.web.android"

    override suspend fun supports(task: Task): Boolean = task is WebTask

    override suspend fun execute(task: Task): Outcome {
        require(task is WebTask)
        val target = task.url.trim()
        if (target.isEmpty()) {
            return Outcome.PermanentFailure("No web search query was provided")
        }

        return try {
            val intent = if (target.startsWith("http://", ignoreCase = true) ||
                target.startsWith("https://", ignoreCase = true)
            ) {
                Intent(Intent.ACTION_VIEW, Uri.parse(target))
            } else {
                Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, target)
                }
            }.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Outcome.Dispatched("Web request sent", "Browser")
        } catch (_: ActivityNotFoundException) {
            Outcome.PermanentFailure("No web browser or search app is available")
        } catch (_: SecurityException) {
            Outcome.PermanentFailure("Android blocked the web request")
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to open web search")
        }
    }
}
