package com.halo.app.notification

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.halo.app.data.notification.IncomingNotification
import java.time.Instant

/** Framework boundary only. Application-owned bridge serializes writes to Room. */
class HaloNotificationListenerService : NotificationListenerService() {
    private val bridge get() = (application as NotificationBridgeOwner).notificationBridge
    private var connected = false
    private val lifecycle = NotificationLifecycleTracker()
    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        LiveNotificationOwner.attach(this)
        com.halo.runtime.cognition.routine.AutomationServiceSignals.notifications.availability.value = com.halo.runtime.cognition.routine.AutomationSourceStatus.AVAILABLE
        val active = activeNotifications.orEmpty()
        lifecycle.seed(active.map { it.key })
        // MINING-P1-001: capture live RemoteInput reply actions for currently posted notifications.
        // Only the live platform objects are held, never persisted.
        active.forEach { sbn ->
            NotificationReplyRegistry.shared.replace(sbn.key, sbn.notification.actions?.toList().orEmpty())
        }
        bridge.connected(active.map(::incoming))
    }
    override fun onListenerDisconnected() {
        LiveNotificationOwner.detach(this)
        connected = false
        com.halo.runtime.cognition.routine.AutomationServiceSignals.notifications.availability.value = if(bridge.isAccessGranted()) com.halo.runtime.cognition.routine.AutomationSourceStatus.REQUIRES_SERVICE else com.halo.runtime.cognition.routine.AutomationSourceStatus.REQUIRES_PERMISSION
        NotificationReplyRegistry.shared.clear()
        lifecycle.clear()
        bridge.disconnected()
        super.onListenerDisconnected()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (connected && sbn != null) {
            NotificationReplyRegistry.shared.replace(sbn.key, sbn.notification.actions?.toList().orEmpty())
            val automationSignal = if (lifecycle.posted(sbn.key, sbn.packageName == packageName, sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)) {
                val opaque = java.util.UUID.randomUUID().toString()
                com.halo.runtime.cognition.routine.AutomationSignal(opaque,
                    com.halo.runtime.cognition.routine.AutomationSignalKind.NOTIFICATION_POSTED,
                    Instant.ofEpochMilli(sbn.postTime), "notifications",
                    com.halo.runtime.cognition.routine.AutomationTrust.UNTRUSTED_EXTERNAL,
                    mapOf("package" to com.halo.runtime.cognition.routine.AutomationValue.Text(sbn.packageName)))
            } else null
            bridge.posted(incoming(sbn), automationSignal)
            if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) {
                activeNotifications.orEmpty().filter {
                    it.groupKey == sbn.groupKey && it.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
                }.forEach { bridge.posted(incoming(it)) }
            }
        }
    }
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        if (connected) activeNotifications.orEmpty().forEach { bridge.posted(incoming(it)) }
    }
    override fun onDestroy() {
        LiveNotificationOwner.detach(this)
        if (connected) bridge.disconnected()
        NotificationReplyRegistry.shared.clear()
        lifecycle.clear()
        connected = false
        com.halo.runtime.cognition.routine.AutomationServiceSignals.notifications.availability.value = if(bridge.isAccessGranted()) com.halo.runtime.cognition.routine.AutomationSourceStatus.REQUIRES_SERVICE else com.halo.runtime.cognition.routine.AutomationSourceStatus.REQUIRES_PERMISSION
        super.onDestroy()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (connected && sbn != null) {
            // Gone means gone: a removed notification can no longer be replied to.
            NotificationReplyRegistry.shared.remove(sbn.key)
            lifecycle.removed(sbn.key)
            bridge.removed(sbn.key)
        }
    }
    private fun incoming(sbn: StatusBarNotification): IncomingNotification {
        val n = sbn.notification
        val ranking = Ranking()
        val ranked = currentRanking.getRanking(sbn.key, ranking)
        val override = if (ranked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ranking.lockscreenVisibilityOverride
        } else {
            VISIBILITY_OVERRIDE_UNAVAILABLE
        }
        return IncomingNotification(sbn.key, sbn.packageName,
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrNull(),
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            n.category, Instant.ofEpochMilli(sbn.postTime),
            n.visibility == Notification.VISIBILITY_PUBLIC &&
                (override == VISIBILITY_OVERRIDE_UNAVAILABLE || override == Notification.VISIBILITY_PUBLIC),
            n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0 && activeNotifications.orEmpty().any {
                it.key != sbn.key && it.groupKey == sbn.groupKey && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            })
    }

    private companion object {
        const val VISIBILITY_OVERRIDE_UNAVAILABLE = -1000
    }
}
