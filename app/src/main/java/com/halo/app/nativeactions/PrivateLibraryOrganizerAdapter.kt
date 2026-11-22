package com.halo.app.nativeactions

import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.EverydayLocalDataCleanup
import com.halo.app.data.everyday.OrganizerChangeEntity
import com.halo.app.data.everyday.OrganizerCodec
import com.halo.app.data.everyday.OrganizerDeliveryEntity
import com.halo.app.data.everyday.OrganizerOccurrenceEntity
import com.halo.app.data.everyday.OrganizerScheduleEntity
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.everyday.EverydayReadAuthority
import com.halo.app.everyday.organizer.OrganizerCalendar
import com.halo.app.everyday.organizer.OrganizerDeliveryCoordinator
import com.halo.app.everyday.organizer.OrganizerKind
import com.halo.app.everyday.organizer.OrganizerRecord
import com.halo.app.everyday.organizer.OrganizerStatus
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal class PrivateLibraryOrganizerAdapter(
    private val database: AppDatabase,
    private val delivery: OrganizerDeliveryCoordinator,
    private val enqueueSchedules: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val organizer get() = database.organizerDao()

    suspend fun <T> withWithdrawal(versions: Map<String, Long>, action: suspend () -> T): T =
        delivery.withPrivateWithdrawal(versions, action)

    suspend fun capture(target: PrivateLibraryTarget.Organizer): PrivateLibraryItem {
        val row = requireNotNull(organizer.get(target.id))
        val record = OrganizerCodec.decode(row.body)
        check(record.title == target.title && record.version == target.version && record.deletedAt == null)
        return snapshot(record, row.body)
    }

    suspend fun captureLinked(id: String): PrivateLibraryItem {
        val row = requireNotNull(organizer.get(id))
        return snapshot(OrganizerCodec.decode(row.body), row.body)
    }

    private suspend fun snapshot(record: OrganizerRecord, body: String): PrivateLibraryItem {
        check(record.calendar == OrganizerCalendar.PERSONAL && record.calendarUid == null &&
            record.kind in setOf(OrganizerKind.EVENT, OrganizerKind.TASK))
        val contents = JSONObject()
            .put("record", body)
            .put("changes", JSONArray(organizer.overrides(record.id).map { it.body }))
            .put("deliveries", JSONArray(organizer.receipts(record.id).sortedBy { it.occurrenceKey }.map(::encodeDelivery)))
        return PrivateLibraryItem(
            header = WorkspaceObjectEntity(
                id = record.id, type = "PRIVATE_ORGANIZER", title = record.title, createdAt = record.createdAt.toEpochMilli(),
                updatedAt = record.updatedAt.toEpochMilli(), source = "ORGANIZER", lifecycle = "ACTIVE",
                inInbox = false, indexingState = "NONE", contentRef = "organizer:${record.id}",
            ),
            note = null, blocks = emptyList(), memberships = emptyList(), favorite = null,
            alias = null, file = null, organizer = contents,
        )
    }

    fun record(item: PrivateLibraryItem): OrganizerRecord = OrganizerCodec.decode(requireNotNull(item.organizer).getString("record"))

    fun preview(item: PrivateLibraryItem): String {
        val record = record(item)
        return listOfNotNull(
            record.time?.let { "${it.start.toLocalDate()} · ${it.start.toLocalTime()} · ${it.zoneId}" },
            record.location.takeIf(String::isNotBlank),
            record.details.takeIf(String::isNotBlank),
        ).joinToString("\n\n")
    }

    suspend fun withdraw(item: PrivateLibraryItem) {
        val record = record(item)
        check(organizer.withdrawForPrivate(record.id, record.version) == 1)
        organizer.removePrivateHistory(record.id)
        EverydayLocalDataCleanup.notePurged(database, record.id)
    }

    suspend fun restore(item: PrivateLibraryItem) {
        val data = requireNotNull(item.organizer)
        val original = OrganizerCodec.decode(data.getString("record"))
        val parent = original.parentId?.let { organizer.get(it) }?.takeIf { it.deletedAt == null }
            ?.takeIf { OrganizerCodec.decode(it.body).status == OrganizerStatus.ACTIVE }
        val next = original.copy(
            parentId = parent?.id, version = Math.addExact(original.version, 1L),
            updatedAt = Instant.ofEpochMilli(clock()),
        )
        organizer.insert(OrganizerCodec.entity(next))
        val changes = data.getJSONArray("changes")
        for (index in 0 until changes.length()) {
            val body = changes.getString(index)
            val change = OrganizerCodec.override(body)
            check(change.recordId == next.id)
            organizer.putOverride(OrganizerOccurrenceEntity(next.id, change.originalStart.toEpochMilli(), body))
        }
        val deliveries = data.getJSONArray("deliveries")
        for (index in 0 until deliveries.length()) {
            val row = deliveries.getJSONObject(index)
            val archivedVersion = row.getLong("version")
            val savedVersion = if (archivedVersion == original.version) next.version else archivedVersion
            organizer.putReceipt(OrganizerDeliveryEntity(
                occurrenceKey = row.getString("key"), recordId = next.id, recordVersion = savedVersion,
                triggerAt = row.getLong("trigger"), state = row.getString("state"), updatedAt = row.getLong("updated"),
                detail = row.optionalString("detail"), snoozedUntil = row.optionalLong("snoozedUntil"),
                snoozeElapsedDeadlineMs = row.optionalLong("snoozeDeadline"), snoozeBootId = row.optionalString("snoozeBoot"),
            ))
        }
        organizer.enqueue(OrganizerScheduleEntity(next.id, next.version))
        organizer.change(OrganizerChangeEntity(recordId = next.id, version = next.version,
            action = "restored", at = next.updatedAt.toEpochMilli()))
        EverydayReadAuthority.invalidate()
    }

    fun enqueueRestored() { runCatching(enqueueSchedules) }

    private fun encodeDelivery(row: OrganizerDeliveryEntity) = JSONObject()
        .put("key", row.occurrenceKey).put("version", row.recordVersion).put("trigger", row.triggerAt)
        .put("state", row.state).put("updated", row.updatedAt).put("detail", row.detail)
        .put("snoozedUntil", row.snoozedUntil).put("snoozeDeadline", row.snoozeElapsedDeadlineMs)
        .put("snoozeBoot", row.snoozeBootId)

    private fun JSONObject.optionalString(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.optionalLong(key: String) = if (isNull(key)) null else getLong(key)
}
