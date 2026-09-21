package com.halo.app.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HostNotification(
    val key: String,
    val packageName: String,
    val appLabel: String?,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val category: String?
)

class HaloNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName.orEmpty()

        // Privacy filter for sensitive applications
        if (pkg.contains("pass") || pkg.contains("authenticator") || pkg.contains("1password") || pkg.contains("bitwarden")) {
            return
        }

        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString()
        val text = extras.getCharSequence("android.text")?.toString()

        val item = HostNotification(
            key = sbn.key,
            packageName = pkg,
            appLabel = pkg.substringAfterLast('.'),
            title = title,
            text = text,
            postedAt = sbn.postTime,
            category = sbn.notification.category
        )

        val current = _recentNotifications.value.toMutableList()
        current.removeAll { it.key == item.key }
        current.add(0, item)
        _recentNotifications.value = current.take(20)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val current = _recentNotifications.value.toMutableList()
        current.removeAll { it.key == sbn.key }
        _recentNotifications.value = current
    }

    companion object {
        private val _recentNotifications = MutableStateFlow<List<HostNotification>>(emptyList())
        val recentNotifications: StateFlow<List<HostNotification>> = _recentNotifications.asStateFlow()
    }
}
