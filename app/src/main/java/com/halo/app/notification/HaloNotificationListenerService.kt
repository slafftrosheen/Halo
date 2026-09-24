package com.halo.app.notification

import android.app.Notification
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
        bridge.connected(activeNotifications.orEmpty().map(::incoming))
    }
    override fun onListenerDisconnected() {
        connected = false
        bridge.disconnected()
        super.onListenerDisconnected()
    }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (connected && sbn != null) {
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
        connected = false
        super.onDestroy()
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (connected && sbn != null) bridge.removed(sbn.key)
    }
    private fun incoming(sbn: StatusBarNotification): IncomingNotification {
        val n = sbn.notification
        val ranking = Ranking()
        val ranked = currentRanking.getRanking(sbn.key, ranking)
        val override = if (ranked) ranking.lockscreenVisibilityOverride else (-1000)
        return IncomingNotification(sbn.key, sbn.packageName,
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrNull(),
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))?.toString(),
            n.category, Instant.ofEpochMilli(sbn.postTime),
            n.visibility == Notification.VISIBILITY_PUBLIC &&
                (override == (-1000) || override == Notification.VISIBILITY_PUBLIC),
            n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            n.flags and Notification.FLAG_GROUP_SUMMARY != 0 && activeNotifications.orEmpty().any {
                it.key != sbn.key && it.groupKey == sbn.groupKey && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            })
    }
}
