package com.halo.app.ui.shell

import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination

private val settingsRoutes = setOf(
    "ai_settings", "voice_settings", "permissions", "provider_setup", "model_browser",
    "skills", "runtime_status", "system_check", "automation", "automation_sandbox",
    "phone_control_sandbox", "memory_learning", "notification_settings", "android_assistant", "storage_backup",
)

/** Both the rail and bottom bar keep the parent selected on every detail screen. */
fun primaryNavigationRoute(route: String?): String? = when {
    route == null -> null
    route == "notifications" || route.startsWith("notifications/") -> "home"
    route == "study" -> "notebook"
    route.startsWith("notebook/") -> "notebook"
    route == "discovery" || route.startsWith("device/") -> "devices"
    route == "integrations" || route.startsWith("integrations/") -> "settings"
    route.startsWith("settings/") || route.startsWith("automation_review/") || route in settingsRoutes -> "settings"
    else -> route
}

fun NavHostController.navigateToPrimary(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
