package com.halo.app.di

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import com.halo.host.android.capability.*
import com.halo.runtime.cognition.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*

/** A closed set of reversible host actions, executed through existing capabilities. */
class AndroidCapabilityTools(private val context: Context, private val events: EventBus,
    private val isForeground: () -> Boolean = { true }) : ToolExecutor {
    private val device = AndroidDeviceCapability(context)
    private val apps = AndroidApplicationCapability(context)
    private val navigation = AndroidNavigationCapability(context)
    private val web = AndroidWebCapability(context)
    private val media = AndroidMediaCapability(context)
    private fun resolves(intent: Intent) = intent.resolveActivity(context.packageManager) != null
    override fun definitions(): List<ToolDefinition> = buildList {
        add(ToolDefinition("halo_list_apps", "halo_list_apps", "Find launchable installed apps by name and return package IDs for halo_open_app.",
            mapOf("type" to "object", "properties" to mapOf("query" to mapOf("type" to "string", "maxLength" to 100)),
                "required" to listOf("query"), "additionalProperties" to false)))
        fun tool(name: String, description: String, key: String, values: List<String>? = null) {
            val property = mutableMapOf<String, Any>("type" to "string", "minLength" to 1, "maxLength" to 500)
            if (values != null) property["enum"] = values
            add(ToolDefinition(name, name, description, mapOf("type" to "object", "properties" to mapOf(key to property),
                "required" to listOf(key), "additionalProperties" to false)))
        }
        tool("halo_volume", "Change media volume by one step.", "direction", listOf("up", "down"))
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH))
            tool("halo_flashlight", "Set phone flashlight state.", "state", listOf("on", "off"))
        if (context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).isNotEmpty())
            tool("halo_open_app", "Open an installed package. Receipt confirms dispatch only.", "package")
        if (resolves(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=place"))))
            tool("halo_navigation", "Open a destination in a maps app. Does not confirm arrival or route execution.", "destination")
        if (resolves(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
            tool("halo_browser", "Open an HTTP or HTTPS URL. Receipt confirms dispatch only.", "url")
        if (resolves(Intent(Intent.ACTION_WEB_SEARCH)))
            tool("halo_search", "Open a web search in an installed app.", "query")
        tool("halo_media", "Send play or pause to the active media session. Does not confirm playback.", "action", listOf("play", "pause"))
    }
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)
        if (toolCall.functionName == "halo_list_apps") {
            val query = toolCall.arguments["query"] as? String ?: return reject("Expected query")
            if (query.length > 100 || toolCall.arguments.keys != setOf("query")) return reject("Invalid query")
            val manager = context.packageManager
            val matches = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.loadLabel(manager).toString() to it.activityInfo.packageName }
                .distinctBy { it.second }.filter { it.first.contains(query, true) || it.second.contains(query, true) }
                .sortedBy { it.first }.take(20)
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                matches.joinToString("\n") { "${it.first}: ${it.second}" }.ifEmpty { "No matching launchable apps" })
        }
        val definition = definitions().firstOrNull { it.id == toolCall.functionName } ?: return reject("Capability or permission unavailable")
        val required = definition.parameters["required"] as List<*>
        val key = required.single() as String
        if (toolCall.arguments.keys != setOf(key)) return reject("Unexpected or missing arguments")
        val value = (toolCall.arguments[key] as? String)?.trim()?.takeIf { it.isNotBlank() && it.length <= 500 && it.none(Char::isISOControl) }
            ?: return reject("Invalid $key")
        val properties = definition.parameters["properties"] as Map<*, *>
        val allowed = (properties[key] as Map<*, *>)["enum"] as? List<*>
        if (allowed != null && value !in allowed) return reject("Unsupported $key")
        if (definition.id in setOf("halo_open_app", "halo_navigation", "halo_browser", "halo_search") && !isForeground())
            return reject("Open Halo to launch another app")
        val (capability, task) = when (definition.id) {
            "halo_volume" -> device to DeviceTask(action = "volume_$value")
            "halo_flashlight" -> device to DeviceTask(action = "flashlight_$value")
            "halo_open_app" -> {
                if (!value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) return reject("Expected installed package name")
                if (context.packageManager.getLaunchIntentForPackage(value) == null) return reject("Package is not launchable")
                apps to ApplicationTask(packageName = value, intent = "launch")
            }
            "halo_navigation" -> navigation to NavigationTask(coordinates = value)
            "halo_browser" -> {
                val uri = Uri.parse(value)
                if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() || uri.userInfo != null) return reject("Expected HTTP(S) URL without credentials")
                web to WebTask(url = value)
            }
            "halo_search" -> web to WebTask(url = value)
            "halo_media" -> media to MediaTask(mediaAction = value, mediaId = "")
            else -> return reject("Unsupported operation")
        }
        events.publish(ActionReceipt(operationId = toolCall.id, category = "Actions", title = definition.id, stage = "Requested", detail = "Validated host action"))
        val result = capability.execute(task)
        val stage = if (result is Outcome.Success) {
            if (capability === device) "Completed" else "Dispatched"
        } else "Failed"
        val detail = when (result) {
            is Outcome.Success -> if (stage == "Dispatched") "Android accepted the dispatch; downstream completion is unobserved." else result.message.orEmpty()
            is Outcome.PermanentFailure -> result.reason
            is Outcome.RetryableFailure -> result.reason
            is Outcome.Cancelled -> result.reason ?: "Cancelled"
            is Outcome.Partial -> result.message
            is Outcome.NeedsClarification -> result.prompt
        }
        events.publish(ActionReceipt(operationId = toolCall.id, category = "Actions", title = definition.id, stage = stage, detail = detail))
        return if (result is Outcome.Success) ToolExecutionResult.Success(toolCall.id, definition.id, "$stage: $detail",
            if (stage == "Completed") com.halo.runtime.cognition.agent.ActionTruth.CONFIRMED else com.halo.runtime.cognition.agent.ActionTruth.ACKNOWLEDGED)
            else ToolExecutionResult.Failed(toolCall.id, definition.id, detail)
    }
}
