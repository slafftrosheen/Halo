package com.halo.app.data.notification

import android.content.Context
import android.content.SharedPreferences

class NotificationPolicyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("halo_notification_policies", Context.MODE_PRIVATE)

    fun policyForApp(packageName: String): NotificationAccessPolicy {
        val saved = prefs.getString("policy_$packageName", null)
        if (saved != null) {
            return runCatching { NotificationAccessPolicy.valueOf(saved) }.getOrDefault(NotificationAccessPolicy.SUMMARIZE_ONLY)
        }

        val pkg = packageName.lowercase()
        return when {
            pkg.contains("pass") || pkg.contains("authenticator") || pkg.contains("authy") || pkg.contains("bank") || pkg.contains("finance") ->
                NotificationAccessPolicy.HIDE_CONTENT
            pkg.contains("health") || pkg.contains("medical") ->
                NotificationAccessPolicy.HIDE_CONTENT
            else -> NotificationAccessPolicy.SUMMARIZE_ONLY
        }
    }

    fun setPolicyForApp(packageName: String, policy: NotificationAccessPolicy) {
        prefs.edit().putString("policy_$packageName", policy.name).apply()
    }
}
