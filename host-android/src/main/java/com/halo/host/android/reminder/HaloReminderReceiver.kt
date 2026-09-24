package com.halo.host.android.reminder

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HaloReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = ReminderRecordStore(context)
        val id = intent.getStringExtra("reminder_id") ?: return
        if (store.all().none { it.id == id }) return
        // Consume before delivery; a duplicate broadcast must not post a second reminder.
        store.remove(id)
        store.mark(id, ReminderStatus.Fired)
        val title = intent.getStringExtra("title") ?: "Halo Reminder"
        val text = intent.getStringExtra("text") ?: "You have a reminder."

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "halo_reminders"

        val channel = NotificationChannel(
            channelId,
            "Halo Reminders",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        if (!notificationManager.areNotificationsEnabled() ||
            (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)) {
            store.mark(id, ReminderStatus.DeliveryBlocked)
            return
        }
        try { notificationManager.notify(id, 0, notification) }
        catch (_: SecurityException) { store.mark(id, ReminderStatus.DeliveryBlocked) }
    }
}
