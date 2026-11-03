package com.halo.app.data.everyday

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.daily.DailyAssistantObservation
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OrganizerBriefCompositionTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val now = Instant.parse("2026-11-03T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val organizer = RoomOrganizerRepository(db, clock)
    private val collectors = RoomCollectorRepository(db)
    private val briefs = RoomBriefRepository(db, clock)
    private val service = EverydayBriefService(briefs, collectors, organizer, { DailyAssistantObservation() },
        { BriefAudience.EVERYDAY }, { emptySet() }, { true }, clock, { ZoneOffset.UTC })
    @After fun close() = db.close()
    private suspend fun seed() = collectors.save(StarterSourceCatalogue.local().first { it.kind == CollectorKind.LOCAL_ORGANIZER }, null)

    @Test fun selectedSectionsPersistWithLegacyDefaultsAndWeeklyUsesSevenDays() = runBlocking {
        seed()
        for (days in listOf(0L, 3L, 6L, 7L)) organizer.save(OrganizerRecord("event:$days", OrganizerKind.EVENT, "Meeting $days",
            time = OrganizerTime(LocalDateTime.ofInstant(now.plusSeconds(days * 86400), ZoneOffset.UTC),
                LocalDateTime.ofInstant(now.plusSeconds(days * 86400 + 3600), ZoneOffset.UTC), "UTC"), createdAt = now), null)
        val settings = briefs.saveSettings(BriefSettings(sections = setOf(BriefSection.EVENTS)), 0)
        assertEquals(settings, RoomBriefRepository(db, clock).settings())
        assertEquals(BriefSection.entries.toSet(), BriefCodec.settings(org.json.JSONObject(BriefCodec.encode(BriefSettings())).apply { remove("sections") }.toString()).sections)
        assertEquals(listOf("Meeting 0"), service.build(BriefPeriod.MIDDAY).facts.map { it.title })
        assertEquals(setOf("Meeting 0", "Meeting 3", "Meeting 6"), service.build(BriefPeriod.WEEKLY).facts.map { it.title }.toSet())
        assertEquals(0, db.collectorDao().requestCount(0))
    }
    @Test fun researchAndHaloSectionsKeepProvenanceWithoutLeakingDerivedResults() = runBlocking {
        seed()
        val work = organizer.save(OrganizerRecord("halo", OrganizerKind.TASK, "Printer research", createdAt = now,
            commitment = OrganizerCommitment(CommitmentOwner.SHARED, state = HaloWorkState.NEEDS_USER,
                result = OrganizerWorkResult("Private removed source body", now), nextHumanAction = "Review the result")), null)
        val source = OrganizerResearch(collectors, clock).saveSource(ResearchSourceQuery("printer", "wikipedia"), "en", true)
        val claim = (collectors.reserve(source.id, source.version, now, true) as CollectorAdmission.Accepted).reservation
        collectors.finish(claim, CollectorSnapshot(source.id, source.version, CollectorPayload.Articles(listOf(
            ArticleFact("paper", "Printer paper", "Dated reference", "https://example.org/paper", "Publisher", null, "en"))), "revision", now, now, now.plusSeconds(3600)),
            CollectorAttempt(source.id, now, CollectorAttemptState.CURRENT))
        briefs.saveSettings(BriefSettings(sections = setOf(BriefSection.HALO_WORK, BriefSection.RESEARCH)), 0)
        val brief = service.build(BriefPeriod.MIDDAY)
        val halo = brief.facts.single { it.values["recordId"] == work.id }
        assertEquals("", halo.detail); assertEquals("SHARED", halo.values["owner"])
        val research = brief.facts.single { it.values["research"] == "true" }
        assertEquals(source.id, research.sourceId); assertEquals("printer", research.values["query"])
        assertEquals(BriefSection.RESEARCH, EverydayBriefEngine.section(research))
        assertTrue(BriefPresentation.spoken(brief, now, false, Locale.ENGLISH).contains("Research / Web"))
        assertFalse(BriefPresentation.spoken(brief, now, false, Locale.ENGLISH).contains("Private removed"))
        collectors.save(source.copy(selected = false), source.version)
        assertTrue(service.visible(brief.id)!!.facts.none { it.values["research"] == "true" })
    }
    @Test fun deselectedSectionsAlsoWithholdHistoricalChangesAndTheirRemovalCounts() = runBlocking {
        seed()
        val task = organizer.save(OrganizerRecord("old", OrganizerKind.TASK, "Task", createdAt = now), null)
        service.build(BriefPeriod.MORNING)
        organizer.save(task.copy(title = "Changed task"), task.version)
        val changes = service.build(BriefPeriod.CHANGES)
        assertEquals(1, changes.changes.size)
        briefs.saveSettings(BriefSettings(sections = setOf(BriefSection.EVENTS)), 0)
        assertTrue(service.visible(changes.id)!!.changes.isEmpty())
        assertTrue(service.build(BriefPeriod.CHANGES).changes.isEmpty())
    }
}
