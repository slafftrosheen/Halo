package com.halo.app.di.tools.host

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.halo.host.android.capability.AndroidDeviceCapability
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.runtime.cognition.tools.ToolFailureKind
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.runtime.cognition.tools.ToolResultEnvelope
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import kotlin.math.roundToInt

class DeviceTools(private val context: Context) : ToolExecutor {
    private val device = AndroidDeviceCapability(context)

    override fun definitions(): List<ToolDefinition> = buildList {
        add(
            ToolDefinition(
                "halo_volume", "halo_volume", "Change media volume by one step.",
                mapOf("type" to "object", "properties" to mapOf("direction" to mapOf("type" to "string", "enum" to listOf("up", "down"))), "required" to listOf("direction"), "additionalProperties" to false)
            )
        )
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        ) {
            add(
                ToolDefinition(
                    "halo_flashlight", "halo_flashlight", "Set phone flashlight state.",
                    mapOf("type" to "object", "properties" to mapOf("state" to mapOf("type" to "string", "enum" to listOf("on", "off"))), "required" to listOf("state"), "additionalProperties" to false)
                )
            )
        }
        // RUNTIME-056: sound mode is a device control a user asks for by name ("silence my phone").
        add(
            ToolDefinition(
                id = "halo_ringer",
                name = "halo_ringer",
                description = "Set the phone sound mode: normal (ring), vibrate, or silent. Silent requires Do Not Disturb " +
                    "access on most devices and is reported as unavailable when Android refuses it.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf("mode" to mapOf("type" to "string", "enum" to listOf("normal", "vibrate", "silent"))),
                    "required" to listOf("mode"),
                    "additionalProperties" to false,
                ),
            )
        )
        // RUNTIME-056: brightness is only offered as available once the user has granted
        // the Modify system settings special access; until then it stays advertised and
        // reports the exact setup step instead of pretending to work.
        add(
            ToolDefinition(
                id = "halo_brightness",
                name = "halo_brightness",
                description = "Set the screen brightness to a percentage. Requires the user to allow Halo to modify system " +
                    "settings; until then this action reports PERMISSION_REQUIRED.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "level" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 100),
                    ),
                    "required" to listOf("level"),
                    "additionalProperties" to false,
                ),
            )
        )
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)

        fun rejectedArg(message: String) = reject(message)

        return when (toolCall.functionName) {
            "halo_volume" -> {
                val direction = toolCall.arguments["direction"] as? String ?: return rejectedArg("Missing direction")
                device.execute(DeviceTask(action = "volume_$direction"))
                receipt(toolCall, HostActionReceipt.CONFIRMED, "direction=$direction")
            }

            "halo_flashlight" -> {
                val state = toolCall.arguments["state"] as? String ?: return rejectedArg("Missing state")
                device.execute(DeviceTask(action = "flashlight_$state"))
                receipt(toolCall, HostActionReceipt.CONFIRMED, "state=$state")
            }

            "halo_ringer" -> setRingerMode(toolCall)

            "halo_brightness" -> setBrightness(toolCall)

            else -> reject("Unknown device tool", ToolRejectionKind.TOOL_UNKNOWN)
        }
    }

    /**
     * The platform is the evidence: after setting a sound mode the current mode is read
     * back, so a partial success (for example vibrate granted where silent was refused)
     * is reported as DISPATCHED rather than confirmed.
     */
    private fun setRingerMode(toolCall: ToolCall): ToolExecutionResult {
        val mode = toolCall.arguments["mode"] as? String
            ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Missing mode", ToolRejectionKind.INVALID_ARGUMENTS)
        if (mode !in RINGER_MODES) {
            return ToolExecutionResult.Rejected(
                toolCall.id, toolCall.functionName,
                "Unsupported mode; use ${RINGER_MODES.joinToString(", ")}",
                ToolRejectionKind.INVALID_ARGUMENTS,
            )
        }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = "This device exposes no audio service",
                failureKind = ToolFailureKind.UNAVAILABLE,
                code = "AUDIO_UNAVAILABLE",
            )
        val target = when (mode) {
            "normal" -> AudioManager.RINGER_MODE_NORMAL
            "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
            else -> AudioManager.RINGER_MODE_SILENT
        }
        val refused = runCatching { audio.ringerMode = target }.exceptionOrNull()
        if (refused != null) {
            val needsPolicyAccess = mode == "silent" && !runCatching { audio.isVolumeFixed }.getOrDefault(false)
            return ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = "Android refused the $mode sound mode: ${refused.message ?: "no reason given"}",
                failureKind = ToolFailureKind.PERMISSION,
                code = if (needsPolicyAccess) "DND_ACCESS_REQUIRED" else "RINGER_REFUSED",
                hint = "Ask the user to allow Halo Do Not Disturb access in Android sound settings, then try again.",
            )
        }
        val current = runCatching { audio.ringerMode }.getOrNull()
        val confirmed = current == target
        return receipt(
            toolCall,
            if (confirmed) HostActionReceipt.CONFIRMED else HostActionReceipt.DISPATCHED,
            buildString {
                append("requested=").append(mode)
                append(" observed=").append(current?.let(::ringerModeLabel) ?: "unknown")
            },
        )
    }

    /**
     * Writing a system setting is a privileged operation. When the user has not granted
     * it, the failure names the exact grant path instead of a generic permission error.
     */
    private fun setBrightness(toolCall: ToolCall): ToolExecutionResult {
        val level = (toolCall.arguments["level"] as? Number)?.toInt()
            ?: return ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Missing level", ToolRejectionKind.INVALID_ARGUMENTS)
        if (level !in 1..100) {
            return ToolExecutionResult.Rejected(
                toolCall.id, toolCall.functionName,
                "Brightness must be between 1 and 100",
                ToolRejectionKind.INVALID_ARGUMENTS,
            )
        }
        if (!Settings.System.canWrite(context)) {
            return ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = "Halo is not allowed to modify system settings, so it cannot set screen brightness",
                failureKind = ToolFailureKind.PERMISSION,
                code = "WRITE_SETTINGS_REQUIRED",
                hint = "Ask the user to allow \"Modify system settings\" for Halo in Android settings. " +
                    "The user can also reach it with halo_open_settings section=write_settings.",
            )
        }
        val value = (level.coerceIn(1, 100) / 100.0 * BRIGHTNESS_MAX).roundToInt().coerceIn(MIN_BRIGHTNESS_VALUE, BRIGHTNESS_MAX)
        val wrote = runCatching {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        }.getOrDefault(false)
        if (!wrote) {
            return ToolExecutionResult.Failed(
                toolCallId = toolCall.id,
                toolId = toolCall.functionName,
                message = "Android rejected the brightness write",
                failureKind = ToolFailureKind.PERMISSION,
                code = "WRITE_SETTINGS_REFUSED",
            )
        }
        val observed = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull()?.let { it * 100 / BRIGHTNESS_MAX }
        return receipt(
            toolCall,
            if (observed == level) HostActionReceipt.CONFIRMED else HostActionReceipt.DISPATCHED,
            "requested_percent=$level observed_percent=${observed ?: "unknown"}",
        )
    }

    private fun receipt(toolCall: ToolCall, receipt: HostActionReceipt, details: String) =
        ToolExecutionResult.Success(
            toolCallId = toolCall.id,
            toolId = toolCall.functionName,
            output = ToolResultEnvelope.success(actionTruth = receipt.toActionTruth(), details = details),
            actionTruth = receipt.toActionTruth(),
        )

    private fun ringerModeLabel(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> "normal"
        AudioManager.RINGER_MODE_VIBRATE -> "vibrate"
        AudioManager.RINGER_MODE_SILENT -> "silent"
        else -> "unknown"
    }

    /** Exposed so the settings screen can offer the exact grant step next to the ability. */
    fun writeSettingsGrantIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        val RINGER_MODES = listOf("normal", "vibrate", "silent")
        const val BRIGHTNESS_MAX = 255
        const val MIN_BRIGHTNESS_VALUE = 1
    }
}
