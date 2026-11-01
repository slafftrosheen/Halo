package com.halo.host.android.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import org.json.JSONObject

data class ReminderRecord(
    val id: String,
    val message: String,
    val triggerAtEpochMs: Long,
    val precision: String,
)

/** Portable reminder records; PendingIntent and alarm state are derived, never serialized. */
enum class ReminderStatus { Scheduled, Blocked, Expired, Cancelled, Fired, Failed, DeliveryBlocked, Migrated }
class ReminderRecordStore(private val context: Context) {
    private val receipts = context.getSharedPreferences("halo_reminder_receipts", Context.MODE_PRIVATE)
    private val migration = context.getSharedPreferences("halo_reminder_migration", Context.MODE_PRIVATE)
    private val migrationIssues = context.getSharedPreferences("halo_reminder_migration_issues", Context.MODE_PRIVATE)
    fun migrationFailures(): Set<String> = migrationIssues.all.keys
    fun markMigrationFailure(id: String) { check(migrationIssues.edit().putLong(id, System.currentTimeMillis()).commit()) }
    fun status(id: String): ReminderStatus? = receipts.getString(id, null)?.let { runCatching { ReminderStatus.valueOf(it) }.getOrNull() }
    fun statuses(): Map<String, String> = receipts.all.mapValues { it.value.toString() }
    fun mark(id: String, status: ReminderStatus) {
        check(receipts.edit().putString(id, status.name).commit())
    }
    fun cancel(id: String) {
        val intent = Intent(context, HaloReminderReceiver::class.java).setData(Uri.parse("halo:reminder/${Uri.encode(id)}"))
        val pending = PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { context.getSystemService(AlarmManager::class.java).cancel(pending); pending.cancel() }
        remove(id); mark(id, ReminderStatus.Cancelled)
    }
    private val preferences = context.getSharedPreferences("halo_reminders", Context.MODE_PRIVATE)

    fun all(): List<ReminderRecord> = preferences.all.values.mapNotNull { encoded ->
        runCatching {
            val json = JSONObject(encoded as String)
            ReminderRecord(json.getString("id"), json.getString("message"), json.getLong("triggerAtEpochMs"), json.getString("precision"))
        }.getOrNull()
    }

    /** Journal first, then revoke the old receiver. A crash cannot erase the only copy. */
    fun claimMigration(id: String): ReminderRecord? = synchronized(migrationLock) {
        val encoded = migration.getString(id, null) ?: preferences.getString(id, null) ?: return@synchronized null
        val json = JSONObject(encoded)
        val record = ReminderRecord(json.getString("id"), json.getString("message"), json.getLong("triggerAtEpochMs"), json.getString("precision"))
        check(migration.edit().putString(id, encoded).commit())
        check(preferences.edit().remove(id).commit())
        record
    }
    fun migrationIds(): Set<String> = synchronized(migrationLock) { preferences.all.keys + migration.all.keys }
    fun acknowledgeMigration(id: String) = synchronized(migrationLock) {
        cancel(id)
        mark(id, ReminderStatus.Migrated)
        check(migration.edit().remove(id).commit())
        check(migrationIssues.edit().remove(id).commit())
    }
    fun consumeForDelivery(id: String): ReminderRecord? = synchronized(migrationLock) {
        if (migration.contains(id)) return@synchronized null
        val value = all().find { it.id == id } ?: return@synchronized null
        remove(id)
        mark(id, ReminderStatus.Fired)
        value
    }

    fun save(record: ReminderRecord) {
        val json = JSONObject()
            .put("id", record.id)
            .put("message", record.message)
            .put("triggerAtEpochMs", record.triggerAtEpochMs)
            .put("precision", record.precision)
        check(preferences.edit().putString(record.id, json.toString()).commit()) { "Unable to save reminder" }
    }

    fun remove(id: String) { check(preferences.edit().remove(id).commit()) }

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

    fun reconcile(now: Long = System.currentTimeMillis()) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        all().forEach { record ->
            if (migration.contains(record.id)) return@forEach
            // Retire the pre-AUT-011 identity before using collision-free intent data.
            PendingIntent.getBroadcast(context, record.id.hashCode(), Intent(context, HaloReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { legacy ->
                alarms.cancel(legacy); legacy.cancel()
            }
            if (record.triggerAtEpochMs <= now) {
                cancel(record.id)
                mark(record.id, ReminderStatus.Expired)
                return@forEach
            }
            if (record.precision == "EXACT" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
                mark(record.id, ReminderStatus.Blocked)
                return@forEach
            }
            val intent = Intent(context, HaloReminderReceiver::class.java)
                .setData(Uri.parse("halo:reminder/${Uri.encode(record.id)}"))
                .putExtra("title", "Halo Reminder")
                .putExtra("text", record.message)
                .putExtra("reminder_id", record.id)
            val pending = PendingIntent.getBroadcast(context, record.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            runCatching {
                if (record.precision == "EXACT") alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, record.triggerAtEpochMs, pending)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, record.triggerAtEpochMs, pending)
                mark(record.id, ReminderStatus.Scheduled)
            }.onFailure { mark(record.id, if (it is SecurityException) ReminderStatus.Blocked else ReminderStatus.Failed) }
        }
    }
    private companion object { val migrationLock = Any() }
}
