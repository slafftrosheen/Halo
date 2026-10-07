package com.halo.app.data.notification

import android.content.Context
import android.content.SharedPreferences

class NotificationPolicyManager(private val context: Context) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("halo_notification_policies", Context.MODE_PRIVATE)
    }

    private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val changes: kotlinx.coroutines.flow.StateFlow<Long> = revision

    fun policyForApp(packageName: String): NotificationAccessPolicy {
        val saved = prefs.getString("policy_$packageName", null)
        if (saved != null) {
            return runCatching { NotificationAccessPolicy.valueOf(saved) }.getOrDefault(NotificationAccessPolicy.BLOCK)
        }

        return NotificationAccessPolicy.BLOCK
    }

    fun setPolicyForApp(packageName: String, policy: NotificationAccessPolicy) {
        check(prefs.edit().putString("policy_$packageName", policy.name).commit()) { "Could not save notification policy" }
        revision.value += 1
    }
}
