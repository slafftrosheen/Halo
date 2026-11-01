package com.halo.app.everyday.organizer.android

import android.app.AlarmManager
import android.content.Context
import com.halo.app.everyday.organizer.PlannedDelivery
import java.time.Instant
import org.json.JSONObject

interface OrganizerAlarmPlatform {
    fun cancel(lane: String)
    /** Return the precision of the actual accepted Android call, including a permission-race fallback. */
    fun schedule(lane: String, plan: PlannedDelivery): Boolean
}

/** Three bounded wakes keep alarm-clock/exact deadlines independent of approximate notifications. */
class OrganizerAlarmQueue(private val context: Context, private val platform: OrganizerAlarmPlatform = AndroidOrganizerAlarmPlatform(context)) {
    private val disk = context.createDeviceProtectedStorageContext().getSharedPreferences(STORE, Context.MODE_PRIVATE)
    fun reset() = synchronized(lock) {
        check(disk.edit().clear().commit())
        lanes.forEach(platform::cancel)
        platform.cancel("next-due-item") // Retire the original single-wake identity if present.
    }
    fun replace(id: String, plan: PlannedDelivery?): Boolean = synchronized(lock) {
        val editor = disk.edit()
        if (plan == null) editor.remove(id) else editor.putString(id, JSONObject().put("key", plan.occurrenceKey)
            .put("id", plan.recordId).put("version", plan.recordVersion).put("at", plan.triggerAt.toEpochMilli())
            .put("exact", plan.exactRequested).put("clock", plan.alarmClock).put("elapsed", plan.elapsedDeadlineMs).toString())
        check(editor.commit()) { "Unable to persist Android scheduling intent" }
        val plans = disk.all.values.map { JSONObject(it as String) }.map { json -> PlannedDelivery(json.getString("key"),
            json.getString("id"), json.getLong("version"), Instant.ofEpochMilli(json.getLong("at")),
            json.getBoolean("exact"), json.getBoolean("clock"), if (json.isNull("elapsed")) null else json.getLong("elapsed")) }
        var requestedExact = false
        for (lane in lanes) {
            val next = plans.filter { bucket(it) == lane }.minWithOrNull(compareBy<PlannedDelivery> { it.triggerAt }.thenBy { it.occurrenceKey })
            platform.cancel(lane)
            if (next != null) {
                val exact = platform.schedule(lane, next)
                if (plan != null && bucket(plan) == lane) requestedExact = exact
            }
        }
        requestedExact
    }
    companion object {
        private const val STORE = "organizer_alarm_queue_v1"
        private val lanes = listOf("alarm-clock", "precise", "approximate")
        private val lock = Any()
        private fun bucket(plan: PlannedDelivery) = if (plan.alarmClock) "alarm-clock" else if (plan.exactRequested) "precise" else "approximate"
        fun revoke(context: Context, id: String) = synchronized(lock) {
            check(context.createDeviceProtectedStorageContext().getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().remove(id).commit())
        }
    }
}

private class AndroidOrganizerAlarmPlatform(private val context: Context) : OrganizerAlarmPlatform {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    override fun cancel(lane: String) {
        OrganizerIntents.alarm(context, lane, noCreate = true)?.let { alarms.cancel(it); it.cancel() }
    }
    override fun schedule(lane: String, plan: PlannedDelivery): Boolean {
        val pending = requireNotNull(OrganizerIntents.alarm(context, lane, plan))
        val exact = plan.exactRequested && OrganizerPermissionAccess(context).current().exact
        val type = if (plan.elapsedDeadlineMs != null) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
        val at = plan.elapsedDeadlineMs ?: plan.triggerAt.toEpochMilli()
        return try {
            when {
                exact && plan.alarmClock -> alarms.setAlarmClock(AlarmManager.AlarmClockInfo(plan.triggerAt.toEpochMilli(),
                    OrganizerIntents.open(context, plan.occurrenceKey, plan.recordVersion)), pending)
                exact -> alarms.setExactAndAllowWhileIdle(type, at, pending)
                else -> alarms.setAndAllowWhileIdle(type, at, pending)
            }
            exact
        } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(type, at, pending); false }
    }
}
