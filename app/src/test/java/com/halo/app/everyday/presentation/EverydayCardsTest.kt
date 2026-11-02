package com.halo.app.everyday.presentation

import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.collectors.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class EverydayCardsTest {
    private val now=Instant.parse("2026-11-03T12:00:00Z")
    private val zone=ZoneId.of("UTC")
    private fun input(records: List<OrganizerRecord> = emptyList(),occurrences: List<OrganizerOccurrence> = emptyList(),sources: List<CollectorState> = emptyList()) =
        EverydaySnapshot(now,zone,records,occurrences,sources,emptyList())
    private fun task(id: String="task")=OrganizerRecord(id,OrganizerKind.TASK,"Secret plan title",time=OrganizerTime(LocalDateTime.parse("2026-11-03T09:00"),zoneId="UTC"),createdAt=now)
    private fun feed(id: String,published: Instant,retrieved: Instant,expires: Instant)=CollectorSource(id,id,"Publisher $id",CollectorKind.FEED,SourceCategory.WORLD,"en","https://example.org/$id",selected=true).let { source ->
        CollectorState(source,CollectorSnapshot(id,source.version,CollectorPayload.Articles(listOf(ArticleFact("article:$id","Headline $id","Body","https://example.org/$id/story",source.publisher,published,"en"))),
            "revision:$id",retrieved,retrieved,expires),CollectorAttempt(id,retrieved,CollectorAttemptState.CURRENT))
    }
    @Test fun independentlyConfiguredClocksRetainTheirOwnZoneAndDateAcrossMidnight() {
        val tokyo=EverydayCardConfig(EverydayCardKind.CLOCK,"Asia/Tokyo","Tokyo")
        val newYork=EverydayCardConfig(EverydayCardKind.CLOCK,"America/New_York","New York")
        val late=input().copy(now=Instant.parse("2026-11-03T23:30:00Z"))
        val east=EverydayCards.project(tokyo,late,Locale.US)
        val west=EverydayCards.project(newYork,late,Locale.US)
        assertNotEquals(east.value,west.value);assertNotEquals(east.rows.first().title,west.rows.first().title)
        assertEquals(EverydayTarget.Clock("Asia/Tokyo","Tokyo"),east.target)
        assertEquals(tokyo,EverydayCardConfig.from(tokyo.fields(),EverydayCardKind.CLOCK))
    }
    @Test fun externalFacesNeverContainPrivateTitlesOrDetailsEvenIfALegacyFlagIsFalse() {
        val secret=task().copy(details="Private body and address")
        val face=EverydayCards.project(EverydayCardConfig(EverydayCardKind.TASKS,hidePrivate=false),input(listOf(secret)),Locale.US,externalHost=true)
        assertFalse((face.title+face.value+face.rows.joinToString { it.title+it.detail }+face.status).contains("Secret"))
        assertFalse(face.rows.any { it.detail.contains("Private body") })
        assertEquals(EverydayTarget.Record(secret.id,secret.version),face.rows.first().target)
        assertTrue(EverydayCards.project(EverydayCardConfig(EverydayCardKind.TASKS),input(listOf(secret)),Locale.US).rows.first().title.contains("Secret"))
    }
    @Test fun kidsOnlyProjectExplicitlySharedRecordsAndApprovedFeeds() {
        val a=task("adult");val b=task("shared")
        val public=feed("adult-feed",now,now,now.plusSeconds(3600))
        val child=feed("child-feed",now,now,now.plusSeconds(3600)).let { it.copy(source=it.source.copy(childApproved=true)) }
        val data=input(listOf(a,b),sources=listOf(public,child)).copy(kids=true,sharedRecordIds=setOf(b.id))
        assertEquals(listOf(EverydayTarget.Record(b.id,b.version)),EverydayCards.project(EverydayCardConfig(EverydayCardKind.TASKS),data,Locale.US).rows.map { it.target })
        assertEquals(listOf("Headline child-feed"),EverydayCards.project(EverydayCardConfig(EverydayCardKind.HEADLINES),data,Locale.US).rows.map { it.title })
    }
    @Test fun mixedHeadlinesKeepPerRowProvenanceAndUseTheOldestSelectedRetrieval() {
        val fresh=feed("fresh",now,now.minusSeconds(60),now.plusSeconds(3600))
        val stale=feed("stale",now.minusSeconds(20),now.minusSeconds(86400),now.minusSeconds(3600)).let { it.copy(attempt=it.attempt.copy(state=CollectorAttemptState.FAILED)) }
        val face=EverydayCards.project(EverydayCardConfig(EverydayCardKind.HEADLINES),input(sources=listOf(fresh,stale)).copy(power=true),Locale.US)
        assertTrue(face.stale);assertTrue(face.lastRefreshFailed);assertEquals(stale.snapshot!!.retrievedAt,face.observedAt)
        assertEquals(listOf(fresh.source.publisher,stale.source.publisher),face.rows.map { it.publisher })
        assertFalse(face.rows.first().stale);assertTrue(face.rows.last().stale)
    }
    @Test fun recurringTaskUsesItsUncompletedMovedOccurrenceRatherThanTheCompletedMasterDate() {
        val series=task().copy(repeat=RepeatRule(RepeatFrequency.DAILY,count=5))
        val first=OrganizerOccurrence(series,now.minusSeconds(10800),now.minusSeconds(10800),null,true)
        val next=OrganizerOccurrence(series.copy(title="Moved task"),now.plusSeconds(75600),now.plusSeconds(90000),null,false)
        val face=EverydayCards.project(EverydayCardConfig(EverydayCardKind.TASKS),input(listOf(series),listOf(first,next)),Locale.US)
        assertEquals("Moved task",face.rows.single().title)
        assertEquals(EverydayTarget.Record(series.id,series.version,next.originalStart),face.rows.single().target)
        assertTrue(face.status.contains("Due today: 0"));assertTrue(face.status.contains("last 30 days: 0"))
    }
    @Test fun oldOverdueCountIsExplicitlyBoundedAndDoesNotInventRecentOccurrences() {
        val old=task().copy(time=OrganizerTime(LocalDateTime.parse("2026-01-01T09:00"),zoneId="UTC"))
        val face=EverydayCards.project(EverydayCardConfig(EverydayCardKind.TASKS),input(listOf(old)),Locale.US)
        assertTrue(face.value.startsWith("1"));assertTrue(face.status.contains("last 30 days: 0"))
    }
    @Test fun dashboardHasSeparateModuleSourcesAndDoesNotClaimANewRetrieval() {
        val news=feed("chosen",now,now.minusSeconds(100),now.plusSeconds(3600))
        val other=feed("other",now.plusSeconds(1),now,now.plusSeconds(3600))
        val config=EverydayCardConfig(EverydayCardKind.DASHBOARD,sections=listOf(EverydayCardKind.HEADLINES,EverydayCardKind.DEVICE),headlineSourceId=news.source.id)
        val face=EverydayCards.project(config,input(sources=listOf(news,other)),Locale.US)
        assertNull(face.observedAt)
        assertEquals(EverydayTarget.Source(news.source.id,news.source.version,news.snapshot!!.contentRevision,"article:chosen"),face.rows.first().target)
        assertEquals(config,EverydayCardConfig.from(config.fields(),EverydayCardKind.DASHBOARD))
    }
    @Test fun upcomingMovedEventCarriesOriginalOccurrenceIdentity() {
        val event=task().copy(kind=OrganizerKind.EVENT,time=OrganizerTime(LocalDateTime.parse("2026-11-04T09:00"),LocalDateTime.parse("2026-11-04T10:00"),"UTC"))
        val original=now.plusSeconds(75600)
        val moved=OrganizerOccurrence(event.copy(title="Moved meeting"),original,now.plusSeconds(172800),now.plusSeconds(176400),false)
        assertEquals(EverydayTarget.Record(event.id,event.version,original),EverydayCards.project(EverydayCardConfig(EverydayCardKind.UPCOMING),input(listOf(event),listOf(moved)),Locale.US).target)
    }
    @Test fun recurringDueTodayCountsAllUnfinishedOccurrencesWhileOverdueIsBounded() {
        val series=task().copy(repeat=RepeatRule(RepeatFrequency.DAILY,count=5))
        val yesterday=now.minusSeconds(86400)
        val today=now.minusSeconds(3600)
        val occurrences=listOf(OrganizerOccurrence(series,yesterday,yesterday,null,false),OrganizerOccurrence(series,today,today,null,false),
            OrganizerOccurrence(series,now.plusSeconds(86400),now.plusSeconds(86400),null,false))
        val face=EverydayCards.project(EverydayCardConfig(EverydayCardKind.TASKS),input(listOf(series),occurrences),Locale.US)
        assertTrue(face.status.contains("Due today: 1"));assertTrue(face.status.contains("last 30 days: 2"))
    }
    @Test fun phoneClockTargetFollowsThePhoneRatherThanAPreviousResolvedCity() {
        assertEquals(EverydayTarget.Clocks,EverydayCards.project(EverydayCardConfig(EverydayCardKind.CLOCK),input(),Locale.US).target)
    }
    @Test fun selectedWeatherWithoutACacheReportsTheFailedAttemptAndOpensThatSource() {
        val source=CollectorSource("weather","Riga","Open-Meteo",CollectorKind.WEATHER,SourceCategory.WEATHER,"en","https://example.org/weather",selected=true,place=WeatherPlace("Riga",56.9,24.1,"Europe/Riga"))
        val state=CollectorState(source,null,CollectorAttempt(source.id,now,CollectorAttemptState.FAILED,nextAllowedAt=now.plusSeconds(3600)))
        val face=EverydayCards.project(EverydayCardConfig(EverydayCardKind.WEATHER),input(sources=listOf(state)),Locale.US)
        assertEquals(EverydayTarget.Source(source.id,source.version,null),face.target)
        assertTrue(face.status.contains("Last refresh failed"));assertTrue(face.status.contains("Next refresh eligible"));assertNull(face.observedAt)
    }
    @Test fun invalidConfigurationCannotCarryRoutesOrUnsupportedModules() {
        assertTrue(runCatching { EverydayCardConfig.from(mapOf("route" to "private"),EverydayCardKind.CLOCK) }.isFailure)
        assertTrue(runCatching { EverydayCardConfig(EverydayCardKind.CLOCK,zoneId="Unknown/Zone") }.isFailure)
        assertTrue(runCatching { EverydayCardConfig(EverydayCardKind.DASHBOARD,sections=listOf(EverydayCardKind.DASHBOARD)) }.isFailure)
    }
}
