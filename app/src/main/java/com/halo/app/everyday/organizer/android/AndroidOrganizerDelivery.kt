package com.halo.app.everyday.organizer.android

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.halo.app.R
import com.halo.app.everyday.organizer.*

/** Collision-free identities include the full record ID; extras alone never identify a PendingIntent. */
object OrganizerIntents {
    const val FIRE = "com.halo.app.organizer.FIRE"
    const val STOP = "com.halo.app.organizer.STOP"
    const val SNOOZE = "com.halo.app.organizer.SNOOZE"
    const val KEY = "occurrence"
    const val VERSION = "version"
    const val TRIGGER = "trigger"
    fun alarm(context: Context, id: String, delivery: PlannedDelivery? = null, noCreate: Boolean = false): PendingIntent? {
        val intent = Intent(context, OrganizerAlarmReceiver::class.java).setAction(FIRE)
            .setData(Uri.Builder().scheme("halo-organizer").authority("schedule").appendPath(id).build())
        delivery?.let { intent.putExtra(KEY, it.occurrenceKey).putExtra(VERSION, it.recordVersion).putExtra(TRIGGER, it.triggerAt.toEpochMilli()) }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or
            if (noCreate) PendingIntent.FLAG_NO_CREATE else PendingIntent.FLAG_UPDATE_CURRENT)
    }
    fun action(context: Context, action: String, receipt: DeliveryReceipt): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, OrganizerAlarmReceiver::class.java).setAction(action)
            .setData(Uri.Builder().scheme("halo-organizer").authority("action").appendPath(action)
                .appendPath(receipt.occurrenceKey).appendPath(receipt.recordVersion.toString()).appendPath(receipt.triggerAt.toEpochMilli().toString()).build())
            .putExtra(KEY, receipt.occurrenceKey).putExtra(VERSION, receipt.recordVersion).putExtra(TRIGGER, receipt.triggerAt.toEpochMilli()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun open(context: Context, key: String, version: Long): PendingIntent = PendingIntent.getActivity(context, 0,
        Intent(context, OrganizerAlarmActivity::class.java)
            .setData(Uri.Builder().scheme("halo-organizer").authority("delivery").appendPath(key).appendPath(version.toString()).build())
            .putExtra(KEY, key).putExtra(VERSION, version).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

data class OrganizerAccess(val notifications: Boolean, val exact: Boolean, val fullScreen: Boolean)

class OrganizerPermissionAccess(private val context: Context) {
    fun current(): OrganizerAccess {
        val notifications = context.getSystemService(NotificationManager::class.java)
        return OrganizerAccess(notifications.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
            Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            Build.VERSION.SDK_INT < 34 || notifications.canUseFullScreenIntent())
    }
    fun exactIntent() = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    fun notificationIntent() = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    fun fullScreenIntent(): Intent = if (Build.VERSION.SDK_INT >= 34)
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")) else notificationIntent()
}

/** AlarmManager handles timing; Android's alarm notification owns bounded sound, with no polling or wake lock. */
class AndroidOrganizerDelivery(private val context: Context) : OrganizerDeliveryPort {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val access = OrganizerPermissionAccess(context)

    override suspend fun resetSchedules() { OrganizerAlarmQueue(context).reset() }
    override suspend fun cancelSchedule(recordId: String) { OrganizerAlarmQueue(context).replace(recordId, null) }
    override suspend fun schedule(record: OrganizerRecord, delivery: PlannedDelivery): DeliveryOutcome {
        val exact = OrganizerAlarmQueue(context).replace(record.id, delivery)
        val state = access.current()
        return if (!state.notifications) DeliveryOutcome(DeliveryState.NOTIFICATIONS_BLOCKED,
            "Notifications are off. The item is saved, but Android cannot alert you")
        else if (exact) DeliveryOutcome(DeliveryState.EXACT)
        else DeliveryOutcome(DeliveryState.APPROXIMATE, if (delivery.exactRequested)
            "Exact alarm access is off. Android may deliver this later; allow exact alarms for precise timing"
            else "Android may delay this notification to save battery")
    }

    override suspend fun present(record: OrganizerRecord, receipt: DeliveryReceipt, recovered: Boolean): DeliveryOutcome {
        val ringing = record.kind in setOf(OrganizerKind.ALARM, OrganizerKind.TIMER)
        val channelId = if (ringing) ALARMS else REMINDERS
        ensureChannels()
        if (!access.current().notifications || notifications.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE)
            return DeliveryOutcome(DeliveryState.NOTIFICATIONS_BLOCKED, "Allow Halo notifications and this notification category to receive alerts")
        val unlocked = context.getSystemService(android.os.UserManager::class.java).isUserUnlocked
        val title = if (unlocked) record.title else context.getString(R.string.organizer_alarm_private_title)
        val builder = Notification.Builder(context, channelId).setSmallIcon(R.drawable.halo_ic_timer)
            .setContentTitle(title).setContentText(context.getString(if (ringing) R.string.organizer_alarm_active else R.string.organizer_reminder_due))
            .setCategory(if (ringing) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true).setAutoCancel(false)
            .setContentIntent(OrganizerIntents.open(context, receipt.occurrenceKey, receipt.recordVersion))
            .setPublicVersion(Notification.Builder(context, channelId).setSmallIcon(R.drawable.halo_ic_timer)
                .setContentTitle(context.getString(R.string.organizer_alarm_private_title)).build())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.organizer_delivery_stop),
                OrganizerIntents.action(context, OrganizerIntents.STOP, receipt)).build())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.organizer_delivery_snooze),
                OrganizerIntents.action(context, OrganizerIntents.SNOOZE, receipt)).build())
            .setDeleteIntent(OrganizerIntents.action(context, OrganizerIntents.STOP, receipt))
        if (ringing) {
            builder.setOngoing(true).setTimeoutAfter(10 * 60 * 1000L)
            if (access.current().fullScreen) builder.setFullScreenIntent(OrganizerIntents.open(context,
                receipt.occurrenceKey, receipt.recordVersion), true)
        }
        val notification = builder.build()
        if (ringing) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        return try {
            notifications.notify(notificationTag(receipt.occurrenceKey, receipt.recordVersion), 0, notification)
            DeliveryOutcome(DeliveryState.FIRED, if (ringing && !access.current().fullScreen)
                "Alarm notification posted. Full-screen access is off; sound follows Android notification settings" else null)
        } catch (_: SecurityException) { DeliveryOutcome(DeliveryState.NOTIFICATIONS_BLOCKED, "Android notification access changed before delivery") }
    }

    override suspend fun dismiss(occurrenceKey: String, recordVersion: Long) {
        notifications.cancel(notificationTag(occurrenceKey, recordVersion), 0)
    }
    private fun ensureChannels() {
        notifications.createNotificationChannel(NotificationChannel(REMINDERS, context.getString(R.string.organizer_reminder_channel), NotificationManager.IMPORTANCE_HIGH))
        notifications.createNotificationChannel(NotificationChannel(ALARMS, context.getString(R.string.organizer_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
    }
    companion object {
        const val ALARMS = "halo_organizer_alarms_v1"
        const val REMINDERS = "halo_organizer_reminders_v1"
        fun notificationTag(key: String, version: Long) = "organizer:$key:$version"
    }
}
