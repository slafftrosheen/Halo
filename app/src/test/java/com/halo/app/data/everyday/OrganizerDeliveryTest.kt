package com.halo.app.data.everyday

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.organizer.android.*
import com.halo.host.android.reminder.*
import java.time.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrganizerDeliveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val clock = MovingClock()
    private var elapsed = 1000L
    private val repository = RoomOrganizerRepository(db, clock, { ZoneOffset.UTC })
    private val port = RecordingPort()
    private val owner = OrganizerDeliveryCoordinator(repository, port, clock, { ZoneOffset.UTC }, { elapsed }, { "boot" })
    private fun record(id: String = "wake", repeat: RepeatRule? = null) = OrganizerRecord(id, OrganizerKind.ALARM, "Wake me",
        time = OrganizerTime(clock.instant().plusSeconds(60).atZone(ZoneOffset.UTC).toLocalDateTime(), zoneId = "UTC"),
        repeat = repeat, createdAt = clock.instant())
    @After fun close() { db.close() }
    private fun advance(ms: Long) { clock.now = clock.now.plusMillis(ms); elapsed += ms }

    @Test fun durableIntentPrecedesOsAndFallbackIsNotReportedAsExact() = runBlocking {
        val saved = repository.save(record(), null)
        port.onSchedule = { plan -> assertEquals(DeliveryState.PENDING, repository.receipts(plan.recordId).single().state) }
        port.scheduling = DeliveryOutcome(DeliveryState.APPROXIMATE, "Exact access is off")
        owner.reconcilePending()
        assertEquals(DeliveryState.APPROXIMATE, repository.receipts(saved.id).single().state)
        assertTrue(repository.pendingScheduleIds().isEmpty())
        assertEquals(saved.version, port.scheduled.single().recordVersion)
    }

    @Test fun explicitReceiptComparisonAllowsClockRollbackButRejectsStaleExpectedRow() = runBlocking {
        val saved = repository.save(record(), null)
        owner.reconcilePending()
        val initial = repository.receipts(saved.id).single()
        val afterClockChange = initial.copy(state = DeliveryState.PENDING, updatedAt = initial.updatedAt.minusSeconds(3600))
        assertFalse(repository.putReceipt(afterClockChange))
        assertTrue(repository.putReceipt(afterClockChange, expected = initial))
        assertFalse(repository.putReceipt(initial.copy(state = DeliveryState.FAILED), expected = initial))
        assertEquals(afterClockChange, repository.receipts(saved.id).single())
    }

    @Test fun duplicateAndEditedCallbacksCannotSoundAgain() = runBlocking {
        val saved = repository.save(record(), null)
        owner.reconcilePending()
        val first = repository.receipts(saved.id).single()
        val changed = repository.save(saved.copy(title = "Different title"), saved.version)
        owner.reconcilePending()
        advance(60_000)
        assertFalse(owner.fire(first.occurrenceKey, first.recordVersion, first.triggerAt))
        val current = repository.receipts(saved.id).single()
        assertTrue(owner.fire(current.occurrenceKey, changed.version, current.triggerAt))
        assertFalse(owner.fire(current.occurrenceKey, changed.version, current.triggerAt))
        assertEquals(1, port.presented.size)
    }

    @Test fun persistedClaimIsRecoveredAndVeryLateClaimDoesNotRing() = runBlocking {
        val saved = repository.save(record(), null)
        owner.reconcilePending(); advance(60_000)
        val receipt = repository.receipts(saved.id).single()
        assertTrue(repository.claimDelivery(receipt.occurrenceKey, saved.version, receipt.triggerAt, clock.instant()))
        owner.reconcileAll()
        assertEquals(listOf(true), port.recovered)
        val another = repository.save(record("old"), null)
        owner.reconcilePending(); advance(60_000)
        val old = repository.receipts(another.id).single()
        assertTrue(repository.claimDelivery(old.occurrenceKey, another.version, old.triggerAt, clock.instant()))
        advance(11 * 60_000)
        owner.reconcileAll()
        assertEquals(DeliveryState.MISSED, repository.receipts(another.id).single().state)
        assertEquals(1, port.presented.size)
    }

    @Test fun timerAndSnoozeKeepElapsedDeadlineAcrossWallClockRollback() = runBlocking {
        val timer = repository.save(OrganizerRecord("tea", OrganizerKind.TIMER, "Tea",
            timer = TimerState(60_000, 60_000).start(clock.instant(), elapsed, "boot"), createdAt = clock.instant()), null)
        owner.reconcilePending()
        val first = repository.receipts(timer.id).single()
        elapsed += 60_000; clock.now = clock.now.minusSeconds(3600)
        assertTrue(owner.fire(first.occurrenceKey, timer.version, first.triggerAt))
        val snoozed = owner.snooze(first.occurrenceKey, timer.version, Duration.ofMinutes(5), first.triggerAt)
        val second = repository.receipts(timer.id).single()
        assertEquals(snoozed.version, second.recordVersion)
        assertEquals(elapsed + 300_000, second.snoozeElapsedDeadlineMs)
        assertFalse(owner.fire(first.occurrenceKey, timer.version, first.triggerAt))
        elapsed += 300_000; clock.now = clock.now.minusSeconds(3600)
        assertTrue(owner.fire(second.occurrenceKey, snoozed.version, second.triggerAt))
        assertEquals(2, port.presented.size)
        assertTrue(owner.dismiss(second.occurrenceKey, snoozed.version, second.triggerAt))
        assertEquals(OrganizerStatus.COMPLETED, repository.get(timer.id)?.status)
        assertNull(OrganizerSchedulePlanner.next(requireNotNull(repository.get(timer.id)), emptyList(), repository.receipts(timer.id),
            clock.instant(), ZoneOffset.UTC, elapsed, "boot"))
    }

    @Test fun stopKeepsRecurringAlarmEnabledAndOneWakeConsumesOtherDueItems() = runBlocking {
        val one = repository.save(record("one", RepeatRule(RepeatFrequency.DAILY)), null)
        val two = repository.save(record("two"), null)
        owner.reconcilePending(); advance(60_000)
        val first = repository.receipts(one.id).single()
        owner.fire(first.occurrenceKey, one.version, first.triggerAt)
        assertEquals(setOf(one.id, two.id), port.presented.map { it.recordId }.toSet())
        assertTrue(owner.dismiss(first.occurrenceKey, one.version, first.triggerAt))
        assertEquals(OrganizerStatus.ACTIVE, repository.get(one.id)?.status)
        assertTrue(port.scheduled.any { it.recordId == one.id && it.triggerAt > clock.instant() })
    }

    @Test fun notificationDenialDoesNotCreateAnImmediateTimerWakeLoop() = runBlocking {
        val timer = repository.save(OrganizerRecord("timer", OrganizerKind.TIMER, "Tea",
            timer = TimerState(1000, 1000).start(clock.instant(), elapsed, "boot"), createdAt = clock.instant()), null)
        owner.reconcilePending(); advance(1000)
        port.presentation = DeliveryOutcome(DeliveryState.NOTIFICATIONS_BLOCKED)
        val receipt = repository.receipts(timer.id).single()
        owner.fire(receipt.occurrenceKey, timer.version, receipt.triggerAt)
        assertEquals(1, port.scheduled.size)
        assertEquals(DeliveryState.NOTIFICATIONS_BLOCKED, repository.receipts(timer.id).single().state)
    }

    @Test fun bootMirrorRedactsContentAndUnlockRejectsSupersededOutcomes() = runBlocking {
        val saved = repository.save(record().copy(details = "private-note", location = "private-place",
            links = listOf(OrganizerLink("private-note-id")), sourceUrl = "https://example.org/private"), null)
        owner.reconcilePending()
        val receipt = repository.receipts(saved.id).single()
        val mirror = OrganizerDirectBootStore(context)
        mirror.save(LockedOrganizerEntry(saved, emptyList(), listOf(receipt)))
        val redacted = requireNotNull(mirror.get(saved.id)).record
        assertEquals("Halo", redacted.title); assertEquals("", redacted.details); assertEquals("", redacted.location)
        assertTrue(redacted.links.isEmpty()); assertNull(redacted.sourceUrl)
        mirror.receipt(receipt.copy(state = DeliveryState.DISMISSED), journal = true)
        val changed = repository.save(saved.copy(title = "New alarm"), saved.version)
        OrganizerLockedDelivery.importAfterUnlock(context, repository)
        assertEquals(changed, repository.get(saved.id))
        assertEquals(DeliveryState.EXACT, repository.receipts(saved.id).single().state)
        assertTrue(mirror.journals().isEmpty())
    }

    @Test fun legacyMigrationJournalSurvivesClaimAndPreventsDuplicateDelivery() = runBlocking {
        val legacy = ReminderRecordStore(context)
        legacy.save(ReminderRecord("legacy", "Do the thing", clock.instant().plusSeconds(60).toEpochMilli(), "EXACT"))
        assertNotNull(legacy.claimMigration("legacy"))
        assertNull(ReminderRecordStore(context).consumeForDelivery("legacy"))
        val migration = LegacyOrganizerReminders(context, repository)
        migration.migrate(); migration.migrate()
        assertEquals(1, repository.records().size)
        assertEquals(ReminderStatus.Migrated, legacy.status("legacy"))
        assertTrue(legacy.migrationIds().isEmpty())
    }

    @Test fun firstDelayedReconciliationDeliversRecentAlarmAndRecordsAncientTimerMiss() = runBlocking {
        val saved = repository.save(record(), null)
        advance(70_000)
        owner.reconcilePending()
        assertEquals(DeliveryState.FIRED, repository.receipts(saved.id).single().state)
        assertEquals(1, port.presented.size)
        val timer = repository.save(OrganizerRecord("expired", OrganizerKind.TIMER, "Expired",
            timer = TimerState(1000, 1000).start(clock.instant(), elapsed, "boot"), createdAt = clock.instant()), null)
        advance(60 * 60_000)
        owner.reconcilePending()
        assertEquals(DeliveryState.MISSED, repository.receipts(timer.id).single().state)
        assertEquals(1, port.presented.size)
    }

    @Test fun consumedLockedAlarmDoesNotStrandTheNextAlarm() = runBlocking {
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val first = record("first").copy(time = OrganizerTime(now.minusSeconds(1).atZone(ZoneOffset.UTC).toLocalDateTime(), zoneId = "UTC"))
        val second = record("second").copy(time = OrganizerTime(now.plusSeconds(60).atZone(ZoneOffset.UTC).toLocalDateTime(), zoneId = "UTC"))
        val mirror = OrganizerDirectBootStore(context)
        val queue = OrganizerAlarmQueue(context)
        queue.reset()
        val receipts = listOf(first, second).map { value ->
            val at = requireNotNull(value.time).instant(ZoneOffset.UTC)
            val receipt = DeliveryReceipt("${value.id}@${at.toEpochMilli()}", value.id, 1, at, DeliveryState.EXACT, now)
            mirror.save(LockedOrganizerEntry(value, emptyList(), listOf(receipt)))
            queue.replace(value.id, PlannedDelivery(receipt.occurrenceKey, value.id, 1, at, true, true))
            receipt
        }
        val locked = OrganizerLockedDelivery(context, unlocked = { false })
        locked.fire(receipts[0].occurrenceKey, 1, receipts[0].triggerAt)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals(receipts[1].occurrenceKey, shadowOf(alarms.scheduledAlarms.single().operation).savedIntent.getStringExtra(OrganizerIntents.KEY))
        assertTrue(locked.action(receipts[0].occurrenceKey, 1, receipts[0].triggerAt, snooze = false))
        assertEquals(1, alarms.scheduledAlarms.size)
    }

    @Test fun lockedRebootDiscoversTodaysUnrecordedRecurrenceAndFailedLoadIsUnknown() = runBlocking {
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val start = now.minusSeconds(86460)
        val value = record("daily").copy(time = OrganizerTime(start.atZone(ZoneOffset.UTC).toLocalDateTime(), zoneId = "UTC"),
            repeat = RepeatRule(RepeatFrequency.DAILY))
        val previous = DeliveryReceipt("daily@${start.toEpochMilli()}", value.id, value.version, start, DeliveryState.FIRED, start)
        val mirror = OrganizerDirectBootStore(context)
        mirror.save(LockedOrganizerEntry(value, emptyList(), listOf(previous)))
        OrganizerLockedDelivery(context, unlocked = { false }).reconcile()
        val today = start.plusSeconds(86400)
        assertEquals(DeliveryState.FIRED, mirror.get(value.id)?.receipts?.find { it.occurrenceKey == "daily@${today.toEpochMilli()}" }?.state)
        assertEquals(OrganizerAlertViewState.UNAVAILABLE, organizerAlertViewState(loaded = true, loadFailed = true, receipt = null))
        assertEquals(OrganizerAlertViewState.LOADING, organizerAlertViewState(loaded = false, loadFailed = false, receipt = null))
        assertEquals(OrganizerAlertViewState.FINISHED, organizerAlertViewState(loaded = true, loadFailed = false, receipt = null))
    }

    @Test fun callbackWaitingBehindUnlockImportUsesNormalOwnerAndCreatesNoLateJournal() = runBlocking {
        val saved = repository.save(record(), null)
        owner.reconcilePending(); advance(60_000)
        val receipt = repository.receipts(saved.id).single()
        val mirror = OrganizerDirectBootStore(context)
        mirror.save(LockedOrganizerEntry(saved, emptyList(), listOf(receipt)))
        var unlocked = false
        val locked = OrganizerLockedDelivery(context, unlocked = { unlocked }, normalOwner = { owner })
        OrganizerLockedDelivery.handoff.lock()
        val importing = launch(start = CoroutineStart.UNDISPATCHED) { OrganizerLockedDelivery.importAfterUnlock(context, repository) }
        val callback = launch(start = CoroutineStart.UNDISPATCHED) { locked.fire(receipt.occurrenceKey, saved.version, receipt.triggerAt) }
        unlocked = true
        OrganizerLockedDelivery.handoff.unlock()
        importing.join(); callback.join()
        assertEquals(DeliveryState.FIRED, repository.receipts(saved.id).single().state)
        assertTrue(mirror.journals().isEmpty())
        assertEquals(1, port.presented.size)
    }

    @Test fun editDuringMirrorPublicationRevokesOldGenerationWithoutRemovingSuccessor() = runBlocking {
        val mirror = OrganizerDirectBootStore(context)
        val wired = RoomOrganizerRepository(db, clock, beforeRecordChange = mirror::invalidate)
        val saved = wired.save(record(), null)
        var changed: OrganizerRecord? = null
        val racing = object : OrganizerRepository by wired {
            override suspend fun overrides(recordId: String): List<OccurrenceOverride> {
                changed = wired.save(saved.copy(title = "New version"), saved.version)
                return emptyList()
            }
        }
        val mirrored = MirroredOrganizerDelivery(port, racing, mirror)
        val plan = requireNotNull(OrganizerSchedulePlanner.next(saved, emptyList(), emptyList(), clock.instant(), ZoneOffset.UTC, elapsed, "boot"))
        assertEquals(DeliveryState.FAILED, mirrored.schedule(saved, plan).state)
        assertNull(mirror.get(saved.id)); assertTrue(port.scheduled.isEmpty())
        mirror.save(LockedOrganizerEntry(requireNotNull(changed), emptyList(), emptyList()))
        mirrored.revokeVersion(saved.id, saved.version)
        assertEquals(changed?.version, mirror.get(saved.id)?.record?.version)
    }

    @Test fun approximateWakeCannotHideAlarmClockAndActualFallbackIsReturned() {
        val applied = mutableMapOf<String, PlannedDelivery>()
        var permitExact = true
        val platform = object : OrganizerAlarmPlatform {
            override fun cancel(lane: String) { applied.remove(lane) }
            override fun schedule(lane: String, plan: PlannedDelivery): Boolean { applied[lane] = plan; return plan.exactRequested && permitExact }
        }
        val queue = OrganizerAlarmQueue(context, platform)
        queue.reset()
        queue.replace("event", PlannedDelivery("event@1", "event", 1, clock.instant().plusSeconds(60), false, false))
        assertTrue(queue.replace("alarm", PlannedDelivery("alarm@1", "alarm", 1, clock.instant().plusSeconds(120), true, true)))
        assertEquals(setOf("event", "alarm"), applied.values.map { it.recordId }.toSet())
        permitExact = false
        assertFalse(queue.replace("alarm", PlannedDelivery("alarm@1", "alarm", 1, clock.instant().plusSeconds(120), true, true)))
    }

    @Test fun malformedLegacyRecordIsRetainedWithoutBlockingValidMigration() = runBlocking {
        context.getSharedPreferences("halo_reminders", Context.MODE_PRIVATE).edit().putString("broken", "not-json").commit()
        val legacy = ReminderRecordStore(context)
        legacy.save(ReminderRecord("valid", "Valid reminder", clock.instant().plusSeconds(60).toEpochMilli(), "EXACT"))
        LegacyOrganizerReminders(context, repository).migrate()
        assertEquals(1, repository.records().size)
        assertTrue("broken" in legacy.migrationFailures())
        assertEquals("not-json", context.getSharedPreferences("halo_reminders", Context.MODE_PRIVATE).getString("broken", null))
    }

    @Test fun renamedMovedOccurrenceUsesReviewedTitleAndPreservesSeriesTitle() = runBlocking {
        val saved = repository.save(record(repeat = RepeatRule(RepeatFrequency.DAILY)), null)
        val time = requireNotNull(saved.time)
        val original = time.instant(ZoneOffset.UTC)
        val changed = repository.saveOverride(OccurrenceOverride(saved.id, original, OccurrenceDisposition.REPLACED,
            time.copy(start = time.start.plusMinutes(2)), "Special morning"), saved.version)
        owner.reconcilePending(); advance(180_000)
        val first = repository.receipts(saved.id).single()
        owner.fire(first.occurrenceKey, changed.version, first.triggerAt)
        assertEquals("Special morning", port.titles.single())
        val next = repository.receipts(saved.id).first { it.triggerAt > clock.instant() }
        assertEquals("Wake me", requireNotNull(repository.get(saved.id)).forDelivery(next.occurrenceKey, repository.overrides(saved.id)).title)
    }

    @Test fun manyRecordsUseOneNativeAlarmAndCancellationSelectsNext() {
        val queue = OrganizerAlarmQueue(context)
        queue.reset()
        for (index in 1..600) queue.replace("id$index", PlannedDelivery("id$index@1", "id$index", 1,
            clock.instant().plusSeconds(index.toLong()), true, false))
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals("id1@1", shadowOf(alarms.scheduledAlarms.single().operation).savedIntent.getStringExtra(OrganizerIntents.KEY))
        queue.replace("id1", null)
        assertEquals(1, alarms.scheduledAlarms.size)
        assertEquals("id2@1", shadowOf(alarms.scheduledAlarms.single().operation).savedIntent.getStringExtra(OrganizerIntents.KEY))
    }

    private class MovingClock(var now: Instant = Instant.parse("2026-11-01T08:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant() = now
    }
    private class RecordingPort : OrganizerDeliveryPort {
        val scheduled = mutableListOf<PlannedDelivery>()
        val presented = mutableListOf<DeliveryReceipt>()
        val recovered = mutableListOf<Boolean>()
        val titles = mutableListOf<String>()
        var scheduling = DeliveryOutcome(DeliveryState.EXACT)
        var presentation = DeliveryOutcome(DeliveryState.FIRED)
        var onSchedule: suspend (PlannedDelivery) -> Unit = {}
        override suspend fun resetSchedules() = Unit
        override suspend fun cancelSchedule(recordId: String) = Unit
        override suspend fun schedule(record: OrganizerRecord, delivery: PlannedDelivery): DeliveryOutcome {
            onSchedule(delivery); scheduled += delivery; return scheduling
        }
        override suspend fun present(record: OrganizerRecord, receipt: DeliveryReceipt, recovered: Boolean): DeliveryOutcome {
            presented += receipt; this.recovered += recovered; titles += record.title; return presentation
        }
        override suspend fun dismiss(occurrenceKey: String, recordVersion: Long) = Unit
    }
}
