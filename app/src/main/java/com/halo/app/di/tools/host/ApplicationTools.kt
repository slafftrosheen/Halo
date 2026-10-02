package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import com.halo.host.android.capability.AndroidApplicationCapability
import com.halo.host.android.resolver.AndroidApplicationResolver
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class ApplicationTools(
    private val context: Context,
    private val isForeground: () -> Boolean
) : ToolExecutor {
    private val resolver = AndroidApplicationResolver(context)
    private val apps = AndroidApplicationCapability(context, resolver)

    override fun definitions(): List<ToolDefinition> = buildList {
        add(ToolDefinition("halo_list_apps", "halo_list_apps", "Find launchable installed apps by name and return package IDs for halo_open_app.",
            mapOf("type" to "object", "properties" to mapOf("query" to mapOf("type" to "string", "maxLength" to 100)),
                "required" to listOf("query"), "additionalProperties" to false)))
        add(ToolDefinition("halo_open_app", "halo_open_app", "Open an installed package. Receipt confirms dispatch only.",
            mapOf("type" to "object", "properties" to mapOf("package" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 500),
                    "appName" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 100)),
                "required" to emptyList<String>(), "additionalProperties" to false)))
    }

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(reason: String) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason)
        
        if (toolCall.functionName == "halo_list_apps") {
            val query = toolCall.arguments["query"] as? String ?: return reject("Expected query")
            if (query.length > 100 || toolCall.arguments.keys != setOf("query")) return reject("Invalid query")
            val matches = resolver.search(query)
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                matches.joinToString("\n") {
                    "label=${it.app.label}; package=${it.app.packageName}; match=${it.quality.name.lowercase()}; system=${it.app.systemApp}"
                }.ifEmpty { "No matching launchable apps" })
        }
        
        if (toolCall.functionName == "halo_open_app") {
            val suppliedPackage = toolCall.arguments["package"] as? String
            val appName = toolCall.arguments["appName"] as? String
            if ((suppliedPackage == null) == (appName == null)) return reject("Provide exactly one package or appName")
            val pkg = suppliedPackage ?: resolver.resolve(appName!!)?.packageName
                ?: return reject("App target is missing or ambiguous; provide its exact name")
            if (!isForeground()) return reject("Open Halo to launch another app")
            if (!pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) return reject("Expected installed package name")
            if (context.packageManager.getLaunchIntentForPackage(pkg) == null) return reject("Package is not launchable")
            
            apps.execute(ApplicationTask(packageName = pkg, intent = "launch"))
            val receipt = HostActionReceipt.DISPATCHED
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, receipt.name, com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
        }
        
        return reject("Unknown tool")
    }
}
