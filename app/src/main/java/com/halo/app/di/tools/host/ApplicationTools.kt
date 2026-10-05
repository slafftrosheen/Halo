package com.halo.app.di.tools.host

import android.content.Context
import com.halo.host.android.capability.AndroidApplicationCapability
import com.halo.host.android.resolver.AndroidApplicationResolver
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.tools.ToolExecutionContext
import com.halo.runtime.cognition.tools.ToolRejectionKind
import com.halo.runtime.cognition.ApplicationTask
import com.halo.runtime.cognition.tools.ToolExecutionResult
import com.halo.runtime.cognition.tools.ToolExecutor
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition

class ApplicationTools(
    private val context: Context,
    private val resolvePackage: (suspend (String) -> String?)? = null,
    private val isLaunchable: ((String) -> Boolean)? = null,
    private val launch: (suspend (String) -> Outcome)? = null,
    private val isForeground: () -> Boolean,
) : ToolExecutor {
    constructor(context: Context, isForeground: () -> Boolean) : this(context, null, null, null, isForeground)
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

    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult =
        execute(toolCall, ToolExecutionContext())

    override suspend fun execute(toolCall: ToolCall, executionContext: ToolExecutionContext): ToolExecutionResult = try {
        executeChecked(toolCall, executionContext)
    } catch (_: SecurityException) {
        ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Android privilege required", ToolRejectionKind.PERMISSION_REQUIRED)
    } catch (_: android.content.ActivityNotFoundException) {
        ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, "Package is not launchable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
    }

    private suspend fun executeChecked(toolCall: ToolCall, executionContext: ToolExecutionContext): ToolExecutionResult {
        fun reject(reason: String, kind: ToolRejectionKind = ToolRejectionKind.INVALID_ARGUMENTS) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, reason, kind)
        
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
            if (toolCall.arguments.keys.any { it !in setOf("package", "appName") }) return reject("Unexpected argument")
            for ((name, value) in toolCall.arguments) {
                val text = value as? String ?: return reject("Expected text argument")
                if (text.isBlank() || text.length > if (name == "package") 500 else 100) return reject("Invalid argument length")
            }
            val suppliedPackage = toolCall.arguments["package"] as? String
            val appName = toolCall.arguments["appName"] as? String
            if ((suppliedPackage == null) == (appName == null)) return reject("Provide exactly one package or appName")
            val pkg = suppliedPackage ?: (if (resolvePackage != null) resolvePackage.invoke(appName!!) else resolver.resolve(appName!!)?.packageName)
                ?: return reject("App target is missing or ambiguous; provide its exact name", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            if (!pkg.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) return reject("Expected installed package name")
            if (!isForeground() && !executionContext.directUserAuthority) return reject("Open Halo to launch another app", ToolRejectionKind.UNSUPPORTED_OPERATION)
            if (!(isLaunchable?.invoke(pkg) ?: (context.packageManager.getLaunchIntentForPackage(pkg) != null))) return reject("Package is not launchable", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
            
            val outcome = launch?.invoke(pkg) ?: apps.execute(ApplicationTask(packageName = pkg, intent = "launch"))
            if (outcome !is Outcome.Dispatched) {
                val permission = when (outcome) {
                    is Outcome.PermanentFailure -> outcome.error is SecurityException
                    is Outcome.RetryableFailure -> outcome.error is SecurityException
                    else -> false
                }
                return reject("Platform refused application launch", if (permission) ToolRejectionKind.PERMISSION_REQUIRED else ToolRejectionKind.UNSUPPORTED_OPERATION)
            }
            val receipt = HostActionReceipt.DISPATCHED
            return ToolExecutionResult.Success(toolCall.id, toolCall.functionName, receipt.name, com.halo.runtime.cognition.agent.ActionTruth.DISPATCHED)
        }
        
        return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
    }
}
