package com.halo.app.di

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.halo.sdk.runtime.skills.HostPermission
import com.halo.host.android.accessibility.HaloAccessibilityService

class AndroidSkillPermissionChecker(private val context: Context) {
    fun granted(permission: HostPermission): Boolean {
        val androidPermission = when (permission) {
            HostPermission.CONTACTS -> Manifest.permission.READ_CONTACTS
            HostPermission.CALENDAR_READ -> Manifest.permission.READ_CALENDAR
            HostPermission.CALENDAR_WRITE -> Manifest.permission.WRITE_CALENDAR
            HostPermission.NOTIFICATION_ACCESS -> return androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            HostPermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
            HostPermission.CAMERA -> Manifest.permission.CAMERA
            HostPermission.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            HostPermission.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else return true
            HostPermission.BLUETOOTH ->
                if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else return true
            HostPermission.FILES -> return true // SAF grants access per selected document.
            HostPermission.ACCESSIBILITY -> return HaloAccessibilityService.isEnabled
        }
        return context.checkSelfPermission(androidPermission) == PackageManager.PERMISSION_GRANTED
    }
}
