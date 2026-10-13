package com.halo.app.di.tools.host

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class MediaTools(
    private val context: Context,
    private val activeSessions: () -> List<android.media.session.MediaController> = {
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        manager.getActiveSessions(android.content.ComponentName(context, com.halo.app.notification.HaloNotificationListenerService::class.java))
    },
) : ToolExecutor {

    fun hasSession(packageName: String): Boolean = runCatching {
        activeSessions().count { it.packageName == packageName } == 1
    }.getOrDefault(false)
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
                        "targetApp" to mapOf("type" to "string", "maxLength" to 150),
                        "positionMs" to mapOf("type" to "integer", "minimum" to 0),
                        "targetPackage" to mapOf("type" to "string", "maxLength" to 150),
                        "action" to mapOf(
                            "type" to "string",
                            "enum" to listOf("play", "pause", "next", "previous", "seek")
                        )
                    ),
                    "required" to listOf("action"),
                    "additionalProperties" to false
                )
            )
        )
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: com.halo.runtime.cognition.tools.ToolRejectionKind = com.halo.runtime.cognition.tools.ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (toolCall.functionName !in setOf("halo_media_status", "halo_media_control")) return reject("Unknown tool", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
        if (toolCall.functionName == "halo_media_control" && toolCall.arguments["action"] !in setOf("play", "pause", "next", "previous", "seek")) return reject("Missing or invalid action")
        val seekPosition = (toolCall.arguments["positionMs"] as? Number)?.toLong()
        if (toolCall.arguments["action"] == "seek" && (seekPosition == null || seekPosition < 0 ||
                (toolCall.arguments["positionMs"] as Number).toDouble() != seekPosition.toDouble())) return reject("Specify an exact nonnegative positionMs")
        if (toolCall.arguments["action"] != "seek" && "positionMs" in toolCall.arguments) return reject("positionMs is only for seek")

        
        return try {
            val controllers = activeSessions()
            if (controllers.isEmpty()) {
                return reject("No active media sessions", com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            }
            val requestedPackage = toolCall.arguments["targetPackage"] as? String
            val requestedApp = toolCall.arguments["targetApp"] as? String
            val matches = controllers.filter { controller ->
                when {
                    requestedPackage != null -> controller.packageName == requestedPackage
                    requestedApp != null -> controller.packageName.equals(requestedApp, true) || runCatching {
                        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(controller.packageName, 0)).toString().equals(requestedApp, true)
                    }.getOrDefault(false)
                    else -> true
                }
            }
            // Android can retain multiple live sessions. Never send a command to
            // whichever controller the OS happens to enumerate first.
            if (requestedPackage != null && requestedApp != null)
                return reject("Specify either targetPackage or targetApp, not both")
            val active = if (requestedPackage != null || requestedApp != null) matches.singleOrNull()
                ?: return reject("No unambiguous active media session for the requested app",
                    com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                else {
                    val playing = controllers.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    when {
                        playing.size == 1 -> playing.single()
                        controllers.size == 1 -> controllers.single()
                        else -> return reject("Multiple media sessions. Name the app to control.",
                            com.halo.runtime.cognition.tools.ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                    }
                }

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
                        "seek" -> {
                            if ((active.playbackState?.actions ?: 0) and PlaybackState.ACTION_SEEK_TO == 0L)
                                return reject("The selected player does not expose seeking", com.halo.runtime.cognition.tools.ToolRejectionKind.UNSUPPORTED_OPERATION)
                            val duration = active.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
                            if (duration > 0 && seekPosition!! > duration) return reject("Seek position exceeds track duration")
                            transportControls.seekTo(seekPosition!!)
                        }
                        else -> return reject("Unsupported action")
                    }
                    ToolExecutionResult.Success(toolCall.id, toolCall.functionName, "Media command dispatched", com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
                }
                else -> reject("Unknown tool", com.halo.runtime.cognition.tools.ToolRejectionKind.TOOL_UNKNOWN)
            }
        } catch (_: SecurityException) {
            reject("Notification access is required for media sessions", com.halo.runtime.cognition.tools.ToolRejectionKind.PERMISSION_REQUIRED)
        } catch (_: android.os.DeadObjectException) {
            reject("Media session disappeared", com.halo.runtime.cognition.tools.ToolRejectionKind.RESOURCE_UNAVAILABLE)
        }
    }
}
