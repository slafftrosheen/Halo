package com.halo.app.everyday.organizer.android

import android.content.Context
import com.halo.app.everyday.organizer.*
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Runs without Room, providers, WorkManager or credentials until first unlock. */
class OrganizerLockedDelivery(private val context: Context,
    private val unlocked: () -> Boolean = { context.getSystemService(android.os.UserManager::class.java).isUserUnlocked },
    private val normalOwner: suspend () -> OrganizerDeliveryCoordinator = {
        (context.applicationContext as com.halo.app.HaloApplication).awaitOrganizerBootstrap()
        com.halo.app.di.ManualDependencyGraph.organizerDelivery
    }) {
    private val store = OrganizerDirectBootStore(context)
    private val port = AndroidOrganizerDelivery(context)
    private fun elapsed() = OrganizerAndroidClock.elapsedMs()
    private fun boot() = OrganizerAndroidClock.bootId(context)

    suspend fun reconcile() {
      val handled = handoff.withLock {
        if (unlocked()) return@withLock false
        port.resetSchedules()
        store.entries().forEach { entry ->
            port.cancelSchedule(entry.record.id)
            OrganizerInitialDelivery.overdue(entry.record, entry.exceptions, entry.receipts, Instant.now(), ZoneId.systemDefault(), elapsed(), boot())
                .forEach { store.receipt(it, journal = true) }
            val current = store.get(entry.record.id) ?: return@forEach
            for (receipt in current.receipts.filter { it.state in awaiting || it.state == DeliveryState.DELIVERING }) {
                val late = OrganizerDeliveryTiming.latenessMillis(entry.record, receipt, Instant.now(), elapsed(), boot())
                if (late >= 0) deliver(entry, receipt, late)
            }
            schedule(entry.record.id)
        }
        true
      }
      if (!handled) OrganizerScheduleWorker.enqueue(context, full = true)
    }

    suspend fun fire(key: String, version: Long, triggerAt: Instant) {
        val reroute = handoff.withLock {
            if (unlocked()) return@withLock true
            val entry = store.get(key.substringBefore('@')) ?: return@withLock false
            val receipt = entry.receipts.find { it.occurrenceKey == key && it.recordVersion == version && it.triggerAt == triggerAt } ?: return@withLock false
            if (entry.record.version != version || receipt.state !in awaiting && receipt.state != DeliveryState.DELIVERING) return@withLock false
            val late = OrganizerDeliveryTiming.latenessMillis(entry.record, receipt, Instant.now(), elapsed(), boot())
            if (late < 0) return@withLock false
            deliver(entry, receipt, late)
            schedule(entry.record.id)
            // Co-due notifications share this wake-up before Android applies another idle allowance.
            for (other in store.entries().filter { it.record.id != entry.record.id }) {
                for (due in other.receipts.filter { it.state in awaiting }) {
                    val lateness = OrganizerDeliveryTiming.latenessMillis(other.record, due, Instant.now(), elapsed(), boot())
                    if (lateness >= 0) deliver(other, due, lateness)
                }
                schedule(other.record.id)
            }
            false
        }
        if (reroute) normalOwner().fire(key, version, triggerAt)
    }

    private suspend fun deliver(entry: LockedOrganizerEntry, receipt: DeliveryReceipt, late: Long) {
        val now = Instant.now()
        if (late > 10 * 60_000) {
            store.receipt(receipt.copy(state = DeliveryState.MISSED, updatedAt = now), journal = true)
            return
        }
        store.receipt(receipt.copy(state = DeliveryState.DELIVERING, updatedAt = now), journal = true)
        val result = port.present(entry.record, receipt, receipt.state == DeliveryState.DELIVERING)
        store.receipt(receipt.copy(state = result.state, updatedAt = Instant.now()), journal = true)
    }

    suspend fun action(key: String, version: Long, triggerAt: Instant, snooze: Boolean): Boolean {
        val result: Boolean? = handoff.withLock {
            if (unlocked()) return@withLock null
            val entry = store.get(key.substringBefore('@')) ?: return@withLock false
            val receipt = entry.receipts.find { it.occurrenceKey == key && it.recordVersion == version && it.triggerAt == triggerAt } ?: return@withLock false
            if (entry.record.version != version || receipt.state !in setOf(DeliveryState.FIRED, DeliveryState.DELIVERING)) return@withLock false
            val now = Instant.now()
            val next = if (snooze) receipt.copy(state = DeliveryState.PENDING, triggerAt = now.plusSeconds(300), updatedAt = now,
                snoozedUntil = now.plusSeconds(300), snoozeElapsedDeadlineMs = elapsed() + 300_000, snoozeBootId = boot())
            else receipt.copy(state = DeliveryState.DISMISSED, updatedAt = now)
            store.receipt(next, journal = true)
            port.dismiss(key, version)
            schedule(entry.record.id)
            true
        }
        if (result != null) return result
        val owner = normalOwner()
        return if (snooze) { owner.snooze(key, version, java.time.Duration.ofMinutes(5), triggerAt); true }
            else owner.dismiss(key, version, triggerAt)
    }

    private suspend fun schedule(id: String) {
        port.cancelSchedule(id)
        val entry = store.get(id) ?: return
        val plan = OrganizerSchedulePlanner.next(entry.record, entry.exceptions, entry.receipts, Instant.now(), ZoneId.systemDefault(), elapsed(), boot()) ?: return
        val previous = entry.receipts.find { it.occurrenceKey == plan.occurrenceKey }
        val pending = DeliveryReceipt(plan.occurrenceKey, id, entry.record.version, plan.triggerAt, DeliveryState.PENDING, Instant.now(),
            snoozedUntil = previous?.snoozedUntil, snoozeElapsedDeadlineMs = previous?.snoozeElapsedDeadlineMs, snoozeBootId = previous?.snoozeBootId)
        store.receipt(pending, journal = true)
        val result = port.schedule(entry.record, plan)
        store.receipt(pending.copy(state = result.state, updatedAt = Instant.now()), journal = true)
    }

    companion object {
        /** Unlock imports hold the same lock so a last locked callback cannot be lost during handoff. */
        val handoff = Mutex()
        private val awaiting = setOf(DeliveryState.PENDING, DeliveryState.EXACT, DeliveryState.APPROXIMATE,
            DeliveryState.NOTIFICATIONS_BLOCKED, DeliveryState.FAILED)
        suspend fun importAfterUnlock(context: Context, repository: OrganizerRepository) = handoff.withLock {
            val store = OrganizerDirectBootStore(context)
            store.journals().forEach { (id, encoded) ->
                val entry = store.decode(encoded)
                repository.importLockedDeliveries(id, entry.record.version, entry.receipts)
                store.acknowledgeJournal(id, encoded)
            }
        }
    }
}
