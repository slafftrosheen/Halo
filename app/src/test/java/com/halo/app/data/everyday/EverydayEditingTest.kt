package com.halo.app.data.everyday

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import com.halo.app.ui.everyday.OrganizerDraft
import com.halo.app.ui.screens.kids.KidsPreferences
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EverydayEditingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val now = Instant.parse("2026-03-01T10:00:00Z")
    private val repository = RoomOrganizerRepository(db, Clock.fixed(now, ZoneOffset.UTC))
    private fun event(id: String = "event", uid: String? = "uid:$id") = OrganizerRecord(id, OrganizerKind.EVENT, "Event",
        time = OrganizerTime(LocalDateTime.parse("2026-03-28T03:30"), LocalDateTime.parse("2026-03-28T05:30"), "Europe/Riga", followDeviceZone = true),
        repeat = RepeatRule(RepeatFrequency.DAILY, count = 5), createdAt = now, calendarUid = uid)
    @After fun close() { db.close(); context.getSharedPreferences("halo_kids_ui", 0).edit().clear().commit() }

    @Test fun titleOnlySeriesEditRetainsExactImportedUntilAndExistingOverrides() = runBlocking {
        val until = Instant.parse("2026-04-02T03:29:59Z")
        val saved = repository.save(event().copy(repeat = RepeatRule(RepeatFrequency.DAILY, until = until)), null)
        val identity = saved.time!!.start.atZone(ZoneId.of(saved.time.zoneId)).toInstant()
        val updated = repository.saveOverride(OccurrenceOverride(saved.id, identity, OccurrenceDisposition.CANCELLED), saved.version)
        val draft = OrganizerDraft(updated, false).copy(title = "Only the title", resetOccurrences = true)
        val restored = OrganizerDraft.restore(draft.bundle())
        val result = repository.save(restored.record(now, 0, "boot"), updated.version, restored.resetOccurrences)
        assertEquals(until, result.repeat!!.until)
        assertEquals(1, repository.overrides(saved.id).size)
    }
    @Test fun floatingDstReplacementSurvivesTitleEditAndDraftRestoration() {
        val base = event().copy(kind = OrganizerKind.TASK)
        val replacement = OrganizerTime(LocalDateTime.parse("2026-03-29T03:30"), LocalDateTime.parse("2026-03-29T05:30"), "Europe/Riga", followDeviceZone = true)
        val identity = Instant.parse("2026-03-29T01:30:00Z")
        val override = OccurrenceOverride(base.id, identity, OccurrenceDisposition.COMPLETED, replacement, "Moved task",
            sourceUrlOverride = "https://example.org/corrected")
        val zone = ZoneId.of("America/New_York")
        val actual = OrganizerRecurrence.between(base, listOf(override), Instant.parse("2026-03-29T00:00:00Z"), Instant.parse("2026-03-30T00:00:00Z"), zone).single()
        val draft = OrganizerDraft.restore(OrganizerDraft.forOccurrence(base, actual, override).copy(title = "Renamed").bundle())
        assertEquals(replacement, draft.record(now, 0, "boot").time)
        assertEquals(OccurrenceDisposition.COMPLETED, draft.originalOverride!!.disposition)
        assertEquals(override.sourceUrlOverride, draft.originalOverride.sourceUrlOverride)
    }
    @Test fun firstFloatingOccurrenceEditRetainsAuthoredElapsedDurationAcrossTravel() {
        val base = event().copy(time = OrganizerTime(LocalDateTime.parse("2026-03-29T03:30"), LocalDateTime.parse("2026-03-29T05:30"), "Europe/Riga", followDeviceZone = true))
        val zone = ZoneId.of("America/New_York")
        val original = OrganizerRecurrence.between(base, emptyList(), Instant.parse("2026-03-30T00:00:00Z"), Instant.parse("2026-03-31T00:00:00Z"), zone).single()
        val draft = OrganizerDraft.forOccurrence(base, original, null).copy(title = "Renamed")
        val time = requireNotNull(draft.record(now, 0, "boot").time)
        val edited = OrganizerRecurrence.between(base, listOf(OccurrenceOverride(base.id, original.originalStart, OccurrenceDisposition.REPLACED, time)),
            Instant.parse("2026-03-30T00:00:00Z"), Instant.parse("2026-03-31T00:00:00Z"), zone).single()
        assertEquals(original.start, edited.start); assertEquals(original.end, edited.end)
    }
    @Test fun importDeduplicatesUidAcrossFileNamesAndDoesNotResurrectTrash() = runBlocking {
        val first = repository.importCalendar(CalendarImportPreview(listOf(event("first", "shared-uid")), emptyList(), emptyList()))
        val imported = repository.get(first.added.single())!!
        repository.delete(imported.id, imported.version)
        val second = repository.importCalendar(CalendarImportPreview(listOf(event("copied-file", "shared-uid")), emptyList(), emptyList()))
        assertTrue(second.added.isEmpty()); assertEquals(listOf("copied-file"), second.alreadyPresent)
        assertTrue(repository.records().isEmpty()); assertEquals(1, repository.records(true).size)
    }
    @Test fun importAuthorityRevokedMidTransactionRollsBackEveryRecordAndOutbox() = runBlocking {
        var allowed = true; var writes = 0; var schedules = 0
        val guarded = RoomOrganizerRepository(db, schedulesChanged = { schedules++ }, beforeRecordChange = { if (++writes == 2) allowed = false })
        val failure = runCatching { EverydayWriteAuthority.run({ check(allowed) }) {
            guarded.importCalendar(CalendarImportPreview(listOf(event("one"), event("two")), emptyList(), emptyList()))
        } }.exceptionOrNull()
        assertNotNull(failure); assertTrue(repository.records(true).isEmpty()); assertTrue(repository.pendingScheduleIds().isEmpty()); assertEquals(0, schedules)
    }
    @Test fun cacheClearInvalidatesOldRefreshAndPreservesDurableEndpointQuota() = runBlocking {
        val sources = RoomCollectorRepository(db)
        val source = sources.save(CollectorSource("source", "Feed", "Publisher", CollectorKind.FEED, SourceCategory.WORLD,
            "en", "https://example.org/feed", selected = true), null)
        val claim = (sources.reserve(source.id, source.version, now, true) as CollectorAdmission.Accepted).reservation
        val next = sources.clearSnapshot(source.id, source.version)
        assertTrue(next.version > source.version)
        assertFalse(sources.finish(claim, null, CollectorAttempt(source.id, now, CollectorAttemptState.CANCELLED)))
        assertEquals(1, db.collectorDao().requestCount(0)); assertNull(sources.state(source.id)!!.snapshot)
        assertEquals(CollectorAttemptState.NEVER, sources.state(source.id)!!.attempt.state)
    }
    @Test fun adultActionRevisionCannotRegainAuthorityAfterKidsRoundTrip() = runBlocking {
        val preferences = KidsPreferences(context)
        preferences.setEnabled(false)
        val old = preferences.adultActionRevision()
        preferences.setEnabled(true); preferences.setEnabled(false)
        assertTrue(runCatching { preferences.requireAdultAction(old) }.isFailure)
        preferences.requireAdultAction(preferences.adultActionRevision())
    }

    @Test fun restoredAddFormResolvesItsCommittedSourceWithoutCreatingOrOverwritingAnother() = runBlocking {
        val sources = RoomCollectorRepository(db)
        val draft = CollectorSource("custom:00000000-0000-0000-0000-000000000001", "Feed", "Publisher", CollectorKind.FEED,
            SourceCategory.WORLD, "en", "https://example.org/feed")
        val saved = sources.saveDraft(draft)
        assertEquals(saved, RoomCollectorRepository(db).saveDraft(draft))
        assertEquals(1, sources.states().size)
        sources.saveDraft(draft.copy(id = "custom:00000000-0000-0000-0000-000000000002", title = "Second feed"))
        assertEquals(2, sources.states().size)
        sources.save(saved.copy(title = "Changed independently"), saved.version)
        assertTrue(runCatching { sources.saveDraft(draft) }.exceptionOrNull() is CollectorConflict)
        assertEquals("Changed independently", sources.state(saved.id)!!.source.title)
    }
}
