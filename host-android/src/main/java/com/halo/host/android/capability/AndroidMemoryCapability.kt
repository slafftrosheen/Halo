package com.halo.host.android.capability

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.halo.host.android.reminder.HaloReminderReceiver
import com.halo.host.android.reminder.ReminderRecord
import com.halo.host.android.reminder.ReminderRecordStore
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.MemoryTask
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.Task
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

data class ReminderSpec(
    val message: String,
    val triggerAt: Instant,
    val precision: ReminderPrecision
)

enum class ReminderPrecision { EXACT, FLEXIBLE }

class AndroidMemoryCapability(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault()
) : Capability {
    override val id: String = "capability.memory.android"

    override suspend fun supports(task: Task): Boolean = task is MemoryTask

    override suspend fun execute(task: Task): Outcome {
        require(task is MemoryTask)
        return try {
            val spec = parseReminder(task.memoryAction)
                ?: return Outcome.PermanentFailure("I couldn't understand when to schedule that reminder")
            val triggerTime = spec.triggerAt.toEpochMilli()
            if (triggerTime <= clock.millis()) {
                return Outcome.PermanentFailure("That reminder time is in the past")
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (spec.precision == ReminderPrecision.EXACT &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                return Outcome.PermanentFailure("NEEDS_EXACT_ALARM_ACCESS")
            }
            val intent = Intent(context, HaloReminderReceiver::class.java).apply {
                putExtra("title", "Halo Reminder")
                putExtra("text", spec.message)
            }
            val reminderId = "${spec.message}|$triggerTime"
            intent.putExtra("reminder_id", reminderId)
            val stableId = reminderId.hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                stableId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (spec.precision == ReminderPrecision.EXACT) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            ReminderRecordStore(context).save(ReminderRecord(reminderId, spec.message, triggerTime, spec.precision.name))
            Outcome.Success("Reminder set")
        } catch (e: Exception) {
            Outcome.RetryableFailure(e.message ?: "Failed to set reminder")
        }
    }

    private fun parseReminder(raw: String): ReminderSpec? {
        val fields = raw.split(REMINDER_FIELD_SEPARATOR, limit = 2)
        val message = fields.firstOrNull()?.trim().orEmpty()
        val expression = fields.getOrNull(1)?.trim()
            ?: relativeExpression.find(raw)?.value
            ?: absoluteExpression.find(raw)?.value
            ?: return null
        if (message.isBlank()) return null
        val now = ZonedDateTime.now(clock).withZoneSameInstant(zoneId)

        relativeExpression.matchEntire(expression.lowercase(locale))?.let { match ->
            val amount = match.groupValues[1].toLongOrNull() ?: return null
            val duration = if (match.groupValues[2].startsWith("hour")) {
                Duration.ofHours(amount)
            } else Duration.ofMinutes(amount)
            return ReminderSpec(message, now.toInstant().plus(duration), ReminderPrecision.EXACT)
        }

        val match = absoluteExpression.matchEntire(expression.lowercase(locale)) ?: return null
        val day = match.groupValues[1]
        var hour = match.groupValues[2].toIntOrNull() ?: return null
        val minute = match.groupValues[3].ifBlank { "0" }.toIntOrNull() ?: return null
        val period = match.groupValues[4]
        if (hour !in 0..23 || minute !in 0..59) return null
        if (period.isNotBlank()) {
            if (hour !in 1..12) return null
            if (period == "pm" && hour < 12) hour += 12
            if (period == "am" && hour == 12) hour = 0
        }
        val date = when (day) {
            "tomorrow" -> now.toLocalDate().plusDays(1)
            else -> now.toLocalDate()
        }
        val trigger = ZonedDateTime.of(date, LocalTime.of(hour, minute), zoneId)
        return ReminderSpec(message, trigger.toInstant(), ReminderPrecision.EXACT)
    }

    private companion object {
        const val REMINDER_FIELD_SEPARATOR = "\u001F"
        val relativeExpression = Regex("(?:in\\s+)?(\\d+)\\s*(minutes?|hours?)", RegexOption.IGNORE_CASE)
        val absoluteExpression = Regex("(?:(today|tomorrow|tonight)\\s+)?(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
    }
}
