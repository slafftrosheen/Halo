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

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
