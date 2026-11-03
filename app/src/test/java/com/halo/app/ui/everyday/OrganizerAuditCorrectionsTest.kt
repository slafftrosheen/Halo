package com.halo.app.ui.everyday

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.halo.app.everyday.organizer.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class OrganizerAuditCorrectionsTest {
    @Test fun everyImportReasonAndSeriesWithheldWrapperHasReviewedRussianAndLatvianCopy() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        fun resources(tag: String)=app.createConfigurationContext(Configuration(app.resources.configuration).apply {setLocale(Locale.forLanguageTag(tag))}).resources
        for(reason in CalendarImportReason.entries) {
            val issue=CalendarImportIssue(1,null,"Diagnostic English must not reach UI",reasonCode=reason,
                reasonArguments=if(reason==CalendarImportReason.IANA_RULES) listOf("Europe/Riga") else emptyList(),seriesWithheld=true)
            val english=calendarImportIssueText(resources("en"),issue)
            for(language in listOf("ru","lv")) {
                val copy=calendarImportIssueText(resources(language),issue)
                assertNotEquals(reason.name,english,copy)
                assertFalse(copy.contains("Diagnostic English"))
                assertFalse(copy.contains("Series withheld:"))
                if(reason==CalendarImportReason.IANA_RULES) assertTrue(copy.contains("Europe/Riga"))
            }
        }
        val calendar="BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:1\r\nDTSTART;TZID=Europe/Riga:20261103T090000\r\nDURATION:PT1H\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val warning=OrganizerIcs.preview(calendar.toByteArray(),"local",ZoneOffset.UTC,Instant.EPOCH).issues.single()
        assertEquals(CalendarImportReason.IANA_RULES,warning.reasonCode)
        assertFalse(calendarImportIssueText(resources("lv"),warning).contains("No embedded"))
    }
    @Test fun linkSearchFindsOlderPlansActivitiesAndTimelineBeforeApplyingTheDisplayLimit() {
        val items=(1..120).flatMap {n -> listOf("TASK","ACTIVITY","TIMELINE").map {kind -> OrganizerLinkChoice("$kind:$n","$kind номер $n",kind)} }
        val selected=listOf("TASK:1")
        val found=organizerLinkSearch(items,"НОМЕР 120",selected)
        assertEquals(setOf("TASK:1","TASK:120","ACTIVITY:120","TIMELINE:120"),found.map {it.id}.toSet())
        assertEquals("TASK:1",found.first().id)
        assertEquals(90,organizerLinkSearch(items,"",emptyList()).size)
        val long=OrganizerLinkChoice("run:long","Truncated title","ACTIVITY","x".repeat(130)+"needle")
        assertEquals(listOf(long),organizerLinkSearch(listOf(long),"needle",emptyList()))
    }
}
