package com.halo.app.data.settings

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NotificationSettingsApp(val packageName: String, val label: String, val appVersion: String)
fun interface NotificationSettingsApps { suspend fun list(): List<NotificationSettingsApp> }

/** The same launchable-app scope shown by NotificationSettingsScreen; no content or access grant. */
class NotificationSettingsAppSource(private val context: Context) : NotificationSettingsApps {
    override suspend fun list(): List<NotificationSettingsApp> = withContext(Dispatchers.IO) {
        val packages = context.packageManager
        packages.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }.mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                val record = runCatching { packages.getPackageInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
                val version = if (android.os.Build.VERSION.SDK_INT >= 28) record.longVersionCode else record.versionCode.toLong()
                NotificationSettingsApp(pkg, info.loadLabel(packages).toString().take(200),
                    notificationSettingsVersion("notification-app", pkg, version.toString(),
                        record.firstInstallTime.toString(), record.lastUpdateTime.toString()))
            }.sortedBy { it.packageName }
    }
}
