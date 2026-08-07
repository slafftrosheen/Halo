package com.halo.host.android.capability

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.Task
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.Outcome

class AndroidMediaCapability(private val context: Context) : Capability {
    override val id: String = "capability.media.android"

    override suspend fun supports(task: Task): Boolean {
        return task is MediaTask
    }

    override suspend fun execute(task: Task): Outcome {
        require(task is MediaTask)
        return try {
            // Very rudimentary mock for alpha
            val intent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(intent)
                Outcome.Success("Opened Spotify for ${task.mediaId}")
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${Uri.encode(task.mediaId)}"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(webIntent)
                Outcome.Success("Opened Spotify Web for ${task.mediaId}")
            }
        } catch (e: android.content.ActivityNotFoundException) {
            Outcome.PermanentFailure("Media app not found")
        } catch (e: SecurityException) {
            Outcome.PermanentFailure("Permission denied to launch media app")
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to execute media action")
        }
    }
}
