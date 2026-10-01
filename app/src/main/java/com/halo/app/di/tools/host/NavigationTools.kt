package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.halo.host.android.capability.AndroidNavigationCapability
import com.halo.host.android.capability.AndroidWebCapability
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.WebTask
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class NavigationTools(
    private val context: Context,
    private val isForeground: () -> Boolean
) : ToolExecutor {
    private val navigation = AndroidNavigationCapability(context)
    private val web = AndroidWebCapability(context)

    override fun definitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            "halo_navigation", "halo_navigation", "Open a destination in a maps app. Does not confirm arrival or route execution.",
            mapOf("type" to "object", "properties" to mapOf("destination" to mapOf("type" to "string", "maxLength" to 500)), "required" to listOf("destination"), "additionalProperties" to false)
        ),
        ToolDefinition(
            "halo_browser", "halo_browser", "Open an HTTP or HTTPS URL. Receipt confirms dispatch only.",
            mapOf("type" to "object", "properties" to mapOf("url" to mapOf("type" to "string", "maxLength" to 500)), "required" to listOf("url"), "additionalProperties" to false)
        ),
        ToolDefinition(
            "halo_search", "halo_search", "Open a web search in an installed app.",
            mapOf("type" to "object", "properties" to mapOf("query" to mapOf("type" to "string", "maxLength" to 500)), "required" to listOf("query"), "additionalProperties" to false)
        )
    )

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)

        if (!isForeground()) return reject("Open Halo to launch another app")

        return when (toolCall.functionName) {
            "halo_navigation" -> {
                val dest = toolCall.arguments["destination"] as? String ?: return reject("Missing destination")
                navigation.execute(NavigationTask(coordinates = dest))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.DISPATCHED.name)
            }
            "halo_browser" -> {
                val urlStr = toolCall.arguments["url"] as? String ?: return reject("Missing url")
                val uri = Uri.parse(urlStr)
                if (uri.scheme !in setOf("http", "https")) return reject("URL scheme must be http or https")
                web.execute(WebTask(url = urlStr))
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.DISPATCHED.name)
            }
            "halo_search" -> {
                val query = toolCall.arguments["query"] as? String ?: return reject("Missing query")
                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ToolExecutionResult.Success(toolCall.id, toolCall.functionName, HostActionReceipt.DISPATCHED.name)
            }
            else -> reject("Unknown navigation tool")
        }
    }
}
