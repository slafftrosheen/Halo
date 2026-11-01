package com.halo.app.data.everyday

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.organizer.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class OrganizerPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repository = RoomOrganizerRepository(db, clock, { ZoneOffset.UTC })
    private fun task(id: String = "task") = OrganizerRecord(id, OrganizerKind.TASK, "Plan the day", createdAt = now)
    @After fun close() { db.close() }

    @Test fun editAndDeleteUndoAreVersionedAndSurviveRepositoryRecreation() = runBlocking {
        val first = repository.save(task(), null)
        val second = repository.save(first.copy(title = "Updated"), first.version)
        assertEquals(2L, second.version)
        assertThrows(OrganizerConflict::class.java) { runBlocking { repository.save(first.copy(title = "Stale"), first.version) } }
        val deleted = repository.delete(first.id, second.version)
        assertTrue(repository.records().isEmpty())
        val recreated = RoomOrganizerRepository(db, clock)
        val restored = recreated.restore(first.id, deleted.version)
        assertEquals("Updated", restored.title); assertNull(restored.deletedAt)
        assertThrows(OrganizerConflict::class.java) { runBlocking { recreated.restore(first.id, deleted.version) } }
        assertEquals(listOf("created", "updated", "deleted", "restored"), recreated.changes(now).map { it.action })
    }
    @Test fun durableOutboxSurvivesEnqueueFailureAndStaleAcknowledgement() = runBlocking {
        val failing = RoomOrganizerRepository(db, clock, schedulesChanged = { error("WorkManager unavailable") })
        val first = failing.save(task(), null)
        assertEquals(listOf(first.id), repository.pendingScheduleIds())
        val next = repository.save(first.copy(title = "Changed"), first.version)
        assertFalse(repository.acknowledgeSchedule(first.id, first.version))
        assertEquals(listOf(first.id), repository.pendingScheduleIds())
        assertTrue(repository.acknowledgeSchedule(next.id, next.version))
        assertTrue(repository.pendingScheduleIds().isEmpty())
    }
    @Test fun subtasksPreventCyclesOrOrphansAndParentCompletionRequiresDoneChildren() = runBlocking {
        val parent = repository.save(task("parent"), null)
        val child = repository.save(task("child").copy(parentId = parent.id), null)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.save(parent.copy(parentId = child.id), parent.version) } }
        assertThrows(OrganizerConflict::class.java) { runBlocking { repository.delete(parent.id, parent.version) } }
        assertThrows(OrganizerConflict::class.java) { runBlocking { repository.save(parent.copy(status = OrganizerStatus.COMPLETED), parent.version) } }
        repository.save(child.copy(status = OrganizerStatus.COMPLETED), child.version)
        assertEquals(OrganizerStatus.COMPLETED, repository.save(parent.copy(status = OrganizerStatus.COMPLETED), parent.version).status)
    }
    @Test fun recurringTaskCompletionPreservesSeriesAndRequiresExplicitResetForSeriesEdit() = runBlocking {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-01T10:00"), zoneId = "UTC")
        val first = repository.save(task().copy(time = time, repeat = RepeatRule(RepeatFrequency.DAILY)), null)
        val useCases = OrganizerUseCases(repository, clock, { ZoneOffset.UTC }, { 0L }, { "boot" })
        val next = useCases.completeTask(first.id, first.version, time.instant(ZoneOffset.UTC))
        assertEquals(OrganizerStatus.ACTIVE, next.status)
        assertEquals(OccurrenceDisposition.COMPLETED, repository.overrides(first.id).single().disposition)
        val edited = next.copy(time = time.copy(start = time.start.plusHours(1)))
        assertThrows(OrganizerConflict::class.java) { runBlocking { repository.save(edited, next.version) } }
        repository.save(edited, next.version, resetOccurrences = true)
        assertTrue(repository.overrides(first.id).isEmpty())
    }
    @Test fun staleDeliveryCannotOverwriteEditedScheduleAndFiredCannotBeRescheduled() = runBlocking {
        val first = repository.save(task(), null)
        val receipt = DeliveryReceipt("task@1", first.id, first.version, now.plusSeconds(60), DeliveryState.FIRED, now)
        assertTrue(repository.putReceipt(receipt))
        assertFalse(repository.putReceipt(receipt.copy(state = DeliveryState.EXACT)))
        val next = repository.save(first.copy(title = "New version"), first.version)
        assertFalse(repository.putReceipt(receipt.copy(state = DeliveryState.FAILED)))
        assertTrue(repository.putReceipt(receipt.copy(recordVersion = next.version, state = DeliveryState.CANCELLED)))
    }
    @Test fun fullRecordCodecRetainsAttributionLinksTimerAndTimeZone() = runBlocking {
        val original = task().copy(details = "Latviešu · Русский · Polski", time = OrganizerTime(LocalDateTime.parse("2026-03-29T07:00"), zoneId = "Europe/Riga"),
            repeat = RepeatRule(RepeatFrequency.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), count = 8),
            links = listOf(OrganizerLink("note-id")), sourceUrl = "https://example.org/article", sourceLabel = "Publisher", notifyBeforeMinutes = 10)
        val saved = repository.save(original, null)
        assertEquals(saved, RoomOrganizerRepository(db).get(saved.id))
        val timer = OrganizerRecord("timer", OrganizerKind.TIMER, "Tea", timer = TimerState(60000, 60000).start(now, 123, "boot"), createdAt = now)
        assertEquals(timer, OrganizerCodec.decode(OrganizerCodec.encode(timer)))
    }
    @Test fun movedRecurringTaskCompletionAndReopeningKeepItsMoveAndTitle() = runBlocking {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-01T10:00"), zoneId = "UTC")
        val first = repository.save(task().copy(time = time, repeat = RepeatRule(RepeatFrequency.DAILY)), null)
        val original = time.instant(ZoneOffset.UTC)
        val moved = repository.saveOverride(OccurrenceOverride(first.id, original, OccurrenceDisposition.REPLACED,
            time.copy(start = time.start.plusDays(1).plusHours(2)), "Moved task"), first.version)
        val useCases = OrganizerUseCases(repository, clock, { ZoneOffset.UTC }, { 0L }, { "boot" })
        val completed = useCases.completeTask(first.id, moved.version, original)
        val exception = repository.overrides(first.id).single()
        assertEquals("Moved task", exception.replacementTitle); assertEquals(12, exception.replacementTime!!.start.hour)
        val rows = useCases.agenda(now, now.plusSeconds(3 * 86400))
        assertTrue(rows.single { it.originalStart == original }.completed)
        useCases.reopenTask(first.id, completed.version, original)
        assertEquals(OccurrenceDisposition.REPLACED, repository.overrides(first.id).single().disposition)
        assertEquals("Moved task", repository.overrides(first.id).single().replacementTitle)
    }
    @Test fun recurringParentCompletionChecksItsSubtasksAndRecurringReopenWorks() = runBlocking {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-01T10:00"), zoneId = "UTC")
        val first = repository.save(task().copy(time = time, repeat = RepeatRule(RepeatFrequency.DAILY)), null)
        val child = repository.save(task("child").copy(parentId = first.id), null)
        val useCases = OrganizerUseCases(repository, clock, { ZoneOffset.UTC }, { 0L }, { "boot" })
        assertThrows(OrganizerConflict::class.java) { runBlocking { useCases.completeTask(first.id, first.version, time.instant(ZoneOffset.UTC)) } }
        useCases.completeTask(child.id, child.version)
        val complete = useCases.completeTask(first.id, first.version, time.instant(ZoneOffset.UTC))
        useCases.reopenTask(first.id, complete.version, time.instant(ZoneOffset.UTC))
        assertTrue(repository.overrides(first.id).isEmpty())
    }
    @Test fun terminalReceiptRejectsLateFailureDuplicateDeliveryAndOlderProgress() = runBlocking {
        val first = repository.save(task(), null)
        val pending = DeliveryReceipt("task@1", first.id, first.version, now.plusSeconds(60), DeliveryState.EXACT, now.plusSeconds(1))
        assertTrue(repository.putReceipt(pending))
        assertFalse(repository.putReceipt(pending.copy(state = DeliveryState.PENDING, updatedAt = now)))
        val fired = pending.copy(state = DeliveryState.FIRED, updatedAt = now.plusSeconds(60))
        assertTrue(repository.putReceipt(fired)); assertFalse(repository.putReceipt(fired))
        assertFalse(repository.putReceipt(fired.copy(state = DeliveryState.FAILED, updatedAt = now.plusSeconds(70))))
        assertFalse(repository.putReceipt(fired.copy(state = DeliveryState.NOTIFICATIONS_BLOCKED, updatedAt = now.plusSeconds(70))))
        assertEquals(DeliveryState.FIRED, repository.receipts(first.id).single().state)
    }
    @Test fun submillisecondClockReturnsExactlyTheDurableSnapshotForImmediateEditing() = runBlocking {
        val precise = RoomOrganizerRepository(db, Clock.fixed(now.plusNanos(123456), ZoneOffset.UTC))
        val first = precise.save(task(), null)
        assertEquals(first, precise.get(first.id))
        assertEquals("Edit", precise.save(first.copy(title = "Edit"), first.version).title)
    }
    @Test fun permanentRemovalRequiresCurrentTrashAndAcknowledgedScheduleCancellation() = runBlocking<Unit> {
        val first = repository.save(task(), null)
        val deleted = repository.delete(first.id, first.version)
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.purgeDeleted(first.id, deleted.version) } }
        repository.acknowledgeSchedule(first.id, deleted.version)
        repository.purgeDeleted(first.id, deleted.version)
        assertNull(repository.get(first.id))
        assertThrows(OrganizerConflict::class.java) { runBlocking { repository.restore(first.id, deleted.version) } }
    }
    @Test fun movingAnOpenRecurringChildDoesNotDetachItFromLogicalParentOccurrence() = runBlocking {
        val time = OrganizerTime(LocalDateTime.parse("2026-01-01T10:00"), zoneId = "UTC")
        val parent = repository.save(task().copy(time = time, repeat = RepeatRule(RepeatFrequency.DAILY)), null)
        val child = repository.save(task("child").copy(parentId = parent.id, time = time, repeat = RepeatRule(RepeatFrequency.DAILY)), null)
        val original = time.instant(ZoneOffset.UTC)
        val moved = repository.saveOverride(OccurrenceOverride(child.id, original, OccurrenceDisposition.REPLACED,
            time.copy(start = time.start.plusDays(1))), child.version)
        val useCases = OrganizerUseCases(repository, clock, { ZoneOffset.UTC }, { 0L }, { "boot" })
        assertThrows(OrganizerConflict::class.java) { runBlocking { useCases.completeTask(parent.id, parent.version, original) } }
        useCases.completeTask(child.id, moved.version, original)
        assertEquals(OrganizerStatus.ACTIVE, useCases.completeTask(parent.id, parent.version, original).status)
    }
}
