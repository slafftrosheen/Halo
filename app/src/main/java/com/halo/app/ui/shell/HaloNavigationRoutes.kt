package com.halo.app.ui.shell

import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination

private val settingsRoutes = setOf(
    "ai_settings", "voice_settings", "permissions", "provider_setup", "model_browser",
    "skills", "runtime_status", "system_check", "automation", "automation_sandbox",
    "phone_control_sandbox", "memory_learning", "notification_settings", "android_assistant", "storage_backup",
    "vision_lab", "local_audio_lab", "advanced_settings", "workload_diagnostics", "assistant_diagnostics",
)

/** Both the rail and bottom bar keep the parent selected on every detail screen. */
fun primaryNavigationRoute(route: String?): String? = when {
    route == null -> null
    route.substringBefore("?") == "activity" -> "activity"
    route.substringBefore("?") == "automation" -> "settings"
    route == "notifications" || route.startsWith("notifications/") -> "home"
    route.substringBefore("?") == "study" -> "notebook"
    route == "workspace_handoff" || route == "canvases" || route.startsWith("notebook/") || route.startsWith("studio") || route.startsWith("file_tools") -> "notebook"
    route == "screen" || route.startsWith("screen/") -> "home"
    route == "discovery" || route.startsWith("device/") -> "devices"
    route == "integrations" || route.startsWith("integrations/") -> "settings"
    route.startsWith("advanced/") || route.startsWith("settings/") || route.startsWith("automation_review/") || route in settingsRoutes -> "settings"
    else -> route
}

fun NavHostController.navigateToPrimary(route: String, restoreSavedState: Boolean = true) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        // An explicit destination request must not restore a detail above that root.
        restoreState = restoreSavedState
    }
}

/** Object-bearing instruments own a fresh entry; Back restores the previous identity and draft. */
internal fun NavHostController.navigateToInstrument(route: String) {
    navigate(route) {
        launchSingleTop = route.substringBefore("?") !in setOf(
            com.halo.app.ui.everyday.EverydayRoutes.ORGANIZER, com.halo.app.ui.everyday.EverydayRoutes.BRIEF,
            com.halo.app.ui.everyday.EverydayRoutes.SOURCE,com.halo.app.ui.everyday.EverydayRoutes.CLOCKS,"activity")
    }
}
