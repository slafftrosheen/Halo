package com.halo.app.di.tools.host

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.halo.host.android.capability.AndroidDeviceCapability
import com.halo.runtime.cognition.DeviceTask
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

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
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        return when (toolCall.functionName) {
            "halo_volume" -> {
                val direction = toolCall.arguments["direction"] as? String ?: return reject("Missing direction")
                device.execute(DeviceTask(action = "volume_$direction"))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.CONFIRMED.name)
            }
            "halo_flashlight" -> {
                val state = toolCall.arguments["state"] as? String ?: return reject("Missing state")
                device.execute(DeviceTask(action = "flashlight_$state"))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.CONFIRMED.name)
            }
            else -> reject("Unknown device tool")
        }
    }
}
