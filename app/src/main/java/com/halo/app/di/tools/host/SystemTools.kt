package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class SystemTools(private val context: Context) : ToolExecutor {
    private val sections = mapOf(
        "general" to Settings.ACTION_SETTINGS, "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS, "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "app" to Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "notifications" to Settings.ACTION_APP_NOTIFICATION_SETTINGS
    ).toMutableMap().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            put("exact_alarms", Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        }
    }

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            id = "halo_open_settings",
            name = "halo_open_settings",
            description = "Open a specific Android settings page.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("section" to mapOf("type" to "string", "enum" to sections.keys.toList())),
                "required" to emptyList<String>(),
                "additionalProperties" to false
            )
        ),
        ToolDefinition(
            id = "halo_share_text",
            name = "halo_share_text",
            description = "Open the Android share sheet; user chooses the recipient and confirms sending.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf("text" to mapOf("type" to "string", "maxLength" to 2000)),
                "required" to listOf("text"),
                "additionalProperties" to false
            )
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        return when (toolCall.functionName) {
            "halo_open_settings" -> {
                val section = toolCall.arguments["section"] as? String
                val action = sections[section] ?: Settings.ACTION_SETTINGS
                context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.DISPATCHED.name)
            }
            "halo_share_text" -> {
                val text = toolCall.arguments["text"] as? String ?: return reject("Missing text")
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.DISPATCHED.name)
            }
            else -> reject("Unknown tool")
        }
    }
}
