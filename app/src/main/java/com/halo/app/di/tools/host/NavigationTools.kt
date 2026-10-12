package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import com.halo.host.android.capability.AndroidNavigationCapability
import com.halo.host.android.capability.AndroidWebCapability
import com.halo.runtime.cognition.NavigationTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.ActionFailureCode
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.ToolFailureKind
import com.halo.runtime.cognition.tools.ToolRejectionKind
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

    private fun fromHostOutcome(call: ToolCall, outcome: Outcome): ToolExecutionResult = when (outcome) {
        is Outcome.Dispatched -> ToolExecutionResult.Success(call.id, call.functionName,
            HostActionReceipt.DISPATCHED.name, ActionTruth.DISPATCHED)
        is Outcome.Success -> ToolExecutionResult.Success(call.id, call.functionName,
            "Platform acknowledged request", ActionTruth.CONFIRMED)
        is Outcome.PermanentFailure -> ToolExecutionResult.Rejected(call.id, call.functionName,
            outcome.reason.take(300), if (outcome.code == ActionFailureCode.PERMISSION_REQUIRED ||
                outcome.error is SecurityException) ToolRejectionKind.PERMISSION_REQUIRED else ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        is Outcome.RetryableFailure -> ToolExecutionResult.Failed(call.id, call.functionName,
            outcome.reason.take(300), failureKind = ToolFailureKind.TRANSIENT, code = "ANDROID_DISPATCH_RETRYABLE")
        is Outcome.Partial -> ToolExecutionResult.Failed(call.id, call.functionName,
            "Android did not confirm the requested action", completionUnknown = true,
            failureKind = ToolFailureKind.UNKNOWN, code = "ANDROID_DISPATCH_UNCERTAIN")
        is Outcome.Cancelled -> ToolExecutionResult.Rejected(call.id, call.functionName,
            "Action cancelled", ToolRejectionKind.INTERACTION_CANCELLED)
        is Outcome.NeedsClarification -> ToolExecutionResult.Rejected(call.id, call.functionName,
            outcome.prompt.take(300), ToolRejectionKind.INVALID_ARGUMENTS)
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) =
            ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        if (!isForeground()) return reject("Open Halo to launch another app", ToolRejectionKind.UNSUPPORTED_OPERATION)
        if (toolCall.arguments.size != 1) return reject("Expected exactly one argument")

        return when (toolCall.functionName) {
            "halo_navigation" -> {
                val dest = (toolCall.arguments["destination"] as? String)?.trim()
                    ?.takeIf { it.isNotBlank() && it.length <= 500 } ?: return reject("Missing or invalid destination")
                fromHostOutcome(toolCall, navigation.execute(NavigationTask(coordinates = dest)))
            }
            "halo_browser" -> {
                val url = (toolCall.arguments["url"] as? String)?.trim()
                    ?.takeIf { it.isNotBlank() && it.length <= 500 } ?: return reject("Missing or invalid URL")
                val uri = Uri.parse(url)
                if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank())
                    return reject("URL must be an absolute HTTP or HTTPS address")
                fromHostOutcome(toolCall, web.execute(WebTask(url = url)))
            }
            "halo_search" -> {
                val query = (toolCall.arguments["query"] as? String)?.trim()
                    ?.takeIf { it.isNotBlank() && it.length <= 500 } ?: return reject("Missing or invalid query")
                // Prefer the user's Android search handler, but some phones have no
                // ACTION_WEB_SEARCH receiver. Then use a real browser search URL,
                // not a second identical implicit search request.
                val outcome = try {
                    context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(android.app.SearchManager.QUERY, query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    Outcome.Dispatched("Android web search dispatched")
                } catch (_: ActivityNotFoundException) {
                    web.execute(WebTask(url = "https://duckduckgo.com/?q=" + Uri.encode(query)))
                } catch (_: SecurityException) {
                    Outcome.PermanentFailure("Android blocked the web search",
                        code = ActionFailureCode.PERMISSION_REQUIRED)
                } catch (_: Exception) {
                    Outcome.RetryableFailure("Android could not dispatch web search")
                }
                fromHostOutcome(toolCall, outcome)
            }
            else -> reject("Unknown navigation tool", ToolRejectionKind.TOOL_UNKNOWN)
        }
    }
}
