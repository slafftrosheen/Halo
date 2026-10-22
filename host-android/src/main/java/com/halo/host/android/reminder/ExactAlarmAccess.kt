package com.halo.host.android.reminder

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

sealed interface ExactAlarmAccess {
    data object Available : ExactAlarmAccess
    data object NotRequired : ExactAlarmAccess
    data object NeedsUserGrant : ExactAlarmAccess
}
class ExactAlarmAccessController(private val context: Context) {
    fun status(): ExactAlarmAccess = if (Build.VERSION.SDK_INT < 31) ExactAlarmAccess.NotRequired
        else if (context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) ExactAlarmAccess.Available
        else ExactAlarmAccess.NeedsUserGrant
    fun openSettings() {
        settingsIntent()?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    fun settingsIntent(): Intent? = if (Build.VERSION.SDK_INT >= 31)
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")) else null
}
