package com.halo.app.di.tools.host

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.halo.host.android.capability.AndroidMediaCapability
import com.halo.runtime.cognition.MediaTask
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class MediaTools(
    private val context: Context
) : ToolExecutor {

    override fun definitions(): List<ToolDefinition> = buildList {
        add(
            ToolDefinition(
                id = "halo_media_status",
                name = "halo_media_status",
                description = "Get the current media playback status, including what is currently playing.",
                parameters = emptyMap()
            )
        )
        add(
            ToolDefinition(
                id = "halo_media_control",
                name = "halo_media_control",
                description = "Send play, pause, next, or previous to the active media session.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "action" to mapOf(
                            "type" to "string",
                            "enum" to listOf("play", "pause", "next", "previous")
                        )
                    ),
                    "required" to listOf("action"),
                    "additionalProperties" to false
                )
            )
        )
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        val mediaManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        
        // Note: Getting active sessions requires android.permission.MEDIA_CONTENT_CONTROL or NotificationListenerService access.
        // We will try our best, otherwise we fallback to AudioManager key dispatch.
        // Actually, we can use AndroidMediaCapability as a fallback if we don't have access.
        // For now, let's keep it simple and just do the right thing if we have access.
        
        return try {
            val controllers = mediaManager.getActiveSessions(android.content.ComponentName(context, com.halo.app.notification.HaloNotificationListenerService::class.java))
            if (controllers.isEmpty()) {
                return reject("No active media sessions")
            }
            val active = controllers.first()

            when (toolCall.functionName) {
                "halo_media_status" -> {
                    val metadata = active.metadata
                    val state = active.playbackState?.state
                    val isPlaying = state == PlaybackState.STATE_PLAYING
                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    val info = "Playing: $isPlaying, Title: ${title ?: "Unknown"}, Artist: ${artist ?: "Unknown"}"
                    ToolExecutionResult.Success(toolCall.id, toolCall.functionName, info)
                }
                "halo_media_control" -> {
                    val action = toolCall.arguments["action"] as? String ?: return reject("Missing action")
                    val transportControls = active.transportControls
                    when (action) {
                        "play" -> transportControls.play()
                        "pause" -> transportControls.pause()
                        "next" -> transportControls.skipToNext()
                        "previous" -> transportControls.skipToPrevious()
                        else -> return reject("Unsupported action")
                    }
                    ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Media command dispatched", com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
                }
                else -> reject("Unknown tool")
            }
        } catch (_: SecurityException) {
            // Fallback to old behavior if no notification listener permission
            val apps = AndroidMediaCapability(context)
            if (toolCall.functionName == "halo_media_control") {
                val action = toolCall.arguments["action"] as? String ?: return reject("Missing action")
                apps.execute(MediaTask(mediaAction = action, mediaId = ""))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Media command dispatched", com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
            } else {
                reject("Permission denied to read media status")
            }
        }
    }
}
