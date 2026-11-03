package com.halo.app.data.everyday

import androidx.room.withTransaction
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.organizer.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.map

/** One transaction owns record, revision, change history and the intent to update OS delivery. */
class RoomOrganizerRepository(private val database: AppDatabase, private val clock: Clock = Clock.systemUTC(),
    private val deviceZone: () -> ZoneId = { ZoneId.systemDefault() },
    private val schedulesChanged: () -> Unit = {},
    private val beforeRecordChange: (String) -> Unit = {}) : OrganizerRepository {
    private val dao get() = database.organizerDao()
    override fun observe() = dao.observe().map { rows -> rows.map { OrganizerCodec.decode(it.body) } }
    override suspend fun records(includeDeleted: Boolean) = dao.records(includeDeleted).map { OrganizerCodec.decode(it.body) }
    override suspend fun get(id: String) = dao.get(id)?.let { OrganizerCodec.decode(it.body) }

    override suspend fun save(record: OrganizerRecord, expectedVersion: Long?, resetOccurrences: Boolean): OrganizerRecord {
        return saveRecord(record, expectedVersion, resetOccurrences, false)
    }
    override suspend fun promoteInbox(record: OrganizerRecord, expectedVersion: Long): OrganizerRecord = saveRecord(record, expectedVersion, false, true)
    private suspend fun saveRecord(record: OrganizerRecord, expectedVersion: Long?, resetOccurrences: Boolean, promotion: Boolean): OrganizerRecord {
        record.validate()
        val saved = database.withTransaction {
            val previous = get(record.id)
            if (previous == null) {
                if (expectedVersion != null) throw OrganizerConflict("Record no longer exists")
                check(dao.count() < 10000) { "Organizer storage limit reached; permanently remove unneeded items from trash" }
                require(record.deletedAt == null)
            } else {
                if (expectedVersion != previous.version || previous.deletedAt != null) throw OrganizerConflict("Record changed; reopen it before saving")
                if (promotion) {
                    require(previous.kind == OrganizerKind.TASK && previous.commitment.purpose == CommitmentPurpose.INBOX && previous.commitment.execution == null)
                    require(record.kind in setOf(OrganizerKind.TASK, OrganizerKind.EVENT, OrganizerKind.REMINDER) && record.commitment.purpose != CommitmentPurpose.INBOX)
                    require(previous.kind == record.kind || records().none { it.parentId == previous.id }) { "Move or complete the Inbox subtasks before promoting this item" }
                    require(dao.overrides(record.id).isEmpty()) { "Review occurrence changes before promoting this capture" }
                } else require(previous.kind == record.kind) { "A record's instrument cannot change" }
                require(record.deletedAt == null) { "Use the delete operation" }
                require(record.createdAt == previous.createdAt)
                if (previous.commitment.execution != null && record.commitment.execution?.planId == previous.commitment.execution.planId &&
                    (previous.title != record.title || previous.details != record.details || previous.time != record.time || previous.repeat != record.repeat ||
                        previous.links != record.links || previous.commitment.actions != record.commitment.actions || previous.commitment.owner != record.commitment.owner ||
                        previous.commitment.intent != record.commitment.intent || previous.commitment.sources != record.commitment.sources))
                    throw OrganizerConflict("Stop the reviewed Halo work before changing its plan or context")
                // A series edit cannot silently discard individually moved/completed occurrences.
                if ((previous.time != record.time || previous.repeat != record.repeat) && dao.overrides(record.id).isNotEmpty() && !resetOccurrences)
                    throw OrganizerConflict("This series has occurrence changes. Review them and explicitly choose whether to reset them")
            }
            validateParent(record)
            if (record.status == OrganizerStatus.COMPLETED && records().any { it.parentId == record.id && it.status == OrganizerStatus.ACTIVE })
                throw OrganizerConflict("Complete the remaining subtasks first")
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(record.id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            if (resetOccurrences && previous != null && (previous.time != record.time || previous.repeat != record.repeat)) dao.clearOverrides(record.id)
            val now = Instant.ofEpochMilli(clock.millis())
            val next = OrganizerCodec.decode(OrganizerCodec.encode(record.copy(version = (previous?.version ?: 0) + 1,
                createdAt = previous?.createdAt ?: now, updatedAt = now)))
            if (previous == null) dao.insert(OrganizerCodec.entity(next)) else check(dao.update(OrganizerCodec.entity(next)) == 1)
            changed(next, if (previous == null) "created" else "updated")
            next
        }
        // A scheduling enqueue failure never rolls back an already durable saved record/outbox.
        runCatching(schedulesChanged)
        return saved
    }
    private suspend fun validateParent(record: OrganizerRecord) {
        var parent = record.parentId
        val visited = mutableSetOf(record.id)
        while (parent != null) {
            require(visited.add(parent) && visited.size <= 16) { "Subtasks cannot form a cycle or exceed 15 levels" }
            val value = get(parent) ?: throw OrganizerConflict("Parent task no longer exists")
            require(value.kind == OrganizerKind.TASK && value.deletedAt == null && value.status == OrganizerStatus.ACTIVE) { "Parent task must be active" }
            parent = value.parentId
        }
    }
    private suspend fun changed(record: OrganizerRecord, action: String) {
        com.halo.app.everyday.EverydayReadAuthority.invalidate()
        dao.enqueue(OrganizerScheduleEntity(record.id, record.version))
        dao.change(OrganizerChangeEntity(recordId = record.id, version = record.version, action = action, at = record.updatedAt.toEpochMilli()))
        dao.pruneChanges(record.updatedAt.minusSeconds(90L * 86400).toEpochMilli())
    }
    override suspend fun delete(id: String, expectedVersion: Long): OrganizerRecord {
        val next = database.withTransaction {
            val old = get(id) ?: throw OrganizerConflict("Record no longer exists")
            if (old.version != expectedVersion || old.deletedAt != null) throw OrganizerConflict("Record changed; reopen before deleting")
            if (records().any { it.parentId == id }) throw OrganizerConflict("Delete or move the subtasks first")
            val now = Instant.ofEpochMilli(clock.millis())
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            old.copy(version = old.version + 1, updatedAt = now, deletedAt = now).also {
                check(dao.update(OrganizerCodec.entity(it)) == 1); changed(it, "deleted")
            }
        }
        runCatching(schedulesChanged); return next
    }
    override suspend fun restore(id: String, deletedVersion: Long): OrganizerRecord {
        val next = database.withTransaction {
            val old = get(id) ?: throw OrganizerConflict("Record no longer exists")
            if (old.version != deletedVersion || old.deletedAt == null) throw OrganizerConflict("This deletion can no longer be undone")
            validateParent(old)
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            old.copy(version = old.version + 1, updatedAt = Instant.ofEpochMilli(clock.millis()), deletedAt = null).also {
                check(dao.update(OrganizerCodec.entity(it)) == 1); changed(it, "restored")
            }
        }
        runCatching(schedulesChanged); return next
    }
    override suspend fun purgeDeleted(id: String, deletedVersion: Long) = database.withTransaction {
        val old = get(id) ?: throw OrganizerConflict("Record no longer exists")
        if (old.version != deletedVersion || old.deletedAt == null) throw OrganizerConflict("Only the reviewed item in trash can be permanently removed")
        if (id in dao.pending()) throw ScheduleCancellationPending()
        if (records(includeDeleted = true).any { it.parentId == id }) throw OrganizerConflict("Move or permanently remove the subtasks first")
        com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
        com.halo.app.everyday.EverydayReadAuthority.invalidate()
        check(dao.purgeDeleted(id, deletedVersion) == 1)
        dao.change(OrganizerChangeEntity(recordId = id, version = deletedVersion + 1, action = "purged", at = clock.millis()))
    }
    override suspend fun overrides(recordId: String) = dao.overrides(recordId).map { OrganizerCodec.override(it.body) }
    override suspend fun saveOverride(exception: OccurrenceOverride, expectedRecordVersion: Long): OrganizerRecord {
        val next = database.withTransaction {
            val old = get(exception.recordId) ?: throw OrganizerConflict("Record no longer exists")
            if (old.version != expectedRecordVersion || old.deletedAt != null || old.status != OrganizerStatus.ACTIVE)
                throw OrganizerConflict("Record changed; reopen this occurrence")
            require(old.repeat != null && OrganizerRecurrence.contains(old, exception.originalStart, deviceZone())) { "Occurrence does not belong to this series" }
            require(exception.disposition != OccurrenceDisposition.COMPLETED || old.kind == OrganizerKind.TASK)
            if (exception.disposition == OccurrenceDisposition.COMPLETED && hasOpenSubtasks(old, exception.originalStart))
                throw OrganizerConflict("Complete the remaining subtasks for this occurrence first")
            require(old.kind != OrganizerKind.EVENT || exception.replacementTime == null || exception.replacementTime.end != null)
            val previous = overrides(old.id).find { it.originalStart == exception.originalStart }
            val value = exception.copy(version = (previous?.version ?: 0) + 1)
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(old.id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            dao.putOverride(OrganizerOccurrenceEntity(value.recordId, value.originalStart.toEpochMilli(), OrganizerCodec.encode(value)))
            old.copy(version = old.version + 1, updatedAt = Instant.ofEpochMilli(clock.millis())).also {
                check(dao.update(OrganizerCodec.entity(it)) == 1); changed(it, "occurrence_${exception.disposition.name.lowercase(java.util.Locale.ROOT)}")
            }
        }
        runCatching(schedulesChanged); return next
    }
    private suspend fun hasOpenSubtasks(parent: OrganizerRecord, originalStart: Instant): Boolean {
        val date = originalStart.atZone(ZoneId.of(requireNotNull(parent.time).zoneId)).toLocalDate()
        for (child in records().filter { it.parentId == parent.id && it.status == OrganizerStatus.ACTIVE }) {
            if (child.repeat == null) return true
            val time = requireNotNull(child.time)
            val identityZone = ZoneId.of(time.zoneId)
            val exceptions = overrides(child.id).associateBy { it.originalStart }
            val logicalOccurrences = OrganizerRecurrence.slots(time, child.repeat, deviceZone())
                .takeWhile { it.identity.atZone(identityZone).toLocalDate() <= date }
                .filter { it.identity.atZone(identityZone).toLocalDate() == date }
            if (logicalOccurrences.any { exceptions[it.identity]?.disposition !in setOf(OccurrenceDisposition.COMPLETED, OccurrenceDisposition.CANCELLED) }) return true
        }
        return false
    }
    override suspend fun removeOverride(recordId: String, originalStart: Instant, expectedRecordVersion: Long): OrganizerRecord {
        val next = database.withTransaction {
            val old = get(recordId) ?: throw OrganizerConflict("Record no longer exists")
            if (old.version != expectedRecordVersion || old.deletedAt != null || old.status != OrganizerStatus.ACTIVE)
                throw OrganizerConflict("Record changed; reopen this occurrence")
            check(overrides(recordId).any { it.originalStart == originalStart }) { "Occurrence has no change to remove" }
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(recordId); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            check(dao.removeOverride(recordId, originalStart.toEpochMilli()) == 1) { "Occurrence has no change to remove" }
            old.copy(version = old.version + 1, updatedAt = Instant.ofEpochMilli(clock.millis())).also {
                check(dao.update(OrganizerCodec.entity(it)) == 1); changed(it, "occurrence_restored")
            }
        }
        runCatching(schedulesChanged); return next
    }
    override suspend fun receipts(recordId: String) = dao.receipts(recordId).map(OrganizerCodec::receipt)
    fun observeReceipts() = dao.observeReceipts().map { rows -> rows.map(OrganizerCodec::receipt) }
    override suspend fun putReceipt(receipt: DeliveryReceipt, expected: DeliveryReceipt?): Boolean = database.withTransaction {
        val record = get(receipt.recordId) ?: return@withTransaction false
        if (record.version != receipt.recordVersion || record.deletedAt != null && receipt.state != DeliveryState.CANCELLED) return@withTransaction false
        require(receipt.occurrenceKey.startsWith("${record.id}@") && receipt.detail.orEmpty().length <= 1000)
        val previous = dao.receipt(receipt.occurrenceKey)
        if (expected != null && previous?.let(OrganizerCodec::receipt) != expected) return@withTransaction false
        if (previous != null && previous.recordVersion == receipt.recordVersion) {
            if (expected == null && receipt.updatedAt.toEpochMilli() < previous.updatedAt) return@withTransaction false
            if (previous.state in setOf(DeliveryState.FIRED.name, DeliveryState.DISMISSED.name, DeliveryState.MISSED.name, DeliveryState.CANCELLED.name)) return@withTransaction false
        }
        if (previous?.state == DeliveryState.DELIVERING.name && previous.recordVersion == receipt.recordVersion &&
            receipt.state in setOf(DeliveryState.PENDING, DeliveryState.EXACT, DeliveryState.APPROXIMATE)) return@withTransaction false
        dao.putReceipt(OrganizerCodec.entity(receipt)); true
    }
    override suspend fun claimDelivery(key: String, version: Long, triggerAt: Instant, now: Instant): Boolean = database.withTransaction {
        val previous = dao.receipt(key)?.let(OrganizerCodec::receipt) ?: return@withTransaction false
        val record = get(previous.recordId) ?: return@withTransaction false
        if (record.version != version || record.deletedAt != null || record.status != OrganizerStatus.ACTIVE ||
            previous.recordVersion != version || previous.triggerAt != triggerAt ||
            previous.state !in setOf(DeliveryState.PENDING, DeliveryState.EXACT, DeliveryState.APPROXIMATE,
                DeliveryState.NOTIFICATIONS_BLOCKED, DeliveryState.FAILED)) return@withTransaction false
        dao.putReceipt(OrganizerCodec.entity(previous.copy(state = DeliveryState.DELIVERING, updatedAt = now)))
        true
    }

    override suspend fun snoozeDelivery(key: String, version: Long, duration: java.time.Duration,
        elapsedMs: Long, bootId: String): OrganizerRecord {
        require(duration.toMillis() in 60_000..86_400_000 && elapsedMs >= 0 && bootId.isNotBlank())
        val result = database.withTransaction {
            val receipt = dao.receipt(key)?.let(OrganizerCodec::receipt) ?: throw OrganizerConflict("Delivery no longer exists")
            val old = get(receipt.recordId) ?: throw OrganizerConflict("Record no longer exists")
            if (old.version != version || old.deletedAt != null || old.status != OrganizerStatus.ACTIVE ||
                receipt.recordVersion != version || receipt.state !in setOf(DeliveryState.FIRED, DeliveryState.DELIVERING))
                throw OrganizerConflict("This alert changed or is no longer ringing; reopen it")
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(old.id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            val now = Instant.ofEpochMilli(clock.millis())
            val next = old.copy(version = old.version + 1, updatedAt = now)
            check(dao.update(OrganizerCodec.entity(next)) == 1)
            dao.putReceipt(OrganizerCodec.entity(receipt.copy(recordVersion = next.version,
                triggerAt = now.plus(duration), state = DeliveryState.PENDING, updatedAt = now, detail = null,
                snoozedUntil = now.plus(duration), snoozeElapsedDeadlineMs = Math.addExact(elapsedMs, duration.toMillis()), snoozeBootId = bootId)))
            changed(next, "snoozed")
            next
        }
        runCatching(schedulesChanged)
        return result
    }

    override suspend fun dismissDelivery(key: String, version: Long): Boolean {
        val result = database.withTransaction {
            val receipt = dao.receipt(key)?.let(OrganizerCodec::receipt) ?: return@withTransaction false
            val record = get(receipt.recordId) ?: return@withTransaction false
            if (record.version != version || receipt.recordVersion != version || record.deletedAt != null ||
                receipt.state !in setOf(DeliveryState.FIRED, DeliveryState.DELIVERING)) return@withTransaction false
            com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(record.id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
            val now = Instant.ofEpochMilli(clock.millis())
            dao.putReceipt(OrganizerCodec.entity(receipt.copy(state = DeliveryState.DISMISSED, updatedAt = now)))
            if (record.kind == OrganizerKind.TIMER || record.repeat == null && record.kind in setOf(OrganizerKind.ALARM, OrganizerKind.REMINDER)) {
                val next = record.copy(version = record.version + 1, updatedAt = now, status = OrganizerStatus.COMPLETED,
                    timer = record.timer?.copy(remainingMs = 0, startedAt = null, elapsedStartMs = null, bootId = null))
                check(dao.update(OrganizerCodec.entity(next)) == 1)
                changed(next, "dismissed")
            } else dao.enqueue(OrganizerScheduleEntity(record.id, record.version))
            true
        }
        if (result) runCatching(schedulesChanged)
        return result
    }

    /** Import only outcomes recorded before unlock for the still-current, previously mirrored version. */
    override suspend fun importLockedDeliveries(recordId: String, version: Long, receipts: List<DeliveryReceipt>): Boolean {
        val result = database.withTransaction {
            val record = get(recordId) ?: return@withTransaction false
            if (record.version != version || record.deletedAt != null || record.status != OrganizerStatus.ACTIVE) return@withTransaction false
            for (receipt in receipts.filter { it.recordVersion == version }) {
                require(receipt.recordId == recordId && receipt.recordVersion == version && receipt.occurrenceKey.startsWith("$recordId@"))
                dao.putReceipt(OrganizerCodec.entity(receipt))
            }
            if (receipts.any { it.recordVersion == version && it.state == DeliveryState.DISMISSED } &&
                (record.kind == OrganizerKind.TIMER || record.repeat == null && record.kind in setOf(OrganizerKind.ALARM, OrganizerKind.REMINDER))) {
                com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(record.id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
                val next = record.copy(version = version + 1, updatedAt = Instant.ofEpochMilli(clock.millis()), status = OrganizerStatus.COMPLETED,
                    timer = record.timer?.copy(remainingMs = 0, startedAt = null, elapsedStartMs = null, bootId = null))
                check(dao.update(OrganizerCodec.entity(next)) == 1)
                changed(next, "dismissed_after_unlock")
            } else dao.enqueue(OrganizerScheduleEntity(recordId, version))
            true
        }
        if (result) runCatching(schedulesChanged)
        return result
    }

    override suspend fun pendingScheduleIds() = dao.pending()
    override suspend fun acknowledgeSchedule(id: String, version: Long) = dao.acknowledge(id, version) == 1
    override suspend fun changes(since: Instant) = dao.changes(since.toEpochMilli()).map { OrganizerChange(it.recordId, it.version, it.action, Instant.ofEpochMilli(it.at)) }

    override suspend fun importCalendar(preview: CalendarImportPreview): CalendarImportResult {
        require(preview.records.size <= 2000 && preview.exceptions.size <= 10000)
        val incoming = preview.records.associateBy { it.id }
        require(incoming.size == preview.records.size && preview.exceptions.distinctBy { it.recordId to it.originalStart }.size == preview.exceptions.size)
        incoming.values.forEach { record ->
            record.validate()
            require(record.kind == OrganizerKind.EVENT && record.calendarUid != null && record.deletedAt == null &&
                record.notifyBeforeMinutes == null && record.parentId == null && record.links.isEmpty())
        }
        preview.exceptions.forEach { exception ->
            val record = requireNotNull(incoming[exception.recordId])
            require(record.repeat != null && OrganizerRecurrence.contains(record, exception.originalStart, deviceZone()))
            require(exception.disposition != OccurrenceDisposition.COMPLETED)
            require(exception.replacementTime == null || exception.replacementTime.end != null)
        }
        val result = database.withTransaction {
            val existing = records(includeDeleted = true)
            val ids = existing.map { it.id }.toSet()
            val uids = existing.mapNotNull { it.calendarUid }.toMutableSet()
            val added = incoming.values.filter { it.id !in ids && uids.add(requireNotNull(it.calendarUid)) }
            require(existing.size + added.size <= 10000) { "Organizer storage limit reached" }
            val now = Instant.ofEpochMilli(clock.millis())
            added.forEach { record ->
                val saved = record.copy(version = 1, createdAt = now, updatedAt = now,
                    commitment = OrganizerCommitment(createdBy = CommitmentCreator.IMPORTED))
                com.halo.app.everyday.EverydayWriteAuthority.requireCurrent(); beforeRecordChange(saved.id); com.halo.app.everyday.EverydayWriteAuthority.requireCurrent()
                dao.insert(OrganizerCodec.entity(saved))
                preview.exceptions.filter { it.recordId == saved.id }.forEach { exception ->
                    val value = exception.copy(version = 1)
                    dao.putOverride(OrganizerOccurrenceEntity(value.recordId, value.originalStart.toEpochMilli(), OrganizerCodec.encode(value)))
                }
                changed(saved, "calendar_imported")
            }
            CalendarImportResult(added.map { it.id }, (incoming.keys - added.map { it.id }.toSet()).toList())
        }
        if (result.added.isNotEmpty()) runCatching(schedulesChanged)
        return result
    }
}
