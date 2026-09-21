package com.halo.host.android.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject

data class ReminderRecord(
    val id: String,
    val message: String,
    val triggerAtEpochMs: Long,
    val precision: String,
)

/** Portable reminder records; PendingIntent and alarm state are derived, never serialized. */
class ReminderRecordStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("halo_reminders", Context.MODE_PRIVATE)

    fun all(): List<ReminderRecord> = preferences.all.values.mapNotNull { encoded ->
        runCatching {
            val json = JSONObject(encoded as String)
            ReminderRecord(json.getString("id"), json.getString("message"), json.getLong("triggerAtEpochMs"), json.getString("precision"))
        }.getOrNull()
    }

    fun save(record: ReminderRecord) {
        val json = JSONObject()
            .put("id", record.id)
            .put("message", record.message)
            .put("triggerAtEpochMs", record.triggerAtEpochMs)
            .put("precision", record.precision)
        check(preferences.edit().putString(record.id, json.toString()).commit()) { "Unable to save reminder" }
    }

    fun remove(id: String) { preferences.edit().remove(id).apply() }

    fun replace(records: List<ReminderRecord>) {
        val editor = preferences.edit().clear()
        records.forEach { record ->
            editor.putString(record.id, JSONObject()
                .put("id", record.id)
                .put("message", record.message)
                .put("triggerAtEpochMs", record.triggerAtEpochMs)
                .put("precision", record.precision).toString())
        }
        check(editor.commit()) { "Unable to restore reminders" }
    }

    fun reconcile() {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        all().forEach { record ->
            if (record.triggerAtEpochMs <= System.currentTimeMillis()) {
                remove(record.id)
                return@forEach
            }
            if (record.precision == "EXACT" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) return@forEach
            val intent = Intent(context, HaloReminderReceiver::class.java)
                .putExtra("title", "Halo Reminder")
                .putExtra("text", record.message)
                .putExtra("reminder_id", record.id)
            val pending = PendingIntent.getBroadcast(context, record.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            runCatching {
                if (record.precision == "EXACT") alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, record.triggerAtEpochMs, pending)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, record.triggerAtEpochMs, pending)
            }
        }
    }
}
