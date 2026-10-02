package com.halo.app.di

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.halo.host.android.accessibility.HaloAccessibilityService
import com.halo.sdk.runtime.skills.HostPermission

/**
 * Android-side permission truth for the portable skill permission vocabulary.
 *
 * Runtime permissions are requested through Activity Result APIs. Special-access permissions
 * remain explicit Android settings flows; Halo never treats opening a settings page as a grant.
 */
class AndroidSkillPermissionChecker(private val context: Context) {

    fun granted(permission: HostPermission): Boolean = when (permission) {
        HostPermission.NOTIFICATION_ACCESS ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        HostPermission.ACCESSIBILITY -> HaloAccessibilityService.isEnabled
        HostPermission.WRITE_SETTINGS -> Settings.System.canWrite(context)
        HostPermission.NOTIFICATION_POLICY ->
            context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true
        HostPermission.FILES -> true // SAF grants access per selected document.
        else -> runtimePermissions(permission).all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun runtimePermissions(permission: HostPermission): Set<String> = when (permission) {
        HostPermission.CONTACTS -> setOf(Manifest.permission.READ_CONTACTS)
        HostPermission.CALENDAR_READ -> setOf(Manifest.permission.READ_CALENDAR)
        HostPermission.CALENDAR_WRITE -> setOf(Manifest.permission.WRITE_CALENDAR)
        HostPermission.MICROPHONE -> setOf(Manifest.permission.RECORD_AUDIO)
        HostPermission.CAMERA -> setOf(Manifest.permission.CAMERA)
        HostPermission.LOCATION -> setOf(Manifest.permission.ACCESS_FINE_LOCATION)
        HostPermission.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= 33) setOf(Manifest.permission.POST_NOTIFICATIONS) else emptySet()
        HostPermission.BLUETOOTH ->
            if (Build.VERSION.SDK_INT >= 31) {
                setOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                setOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        HostPermission.FILES,
        HostPermission.ACCESSIBILITY,
        HostPermission.NOTIFICATION_ACCESS,
        HostPermission.WRITE_SETTINGS,
        HostPermission.NOTIFICATION_POLICY -> emptySet()
    }

    fun specialAccessIntent(permission: HostPermission): Intent? = when (permission) {
        HostPermission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        HostPermission.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        HostPermission.WRITE_SETTINGS -> Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        HostPermission.NOTIFICATION_POLICY -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        else -> null
    }

    /**
     * Tool-derived safety net. Manifests remain the declarative source, while this mapping
     * prevents a stale/missing manifest permission from turning into a late execution failure.
     *
     * NOTIFICATION_POLICY is included for halo_ringer so the global grant flow can unlock
     * silent mode; normal/vibrate do not make that special access a hard skill gate.
     */
    fun toolAccessRequirements(toolIds: Set<String>): Set<HostPermission> = buildSet {
        for (toolId in toolIds) {
            when (toolId) {
                "halo_flashlight" -> add(HostPermission.CAMERA)
                "halo_find_contact" -> add(HostPermission.CONTACTS)
                "halo_calendar_upcoming" -> add(HostPermission.CALENDAR_READ)
                "halo_calendar_create" -> add(HostPermission.CALENDAR_WRITE)
                "halo_notification_digest",
                "halo_list_notifications",
                "halo_dismiss_notification",
                "halo_reply_notification",
                "halo_replyable_notifications" -> add(HostPermission.NOTIFICATION_ACCESS)
                "halo_brightness" -> add(HostPermission.WRITE_SETTINGS)
                "halo_ringer" -> add(HostPermission.NOTIFICATION_POLICY)
                "halo_create_watch" -> add(HostPermission.NOTIFICATIONS)
                "halo_read_screen",
                "halo_find_on_screen",
                "halo_ui_click",
                "halo_ui_scroll",
                "halo_ui_set_text",
                "halo_ui_global_action",
                "halo_ui_set_checked",
                "halo_ui_wait",
                "halo_ui_search" -> add(HostPermission.ACCESSIBILITY)
            }
        }
    }

    fun accessPlan(
        declaredPermissions: Set<HostPermission>,
        toolIds: Set<String>,
    ): Set<HostPermission> = declaredPermissions + toolAccessRequirements(toolIds)
}
