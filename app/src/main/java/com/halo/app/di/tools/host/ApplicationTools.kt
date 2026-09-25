package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import com.halo.host.android.capability.AndroidApplicationCapability
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class ApplicationTools(
    private val context: Context,
    private val isForeground: () -> Boolean
) : ToolExecutor {
    private val apps = AndroidApplicationCapability(context)

    override fun definitions(): List<ToolDefinition> = buildList {
        add(ToolDefinition("halo_list_apps", "halo_list_apps", "Find launchable installed apps by name and return package IDs for halo_open_app.",
            mapOf("type" to "object", "properties" to mapOf("query" to mapOf("type" to "string", "maxLength" to 100)),
                "required" to listOf("query"), "additionalProperties" to false)))
        
        if (context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).isNotEmpty()) {
            add(ToolDefinition("halo_open_app", "halo_open_app", "Open an installed package. Receipt confirms dispatch only.",
                mapOf("type" to "object", "properties" to mapOf("package" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 500)),
                    "required" to listOf("package"), "additionalProperties" to false)))
        }
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)
        
        if (toolCall.functionName == "halo_list_apps") {
            val query = toolCall.arguments["query"] as? String ?: return reject("Expected query")
            if (query.length > 100 || toolCall.arguments.keys != setOf("query")) return reject("Invalid query")
            val manager = context.packageManager
            val matches = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.loadLabel(manager).toString() to it.activityInfo.packageName }
                .distinctBy { it.second }.filter { it.first.contains(query, ignoreCase = true) || it.second.contains(query, ignoreCase = true) }
                .sortedBy { it.first }.take(20)
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                matches.joinToString("\n") { "${it.first}: ${it.second}" }.ifEmpty { "No matching launchable apps" })
        }
        
        if (toolCall.functionName == "halo_open_app") {
            val pkg = toolCall.arguments["package"] as? String ?: return reject("Missing package argument")
            if (!isForeground()) return reject("Open Halo to launch another app")
            if (!pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) return reject("Expected installed package name")
            if (context.packageManager.getLaunchIntentForPackage(pkg) == null) return reject("Package is not launchable")
            
            apps.execute(ApplicationTask(packageName = pkg, intent = "launch"))
            val receipt = HostActionReceipt.DISPATCHED
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, receipt.name)
        }
        
        return reject("Unknown tool")
    }
}
