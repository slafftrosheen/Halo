package com.halo.host.android.capability

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
        return try {
            val urlStr = if (task.url.startsWith("http://") || task.url.startsWith("https://")) {
                task.url
            } else {
                "https://www.google.com/search?q=${Uri.encode(task.url)}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Outcome.Success(task.url)
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to open web search")
        }
    }
}
