package com.halo.host.android.capability

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.MediaStore
import android.view.KeyEvent
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
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            when (task.mediaAction.lowercase()) {
                "play" -> {
                    val query = task.mediaId
                    if (query.isBlank() || query.equals("some music", ignoreCase = true) || query.equals("music", ignoreCase = true)) {
                        // Generic play command
                        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                        audioManager.dispatchMediaKeyEvent(event)
                        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                        Outcome.Success("Resumed media playback")
                    } else {
                        // Search and play specific media
                        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                        intent.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE)
                        intent.putExtra("query", query)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        
                        try {
                            context.startActivity(intent)
                            Outcome.Success("Requested playback for $query")
                        } catch (e: ActivityNotFoundException) {
                            // Fallback to media key event if no search activity handled it directly
                            val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                            audioManager.dispatchMediaKeyEvent(event)
                            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                            Outcome.Success("Triggered media playback for $query")
                        }
                    }
                }
                "pause", "stop" -> {
                    val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
                    audioManager.dispatchMediaKeyEvent(event)
                    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
                    Outcome.Success("Paused media playback")
                }
                "next" -> {
                    val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
                    audioManager.dispatchMediaKeyEvent(event)
                    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT))
                    Outcome.Success("Skipped to next track")
                }
                "previous" -> {
                    val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    audioManager.dispatchMediaKeyEvent(event)
                    audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                    Outcome.Success("Skipped to previous track")
                }
                else -> Outcome.PermanentFailure("Unsupported media action: ${task.mediaAction}")
            }
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to execute media action")
        }
    }
}
