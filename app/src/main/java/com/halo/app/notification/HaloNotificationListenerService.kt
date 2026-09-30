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
    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        val active = activeNotifications.orEmpty()
        // MINING-P1-001: capture live RemoteInput reply actions for currently posted notifications.
        // Only the live platform objects are held, never persisted.
        active.forEach { sbn ->
            NotificationReplyRegistry.shared.replace(sbn.key, sbn.notification.actions?.toList().orEmpty())
        }
        bridge.connected(active.map(::incoming))
    }
    override fun onListenerDisconnected() {
        connected = false
        NotificationReplyRegistry.shared.clear()
        bridge.disconnected()
        super.onListenerDisconnected()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (connected && sbn != null) {
            NotificationReplyRegistry.shared.replace(sbn.key, sbn.notification.actions?.toList().orEmpty())
            bridge.posted(incoming(sbn))
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
        if (connected) bridge.disconnected()
        NotificationReplyRegistry.shared.clear()
        connected = false
        super.onDestroy()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (connected && sbn != null) {
            // Gone means gone: a removed notification can no longer be replied to.
            NotificationReplyRegistry.shared.remove(sbn.key)
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
