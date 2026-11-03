package com.halo.app.everyday.organizer

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Android is an adapter: no OS alarm or notification is authoritative without a durable receipt. */
interface OrganizerDeliveryPort {
    suspend fun resetSchedules()
    suspend fun revokeVersion(recordId: String, version: Long) = Unit
    suspend fun cancelSchedule(recordId: String)
    suspend fun schedule(record: OrganizerRecord, delivery: PlannedDelivery): DeliveryOutcome
    /** Repeating this call must replace the same notification and must not restart an already ringing alarm. */
    suspend fun present(record: OrganizerRecord, receipt: DeliveryReceipt, recovered: Boolean): DeliveryOutcome
    suspend fun dismiss(occurrenceKey: String, recordVersion: Long)
}

data class DeliveryOutcome(val state: DeliveryState, val detail: String? = null)
class OrganizerDeliveryUncertain(cause: Exception): IllegalStateException("Delivery state changed; Android completion is unconfirmed",cause)

/** One application owner serializes callbacks, actions and reconciliation. Room also fences every write. */
class OrganizerDeliveryCoordinator(
    private val repository: OrganizerRepository,
    private val port: OrganizerDeliveryPort,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val elapsedMs: () -> Long,
    private val bootId: () -> String,
    private val changed: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val catchUp = Duration.ofMinutes(10)

    /** Keep OS callbacks out of a portable restore and revoke old locked-boot/notification authority. */
    suspend fun <T> withRestoredState(action: suspend ()->T): T = mutex.withLock {
        try {
        repository.records(true).forEach {record ->
            port.revokeVersion(record.id,record.version)
            repository.receipts(record.id).forEach {port.dismiss(it.occurrenceKey,it.recordVersion)}
        }
        port.resetSchedules()
        action()
        } finally {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                port.resetSchedules();repository.records(true).forEach {reconcileLocked(it.id)}
            }
        }
    }

    suspend fun reconcileAll() = mutex.withLock {
        port.resetSchedules()
        repository.records(includeDeleted = true).forEach { reconcileLocked(it.id) }
    }
    suspend fun reconcilePending() = mutex.withLock {
        repository.pendingScheduleIds().forEach { reconcileLocked(it) }
    }
    suspend fun reconcile(id: String) = mutex.withLock { reconcileLocked(id) }

    private suspend fun reconcileLocked(id: String) {
        val record = repository.get(id) ?: return
        port.cancelSchedule(id)
        val now = Instant.ofEpochMilli(clock.millis())
        val receipts = repository.receipts(id)
        receipts.filter { it.recordVersion != record.version }.forEach { port.dismiss(it.occurrenceKey, it.recordVersion) }
        if (record.deletedAt != null || record.status != OrganizerStatus.ACTIVE) {
            receipts.forEach { receipt ->
                port.dismiss(receipt.occurrenceKey, receipt.recordVersion)
                if (receipt.state !in consumed) persist(receipt.copy(
                    recordVersion = record.version, state = DeliveryState.CANCELLED, updatedAt = now))
            }
            repository.acknowledgeSchedule(id, record.version)
            changed()
            return
        }
        OrganizerInitialDelivery.overdue(record, repository.overrides(id), receipts, now, zone(), elapsedMs(), bootId())
            .forEach { persist(it) }
        // The persisted claim survives death between notification posting and recording its outcome.
        for (receipt in receipts.filter { it.recordVersion == record.version && it.state == DeliveryState.DELIVERING }) {
            deliverLocked(record, receipt, recovered = true)
        }
        for (receipt in repository.receipts(id).filter {
            it.recordVersion == record.version && it.state in awaiting && OrganizerDeliveryTiming.latenessMillis(record, it, now, elapsedMs(), bootId()) >= 0
        }) {
            if (OrganizerDeliveryTiming.latenessMillis(record, receipt, now, elapsedMs(), bootId()) > catchUp.toMillis()) {
                persist(receipt.copy(state = DeliveryState.MISSED, updatedAt = now,
                    detail = "Delivery time passed while Android or Halo was unavailable"))
            } else deliverLocked(record, receipt, recovered = false)
        }
        if (repository.get(id)?.version != record.version) return // Leave the newer outbox intent intact.
        val plan = OrganizerSchedulePlanner.next(record, repository.overrides(id), repository.receipts(id),
            now, zone(), elapsedMs(), bootId())
        if (plan != null) {
            val previous = repository.receipts(id).find { it.occurrenceKey == plan.occurrenceKey }
            val pending = DeliveryReceipt(plan.occurrenceKey, id, record.version, plan.triggerAt,
                DeliveryState.PENDING, now, snoozedUntil = previous?.snoozedUntil?.takeIf { previous.recordVersion == record.version },
                snoozeElapsedDeadlineMs = previous?.snoozeElapsedDeadlineMs?.takeIf { previous.recordVersion == record.version },
                snoozeBootId = previous?.snoozeBootId?.takeIf { previous.recordVersion == record.version })
            if (!persist(pending)) return
            val outcome = try { port.schedule(record, plan) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { DeliveryOutcome(DeliveryState.FAILED, "Android could not schedule this item; open it and retry") }
            require(outcome.state in setOf(DeliveryState.EXACT, DeliveryState.APPROXIMATE,
                DeliveryState.NOTIFICATIONS_BLOCKED, DeliveryState.FAILED))
            if (!persist(pending.copy(state = outcome.state, detail = outcome.detail,
                    updatedAt = Instant.ofEpochMilli(clock.millis())))) {
                port.cancelSchedule(id)
                port.revokeVersion(id, record.version)
                return
            }
            if (outcome.state == DeliveryState.FAILED) return // Keep durable outbox for worker retry.
        }
        if (repository.receipts(id).any { it.recordVersion == record.version && it.state == DeliveryState.FAILED &&
                OrganizerDeliveryTiming.latenessMillis(record, it, now, elapsedMs(), bootId()) in 0..catchUp.toMillis() }) return
        repository.acknowledgeSchedule(id, record.version)
        changed()
    }

    suspend fun fire(key: String, version: Long, triggerAt: Instant) = mutex.withLock {
        val id = key.substringBefore('@')
        val record = repository.get(id) ?: return@withLock false
        val receipt = repository.receipts(id).find { it.occurrenceKey == key && it.recordVersion == version && it.triggerAt == triggerAt }
            ?: return@withLock false
        val now = Instant.ofEpochMilli(clock.millis())
        if (record.version != version || record.deletedAt != null || record.status != OrganizerStatus.ACTIVE) return@withLock false
        val lateness = OrganizerDeliveryTiming.latenessMillis(record, receipt, now, elapsedMs(), bootId())
        if (lateness < 0) return@withLock false
        if (receipt.state !in awaiting && receipt.state != DeliveryState.DELIVERING) return@withLock false
        if (lateness > catchUp.toMillis()) {
            persist(receipt.copy(state = DeliveryState.MISSED, updatedAt = now,
                detail = "Android delivered this alarm too late; it was not sounded"))
        } else deliverLocked(record, receipt, receipt.state == DeliveryState.DELIVERING)
        reconcileLocked(id)
        // Consume co-due items in the same wake-up, rather than spending another Doze alarm allowance.
        for (other in repository.records().filter { it.id != id && it.status == OrganizerStatus.ACTIVE }) {
            if (repository.receipts(other.id).any { it.recordVersion == other.version && it.state in awaiting &&
                    OrganizerDeliveryTiming.latenessMillis(other, it, now, elapsedMs(), bootId()) >= 0 }) reconcileLocked(other.id)
        }
        true
    }

    private suspend fun deliverLocked(record: OrganizerRecord, receipt: DeliveryReceipt, recovered: Boolean) {
        val now = Instant.ofEpochMilli(clock.millis())
        if (OrganizerDeliveryTiming.latenessMillis(record, receipt, now, elapsedMs(), bootId()) > catchUp.toMillis()) {
            persist(receipt.copy(state = DeliveryState.MISSED, updatedAt = now))
            return
        }
        if (!recovered && !repository.claimDelivery(receipt.occurrenceKey, record.version, receipt.triggerAt, now)) return
        if (repository.get(record.id)?.let { it.version == record.version && it.status == OrganizerStatus.ACTIVE && it.deletedAt == null } != true) return
        val presentation = record.forDelivery(receipt.occurrenceKey, repository.overrides(record.id))
        val outcome = try { port.present(presentation, receipt.copy(state = DeliveryState.DELIVERING), recovered) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { DeliveryOutcome(DeliveryState.FAILED, "Android could not display this item; check notification access") }
        require(outcome.state in setOf(DeliveryState.FIRED, DeliveryState.NOTIFICATIONS_BLOCKED, DeliveryState.FAILED))
        if (!persist(receipt.copy(state = outcome.state, detail = outcome.detail,
                updatedAt = Instant.ofEpochMilli(clock.millis())))) port.dismiss(receipt.occurrenceKey, receipt.recordVersion)
    }

    suspend fun snooze(key: String, version: Long, duration: Duration, expectedTriggerAt: Instant? = null): OrganizerRecord = mutex.withLock {
        checkActionTrigger(key, version, expectedTriggerAt)
        val saved = repository.snoozeDelivery(key, version, duration, elapsedMs(), bootId())
        try {
            port.dismiss(key, version)
            reconcileLocked(saved.id)
        } catch(cancelled: CancellationException) {throw cancelled}
          catch(error: Exception) {throw OrganizerDeliveryUncertain(error)}
        saved
    }

    /** Stop this delivered occurrence; a repeating alarm's future occurrences remain enabled. */
    suspend fun dismiss(key: String, version: Long, expectedTriggerAt: Instant? = null): Boolean = mutex.withLock {
        checkActionTrigger(key, version, expectedTriggerAt)
        val accepted = repository.dismissDelivery(key, version)
        if(accepted) try {
            port.dismiss(key, version)
            reconcileLocked(key.substringBefore('@'))
        } catch(cancelled: CancellationException) {throw cancelled}
          catch(error: Exception) {throw OrganizerDeliveryUncertain(error)}
        accepted
    }

    private suspend fun checkActionTrigger(key: String, version: Long, expected: Instant?) {
        if (expected != null && repository.receipts(key.substringBefore('@')).none {
                it.occurrenceKey == key && it.recordVersion == version && it.triggerAt == expected })
            throw OrganizerConflict("This alert changed; reopen it")
    }

    /** Serialized owner writes still compare the stored row, so wall-clock rollback cannot admit stale progress. */
    private suspend fun persist(receipt: DeliveryReceipt): Boolean {
        val previous = repository.receipts(receipt.recordId).find { it.occurrenceKey == receipt.occurrenceKey }
        return repository.putReceipt(receipt, expected = previous)
    }

    private companion object {
        val consumed = setOf(DeliveryState.FIRED, DeliveryState.DISMISSED, DeliveryState.MISSED, DeliveryState.CANCELLED)
        val awaiting = setOf(DeliveryState.PENDING, DeliveryState.EXACT, DeliveryState.APPROXIMATE,
            DeliveryState.NOTIFICATIONS_BLOCKED, DeliveryState.FAILED)
    }
}
