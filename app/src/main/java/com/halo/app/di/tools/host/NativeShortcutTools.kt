package com.halo.app.di.tools.host

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Process
import com.halo.app.MainActivity
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.runtime.cognition.agent.ActionTruth
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*

/** Launcher authority is checked at every use; assistant role alone does not confer it. */
class NativeShortcutTools(private val context: Context, private val isForeground: () -> Boolean = { true }) : ToolExecutor {
    override fun definitions() = listOf(ToolDefinition("halo_native_shortcut", "halo_native_shortcut",
        "List or launch an exposed app shortcut only with Android shortcut-host authority, or ask the user to pin a fixed Halo page. Does not bypass launcher roles.",
        mapOf("type" to "object", "properties" to mapOf(
            "action" to mapOf("type" to "string", "enum" to listOf("list", "run", "pin_halo_page")),
            "packageName" to mapOf("type" to "string", "maxLength" to 200),
            "shortcutId" to mapOf("type" to "string", "maxLength" to 200),
            "screen" to mapOf("type" to "string", "enum" to HaloScreenCatalog.screens.keys.toList())),
            "required" to listOf("action"), "additionalProperties" to false)))
    override suspend fun execute(toolCall: ToolCall): ToolExecutionResult {
        fun reject(message: String, kind: ToolRejectionKind) = ToolExecutionResult.Rejected(toolCall.id, toolCall.functionName, message, kind)
        if (toolCall.functionName != "halo_native_shortcut") return reject("Unknown tool", ToolRejectionKind.TOOL_UNKNOWN)
        if (!isForeground()) return reject("Open Halo to review shortcuts", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
        return try {
            val action = toolCall.arguments["action"]
            if (action == "pin_halo_page") {
                val screen = toolCall.arguments["screen"] as? String
                if (toolCall.arguments.keys != setOf("action", "screen") || screen !in HaloScreenCatalog.screens)
                    return reject("Choose an exact Halo page", ToolRejectionKind.INVALID_ARGUMENTS)
                val manager = context.getSystemService(ShortcutManager::class.java)
                if (!manager.isRequestPinShortcutSupported) return reject("Launcher does not support pinned shortcuts", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                val shortcut = ShortcutInfo.Builder(context, "halo-page-$screen")
                    .setShortLabel(HaloScreenCatalog.screens.getValue(screen!!))
                    .setIcon(android.graphics.drawable.Icon.createWithResource(context, com.halo.app.R.drawable.ic_halo_talk))
                    .setIntent(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                        .putExtra(HaloScreenCatalog.EXTRA_SCREEN, screen)).build()
                if (!manager.requestPinShortcut(shortcut, null)) return reject("Launcher declined pin request", ToolRejectionKind.CAPABILITY_UNAVAILABLE)
                return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                    ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "Pin request submitted; user approval and pinned state not verified"), ActionTruth.DISPATCHED)
            }
            if (action !in setOf("list", "run")) return reject("Unknown shortcut action", ToolRejectionKind.INVALID_ARGUMENTS)
            val allowedKeys = if (action == "run") setOf("action", "packageName", "shortcutId") else setOf("action", "packageName")
            if (toolCall.arguments.keys != allowedKeys) return reject("Invalid shortcut fields", ToolRejectionKind.INVALID_ARGUMENTS)
            val name = toolCall.arguments["packageName"] as? String ?: return reject("packageName required", ToolRejectionKind.INVALID_ARGUMENTS)
            if (!name.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")) || name.length > 200)
                return reject("Invalid package", ToolRejectionKind.INVALID_ARGUMENTS)
            context.packageManager.getApplicationInfo(name, 0)
            val launcher = context.getSystemService(LauncherApps::class.java)
            if (!launcher.hasShortcutHostPermission()) return reject("This Android role cannot inspect or run app shortcuts; choose them in your launcher", ToolRejectionKind.PERMISSION_REQUIRED)
            val query = LauncherApps.ShortcutQuery().setPackage(name).setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val shortcuts = launcher.getShortcuts(query, Process.myUserHandle()).orEmpty().filter { it.isEnabled && it.`package` == name }
            if (action == "list") return ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                ToolResultEnvelope.success(ActionTruth.ACKNOWLEDGED, details = shortcuts.take(20).joinToString("\n") { "id=${it.id}; label=${it.shortLabel?.toString()?.replace('\n',' ')?.take(100)}" }), ActionTruth.ACKNOWLEDGED)
            val id = toolCall.arguments["shortcutId"] as? String ?: return reject("shortcutId required", ToolRejectionKind.INVALID_ARGUMENTS)
            if (id.length > 200 || shortcuts.count { it.id == id } != 1) return reject("Shortcut unavailable or stale", ToolRejectionKind.RESOURCE_UNAVAILABLE)
            launcher.startShortcut(name, id, null, null, Process.myUserHandle())
            ToolExecutionResult.Success(toolCall.id, toolCall.functionName,
                ToolResultEnvelope.success(ActionTruth.DISPATCHED, details = "Exact enabled shortcut dispatched; destination not verified"), ActionTruth.DISPATCHED)
        } catch (_: SecurityException) { reject("Shortcut role or permission changed", ToolRejectionKind.PERMISSION_REQUIRED) }
        catch (_: android.content.pm.PackageManager.NameNotFoundException) { reject("App not visible or installed", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
        catch (_: android.content.ActivityNotFoundException) { reject("Shortcut handler disappeared", ToolRejectionKind.RESOURCE_UNAVAILABLE) }
        catch (_: IllegalArgumentException) { reject("Shortcut not valid for this launcher", ToolRejectionKind.INVALID_ARGUMENTS) }
    }
}
