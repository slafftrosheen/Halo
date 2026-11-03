package com.halo.app.everyday.briefing

import com.halo.app.daily.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import java.time.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class EverydayBriefTest {
    private val now = Instant.parse("2026-11-01T08:00:00Z")
    private val zone = ZoneId.of("Europe/Riga")
    private val localSource: CollectorSource = StarterSourceCatalogue.local().first()
    private val local: CollectorState = CollectorState(localSource, null, CollectorAttempt(localSource.id, null, CollectorAttemptState.NEVER))
    private val news = CollectorSource("news", "News", "Publisher", CollectorKind.FEED, SourceCategory.WORLD, "en", "https://example.org/feed", selected = true)
    private fun input(records: List<OrganizerRecord> = emptyList(), sources: List<CollectorState> = listOf(local),
        period: BriefPeriod = BriefPeriod.ON_DEMAND, baseline: BriefBaseline? = null, audience: BriefAudience = BriefAudience.EVERYDAY) =
        BriefInput(now, zone, audience, period, sources, records, emptyList(), baseline = baseline, attentionObservedAt = now)
    private fun task(id: String = "task", version: Long = 1) = OrganizerRecord(id, OrganizerKind.TASK, "Plan", version = version, createdAt = now)
    private fun feed(title: String, at: Instant = now) = CollectorState(news,
        CollectorSnapshot(news.id, news.version, CollectorPayload.Articles(listOf(ArticleFact("one", title, "Text", "https://example.org/one", "Publisher", now, "en"))),
            title, at, at, at.plusSeconds(3600)), CollectorAttempt(news.id, at, CollectorAttemptState.CURRENT))

    @Test fun localBriefIsUsefulWithoutAnyCollectorSnapshotAndProjectionIsDeterministic() {
        val request = input(listOf(task()))
        val first = EverydayBriefEngine.project(request)
        assertEquals(first, EverydayBriefEngine.project(request))
        assertEquals("Plan", first.document.facts.single().title)
        assertEquals(now, first.document.sources.single { it.id == localSource.id }.verifiedAt)
        assertFalse(first.document.baselineMissing)
    }
    @Test fun absentMorningBaselineIsNeverReportedAsNoChanges() {
        val document = EverydayBriefEngine.project(input(listOf(task()), period = BriefPeriod.CHANGES)).document
        assertTrue(document.baselineMissing); assertNull(document.baselineAt)
        assertTrue(BriefPresentation.comparisonNotice(document, Locale.ENGLISH)!!.contains("no matching morning brief"))
    }
    private fun attention(id: String = "approval") = DailyAssistantItem(id, DailyAssistantKind.APPROVAL,
        DailyAssistantPriority.NEEDS_YOU_NOW, "Review local request", null, DailyAssistantAction.OpenVoice, id, now)

    @Test fun emptyAttentionBaselineCanReportNewItemsAndResolvedItemsAreRemoved() {
        val morning = EverydayBriefEngine.project(input(period = BriefPeriod.MORNING))
        assertTrue("halo:attention" in morning.baseline.observedSources)
        val request = input(period = BriefPeriod.CHANGES, baseline = morning.baseline)
            .copy(now = now.plusSeconds(60), attention = DailyAssistantSnapshot(items = listOf(attention())))
        val added = EverydayBriefEngine.project(request)
        assertEquals(BriefChangeKind.ADDED, added.document.changes.single().kind)
        assertEquals(BriefFactKind.ATTENTION, added.document.changes.single().fact!!.kind)
        val resolved = EverydayBriefEngine.project(request.copy(now = now.plusSeconds(120),
            attention = DailyAssistantSnapshot(), attentionObservedAt = now.plusSeconds(120), baseline = added.baseline))
        assertTrue(resolved.document.facts.isEmpty())
        assertEquals(BriefChangeKind.REMOVED, resolved.document.changes.single().kind)
        assertEquals(added.document.facts.single().id, resolved.document.changes.single().factId)
        assertFalse("halo:attention" in resolved.document.comparisonUnavailableSources)
    }

    @Test fun unavailableOrStaleAttentionDoesNotAssertResolutionButObservedEmptyDoes() {
        val morning=EverydayBriefEngine.project(input(period=BriefPeriod.MORNING)
            .copy(attention=DailyAssistantSnapshot(items=listOf(attention()))))
        for(observed in listOf<Instant?>(null,now.minusSeconds(300))) {
            val unavailable=EverydayBriefEngine.project(input(period=BriefPeriod.CHANGES,baseline=morning.baseline)
                .copy(attentionObservedAt=observed))
            assertTrue(unavailable.document.changes.isEmpty())
            assertTrue("halo:attention" in unavailable.document.comparisonUnavailableSources)
        }
        val empty=EverydayBriefEngine.project(input(period=BriefPeriod.CHANGES,baseline=morning.baseline))
        assertEquals(BriefChangeKind.REMOVED,empty.document.changes.single().kind)
    }

    @Test fun emptyKidsAttentionObservationDoesNotExposeAdultItems() {
        val request = input(period = BriefPeriod.MORNING, audience = BriefAudience.KIDS)
            .copy(attention = DailyAssistantSnapshot(items = listOf(attention("adult"), attention("shared"))),
                childLocalFactIds = setOf("shared"))
        val morning = EverydayBriefEngine.project(request)
        assertEquals(listOf("shared"), morning.document.facts.map { it.values["localId"] })
        val later = EverydayBriefEngine.project(request.copy(period = BriefPeriod.CHANGES, baseline = morning.baseline,
            attention = DailyAssistantSnapshot(items = listOf(attention("adult")))))
        assertTrue(later.document.facts.isEmpty())
        assertEquals(morning.document.facts.single().id, later.document.changes.single().factId)
        assertFalse("halo:attention" in later.document.comparisonUnavailableSources)
    }
    @Test fun comparesStableFactsAndConditionalVerificationDoesNotInventNewsChanges() {
        val morning = EverydayBriefEngine.project(input(listOf(task()), listOf(local, feed("Morning")), BriefPeriod.MORNING))
        val verified = feed("Morning", now.plusSeconds(3600))
        val later = EverydayBriefEngine.project(input(listOf(task(version = 2)), listOf(local, verified), BriefPeriod.CHANGES, morning.baseline)
            .copy(now = now.plusSeconds(3600)))
        assertEquals(1, later.document.changes.size)
        assertEquals(BriefChangeKind.UPDATED, later.document.changes.single().kind)
        assertEquals(BriefFactKind.TASK, later.document.changes.single().fact!!.kind)
    }
    @Test fun mutedRemovedAndUnreadSourcesDoNotLookLikeDeletedNews() {
        val morning = EverydayBriefEngine.project(input(sources = listOf(local, feed("Morning")), period = BriefPeriod.MORNING))
        val later = EverydayBriefEngine.project(input(sources = listOf(local, feed("Morning").let { it.copy(source = it.source.copy(muted = true)) }),
            period = BriefPeriod.CHANGES, baseline = morning.baseline))
        assertTrue(later.document.changes.isEmpty()); assertTrue(later.document.facts.none { it.sourceId == news.id })
    }
    @Test fun sourceApprovalDoesNotExposeAdultLocalRecordsOrNotesToKids() {
        val workspace = StarterSourceCatalogue.local().first { it.kind == CollectorKind.LOCAL_WORKSPACE }
        val snapshot = CollectorSnapshot(workspace.id, workspace.version, CollectorPayload.Local(listOf(
            LocalFact("adult-note", "NOTE", "Private adult title", "", "halo://workspace/object/adult-note", "1", now))), "notes", now, now, now.plusSeconds(900))
        val request = input(listOf(task("adult"), task("shared")), listOf(local, CollectorState(workspace, snapshot,
            CollectorAttempt(workspace.id, now, CollectorAttemptState.CURRENT)), feed("Adult news")), audience = BriefAudience.KIDS)
            .copy(childRecordIds = setOf("shared"))
        val document = EverydayBriefEngine.project(request).document
        assertEquals(1, document.facts.size); assertEquals("shared", document.facts.single().values["recordId"])
        assertTrue(document.facts.none { it.kind in setOf(BriefFactKind.NOTE, BriefFactKind.HEADLINE) })
    }
    @Test fun expiredWarningsAreExcludedEvenWhenSnapshotWasRecentlyVerified() {
        val source = news.copy(kind = CollectorKind.ALERTS, category = SourceCategory.OFFICIAL)
        val state = CollectorState(source, CollectorSnapshot(source.id, source.version,
            CollectorPayload.Warnings(listOf(WarningFact("expired", "Old warning", "Text", null, "severe", now.minusSeconds(3600), now.minusSeconds(1), null, "Publisher"))),
            "warnings", now.minusSeconds(7200), now, now.plusSeconds(3600)), CollectorAttempt(source.id, now, CollectorAttemptState.NOT_MODIFIED))
        assertTrue(EverydayBriefEngine.project(input(sources = listOf(state))).document.facts.isEmpty())
    }
    @Test fun quietHoursCrossMidnightAndLateMorningDoesNotAnnounceInEvening() {
        val settings = BriefSettings(proactive = true)
        assertTrue(settings.quiet(LocalTime.of(23, 0))); assertTrue(settings.quiet(LocalTime.of(6, 59)))
        assertFalse(settings.quiet(LocalTime.of(7, 0)))
        val evening = LocalDate.of(2026, 11, 1).atTime(19, 15).atZone(zone).toInstant()
        assertEquals(BriefPeriod.EVENING, BriefSchedulePolicy.due(settings, evening, zone, emptySet()))
        val done = setOf(BriefSchedulePolicy.key(LocalDate.of(2026, 11, 1), zone, BriefPeriod.EVENING))
        assertNull(BriefSchedulePolicy.due(settings, evening, zone, done))
    }
    @Test fun allFourLanguagesHaveRealBriefLabelsAndCachedNewsKeepsAttribution() {
        val brief = EverydayBriefEngine.project(input(sources = listOf(feed("Headline")))).document
        val titles = listOf("en", "ru", "lv", "pl").map { BriefPresentation.title(BriefPeriod.ON_DEMAND, Locale.forLanguageTag(it)) }
        assertEquals(4, titles.distinct().size)
        val spoken = BriefPresentation.spoken(brief, now, false, Locale.ENGLISH)
        assertTrue(spoken.contains("Cached")); assertTrue(spoken.contains("Publisher")); assertTrue(spoken.contains("Headline"))
    }
    @Test fun publicCalendarDescriptionAndLinkCorrectionsAreChanges() {
        val source = news.copy(kind = CollectorKind.PUBLIC_CALENDAR)
        val event = OrganizerRecord("event", OrganizerKind.EVENT, "Meeting", details = "Old details",
            time = OrganizerTime(LocalDateTime.of(2026, 11, 1, 12, 0), zoneId = zone.id), createdAt = now,
            sourceUrl = "https://example.org/old")
        fun state(record: OrganizerRecord): CollectorState = CollectorState(source, CollectorSnapshot(source.id, source.version,
            CollectorPayload.Calendar(CalendarImportPreview(listOf(record), emptyList(), emptyList())), "calendar", now, now, now.plusSeconds(900)),
            CollectorAttempt(source.id, now, CollectorAttemptState.CURRENT))
        val morning = EverydayBriefEngine.project(input(sources = listOf(state(event)), period = BriefPeriod.MORNING))
        val changed = EverydayBriefEngine.project(input(sources = listOf(state(event.copy(details = "Corrected details", sourceUrl = "https://example.org/new"))),
            period = BriefPeriod.CHANGES, baseline = morning.baseline)).document
        assertEquals(BriefChangeKind.UPDATED, changed.changes.single().kind)
        assertEquals("Corrected details", changed.changes.single().fact!!.detail)
        assertEquals("https://example.org/new", changed.changes.single().fact!!.externalUrl)
    }
}
