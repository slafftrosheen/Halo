package com.halo.host.android.capability

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.halo.host.android.reminder.HaloReminderReceiver
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.MemoryTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task

class AndroidMemoryCapability(private val context: Context) : Capability {
    override val id: String = "capability.memory.android"

    override suspend fun supports(task: Task): Boolean = task is MemoryTask

    override suspend fun execute(task: Task): Outcome {
        require(task is MemoryTask)
        return try {
            val text = task.memoryAction.ifBlank { "Halo Reminder" }
            val delayMs = parseDelayMillis(text)
            val triggerTime = System.currentTimeMillis() + delayMs

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, HaloReminderReceiver::class.java).apply {
                putExtra("title", "Halo Reminder")
                putExtra("text", text)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Outcome.Success("Reminder set")
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to set reminder")
        }
    }

    private fun parseDelayMillis(text: String): Long {
        val lower = text.lowercase()
        val minutesMatch = Regex("(\\d+)\\s*min").find(lower)
        if (minutesMatch != null) {
            val minutes = minutesMatch.groupValues[1].toLongOrNull() ?: 1L
            return minutes * 60 * 1000L
        }
        val hoursMatch = Regex("(\\d+)\\s*hour").find(lower)
        if (hoursMatch != null) {
            val hours = hoursMatch.groupValues[1].toLongOrNull() ?: 1L
            return hours * 60 * 60 * 1000L
        }
        return 10 * 60 * 1000L // Default 10 minutes if unspecified
    }
}
