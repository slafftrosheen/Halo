package com.halo.app.everyday.organizer.android

import android.content.Context
import com.halo.app.everyday.organizer.*
import com.halo.host.android.reminder.ReminderRecordStore
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Stable identities and a legacy-side journal make migration retryable after either process dies. */
class LegacyOrganizerReminders(context: Context, private val repository: OrganizerRepository) {
    private val legacy = ReminderRecordStore(context)
    suspend fun migrate() {
        for (id in legacy.migrationIds()) {
          try {
            val source = legacy.claimMigration(id) ?: continue
            val targetId = "legacy-reminder:${UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8))}"
            if (repository.get(targetId) == null) {
                val at = Instant.ofEpochMilli(source.triggerAtEpochMs)
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val record = repository.save(OrganizerRecord(targetId, OrganizerKind.REMINDER,
                    source.message.take(200).ifBlank { "Halo" }, details = source.message.take(16000),
                    time = OrganizerTime(at.atZone(zone).toLocalDateTime(), zoneId = zone.id), createdAt = now), null)
                if (at < now) repository.putReceipt(DeliveryReceipt("$targetId@${at.toEpochMilli()}", targetId, record.version,
                    at, DeliveryState.MISSED, now, detail = "This legacy reminder was already due before migration"))
            }
            legacy.acknowledgeMigration(id)
          } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                // Preserve the original/journal and expose the issue without blocking unrelated alarms.
                runCatching { legacy.markMigrationFailure(id) }
                android.util.Log.w("OrganizerMigration", "A legacy reminder remains available for migration retry")
            }
        }
    }
}
