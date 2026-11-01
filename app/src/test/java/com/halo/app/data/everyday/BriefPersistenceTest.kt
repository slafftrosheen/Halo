package com.halo.app.data.everyday

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.daily.DailyAssistantSnapshot
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import java.time.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BriefPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val start = Instant.parse("2026-11-01T08:00:00Z")
    private class TestClock(var value: Instant) : Clock() {
        override fun instant() = value
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
    }
    private val clock = TestClock(start)
    private val zone = ZoneId.of("Europe/Riga")
    private val briefs = RoomBriefRepository(db, clock)
    private val collectors = RoomCollectorRepository(db)
    private val organizer = RoomOrganizerRepository(db, clock, { zone })
    private var audience = BriefAudience.EVERYDAY
    private val service = EverydayBriefService(briefs, collectors, organizer, { DailyAssistantSnapshot() },
        { audience }, { emptySet() }, { true }, clock, { zone })
    private suspend fun seed(): OrganizerRecord {
        collectors.save(StarterSourceCatalogue.local().first { it.kind == CollectorKind.LOCAL_ORGANIZER }, null)
        return organizer.save(OrganizerRecord("task", OrganizerKind.TASK, "Private plan", createdAt = start), null)
    }
    @After fun close() { db.close() }
    @Test fun morningBaselineIsAtomicImmutableAndSurvivesReopen() = runBlocking {
        val task = seed()
        val first = service.build(BriefPeriod.MORNING)
        clock.value = start.plusSeconds(3600)
        organizer.save(task.copy(title = "Changed plan"), task.version)
        service.build(BriefPeriod.MORNING)
        val reopened = RoomBriefRepository(db, clock)
        assertEquals(first.generatedAt, reopened.baseline(first.date, zone, BriefAudience.EVERYDAY)!!.capturedAt)
        val changed = service.build(BriefPeriod.CHANGES)
        assertEquals(BriefChangeKind.UPDATED, changed.changes.single().kind)
        assertEquals("Changed plan", changed.changes.single().fact!!.title)
    }
    @Test fun settingsCompareAndSwapRejectsStaleEditsAndRetainsQuietHours() = runBlocking {
        val saved = briefs.saveSettings(BriefSettings(proactive = true, quietStart = LocalTime.of(21, 30), childRecordIds = setOf("task")), 0)
        assertEquals(1L, saved.version)
        assertEquals(saved, RoomBriefRepository(db, clock).settings())
        assertThrows(IllegalStateException::class.java) { runBlocking { briefs.saveSettings(saved.copy(proactive = false), 0) } }
        assertTrue(briefs.settings().proactive)
    }
    @Test fun reopeningRechecksChildSharingSourceSelectionAndDeletedRecords() = runBlocking {
        val task = seed()
        val original = service.build(BriefPeriod.ON_DEMAND)
        assertEquals(1, original.facts.size)
        audience = BriefAudience.KIDS
        assertTrue(service.visible(original.id)!!.facts.isEmpty())
        briefs.saveSettings(BriefSettings(childRecordIds = setOf(task.id)), 0)
        assertEquals(1, service.visible(original.id)!!.facts.size)
        val source = collectors.states().single().source
        collectors.save(source.copy(selected = false), source.version)
        assertTrue(service.visible(original.id)!!.facts.isEmpty())
        val current = collectors.state(source.id)!!.source
        collectors.save(current.copy(selected = true), current.version)
        // A prior source configuration never silently re-authorizes its old document.
        assertTrue(service.visible(original.id)!!.facts.isEmpty())
        val newBrief = service.build(BriefPeriod.ON_DEMAND)
        assertEquals(1, newBrief.facts.size)
        organizer.delete(task.id, task.version)
        assertTrue(service.visible(newBrief.id)!!.facts.isEmpty())
    }
    @Test fun scheduledConcurrentRunsClaimOneDeliveryAndDoNotFetchOrSpeak() = runBlocking {
        seed()
        clock.value = LocalDate.of(2026, 11, 1).atTime(7, 30).atZone(zone).toInstant()
        briefs.saveSettings(BriefSettings(proactive = true), 0)
        var deliveries = 0
        val proactive = BriefProactiveCoordinator(briefs, service, { deliveries++; "DISPATCHED" }, clock, { zone })
        coroutineScope { List(3) { async { proactive.runDue() } }.awaitAll() }
        assertEquals(1, deliveries)
        assertEquals(1, db.briefDao().deliveries(0).size)
        assertEquals("DISPATCHED", db.briefDao().deliveries(0).single().state)
        assertNull(proactive.runDue())
        assertEquals(0, db.collectorDao().requestCount(0))
    }
    @Test fun cancellationBeforeCanonicalSubmissionResumesTheSameDocument() = runBlocking {
        seed()
        clock.value = LocalDate.of(2026, 11, 1).atTime(7, 30).atZone(zone).toInstant()
        briefs.saveSettings(BriefSettings(proactive = true), 0)
        var cancelled = true
        val documents = mutableListOf<String>()
        val coordinator = BriefProactiveCoordinator(briefs, service, { document -> documents += document.id
            if (cancelled) throw CancellationException("cancel") else "DISPATCHED" }, clock, { zone })
        assertThrows(CancellationException::class.java) { runBlocking { coordinator.runDue() } }
        assertEquals("RETRYABLE", db.briefDao().deliveries(0).single().state)
        cancelled = false
        assertNotNull(coordinator.runDue())
        assertEquals(2, documents.size)
        assertEquals(1, documents.distinct().size)
        assertNull(coordinator.runDue())
    }
    @Test fun historyIsBoundedAndClearAlsoClearsComparisonBaselines() = runBlocking {
        seed()
        service.build(BriefPeriod.MORNING)
        repeat(65) { clock.value = start.plusSeconds(it.toLong() + 1); service.build(BriefPeriod.ON_DEMAND) }
        assertEquals(60, briefs.recent(60).size)
        assertNotNull(briefs.baseline(start.atZone(zone).toLocalDate(), zone, audience))
        clock.value = start.plusSeconds(31 * 86400L)
        service.build(BriefPeriod.ON_DEMAND)
        assertEquals(1, briefs.recent(60).size)
        assertNull(briefs.baseline(start.atZone(zone).toLocalDate(), zone, audience))
        service.build(BriefPeriod.MORNING)
        briefs.clear()
        assertTrue(briefs.recent().isEmpty())
        assertTrue(service.build(BriefPeriod.CHANGES).baselineMissing)
    }
    @Test fun settingsAffectIdentityEvenWithIdenticalInputClockAndFacts() = runBlocking {
        seed()
        val first = service.build(BriefPeriod.ON_DEMAND)
        briefs.saveSettings(BriefSettings(pinnedRecordIds = setOf("task")), 0)
        val second = service.build(BriefPeriod.ON_DEMAND)
        assertNotEquals(first.id, second.id)
        assertTrue(second.facts.single().priority > first.facts.single().priority)
    }
    @Test fun coldClaimRecoveryUsesCanonicalUnknownAndNeverSubstitutesMissingHistory() = runBlocking {
        seed()
        clock.value = LocalDate.of(2026, 11, 1).atTime(7, 30).atZone(zone).toInstant()
        briefs.saveSettings(BriefSettings(proactive = true), 0)
        val document = service.build(BriefPeriod.MORNING)
        val key = BriefSchedulePolicy.key(document.date, zone, BriefPeriod.MORNING)
        assertTrue(briefs.claimDelivery(key, document.id, clock.instant()))
        clock.value = clock.instant().plusSeconds(301)
        var delivered = 0
        val coordinator = BriefProactiveCoordinator(briefs, service, { delivered++; "DISPATCHED" }, clock, { zone }, { "COMPLETION_UNKNOWN" })
        assertNull(coordinator.runDue())
        assertEquals(0, delivered)
        assertEquals("COMPLETION_UNKNOWN", db.briefDao().deliveries(0).single().state)
        // A separate period lost before canonical submission can recover, but never replace deleted content.
        clock.value = document.date.atTime(19, 10).atZone(zone).toInstant()
        val evening = service.build(BriefPeriod.EVENING)
        val eveningKey = BriefSchedulePolicy.key(evening.date, zone, BriefPeriod.EVENING)
        briefs.claimDelivery(eveningKey, evening.id, clock.instant())
        briefs.remove(evening.id)
        clock.value = clock.instant().plusSeconds(301)
        assertNull(BriefProactiveCoordinator(briefs, service, { delivered++; "DISPATCHED" }, clock, { zone }).runDue())
        assertEquals(0, delivered)
        assertEquals("CANCELLED", db.briefDao().deliveries(0).single { it.key == eveningKey }.state)
    }
    @Test fun unsubmittedClaimRecoversAfterLeaseWithOriginalIdentityAndExpiry() = runBlocking {
        seed()
        clock.value = LocalDate.of(2026, 11, 1).atTime(7, 30).atZone(zone).toInstant()
        briefs.saveSettings(BriefSettings(proactive = true), 0)
        val document = service.build(BriefPeriod.MORNING)
        val key = BriefSchedulePolicy.key(document.date, zone, BriefPeriod.MORNING)
        briefs.claimDelivery(key, document.id, clock.instant())
        clock.value = clock.instant().plusSeconds(301)
        var restored: EverydayBrief? = null
        BriefProactiveCoordinator(briefs, service, { restored = it; "DISPATCHED" }, clock, { zone }).runDue()
        assertEquals(document.id, restored!!.id)
        assertEquals(document.generatedAt, restored!!.generatedAt)
        assertEquals(1, briefs.recent().size)
    }
    @Test fun adultStoredBriefReappliesKidsLimitsToFactsAndComparisonChanges() = runBlocking {
        seed()
        val ids = mutableSetOf("task")
        repeat(19) { val id = "task-$it"; ids += id
            organizer.save(OrganizerRecord(id, OrganizerKind.TASK, "Task $it", createdAt = start), null) }
        briefs.saveSettings(BriefSettings(taskLimit = 20, childRecordIds = ids), 0)
        val adult = service.build(BriefPeriod.MORNING)
        assertEquals(20, adult.facts.size)
        organizer.records().forEach { organizer.save(it.copy(title = "Changed ${it.title}"), it.version) }
        clock.value = clock.instant().plusSeconds(60)
        val changes = service.build(BriefPeriod.CHANGES)
        assertEquals(20, changes.changes.size)
        audience = BriefAudience.KIDS
        assertEquals(12, service.visible(adult.id)!!.facts.size)
        val childReopen = service.visible(changes.id)!!
        assertTrue(childReopen.changes.isEmpty())
        assertTrue(childReopen.baselineMissing)
        assertNull(childReopen.baselineAt)
        service.build(BriefPeriod.MORNING)
        organizer.records().forEach { organizer.save(it.copy(title = "Shared change ${it.title}"), it.version) }
        clock.value = clock.instant().plusSeconds(60)
        val childChanges = service.build(BriefPeriod.CHANGES)
        assertEquals(12, childChanges.changes.size)
        assertFalse(childChanges.baselineMissing)
    }
}
