package com.halo.app.notification

import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService

/** Live framework ownership only. A disconnected listener cannot read or dismiss anything. */
object LiveNotificationOwner {
    @Volatile private var owner: NotificationListenerService? = null
    fun connected(): Boolean = owner != null
    fun attach(service: NotificationListenerService) { owner = service }
    fun detach(service: NotificationListenerService) { if (owner === service) owner = null }
    fun snapshot(): List<StatusBarNotification>? = owner?.let { runCatching { it.activeNotifications.orEmpty().toList() }.getOrNull() }
    fun dismiss(key: String): Boolean {
        val service = owner ?: return false
        if (runCatching { service.activeNotifications.orEmpty().none { it.key == key } }.getOrDefault(true)) return false
        return runCatching { service.cancelNotification(key); true }.getOrDefault(false)
    }
}
