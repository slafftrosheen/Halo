package com.halo.app.everyday.presentation

import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.collectors.*
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant

class EverydayLibraryProjectionTest {
    private val now=Instant.parse("2026-11-02T12:00:00Z")
    @Test fun familyProjectionContainsOnlySharedRecordsApprovedSourcesAndGenericBriefMetadata() {
        val adult=OrganizerRecord("adult",OrganizerKind.TASK,"Secret plan",createdAt=now)
        val shared=adult.copy(id="shared",title="Shared plan")
        val clock=WorldClockRecord("city","Private home city","Europe/Riga")
        val privateSource=StarterSourceCatalogue.external().first().copy(id="private",selected=true)
        val approved=privateSource.copy(id="approved",title="Approved",childApproved=true)
        fun state(s: CollectorSource)=CollectorState(s,null,CollectorAttempt(s.id,null,CollectorAttemptState.NEVER))
        val document=EverydayBriefEngine.project(BriefInput(now,java.time.ZoneOffset.UTC,BriefAudience.KIDS,BriefPeriod.ON_DEMAND,emptyList(),emptyList(),emptyList()),BriefSettings()).document
            .copy(facts=listOf(BriefFact("secret",BriefFactKind.NOTE,"Revoked note title","Revoked body","source","1","halo://workspace",values=mapOf("localId" to "secret"))))
        val rows=EverydayLibraryProjection.project(listOf(adult,shared),listOf(document),listOf(clock),listOf(state(privateSource),state(approved)),BriefSettings(childRecordIds=setOf(shared.id)),true)
        assertEquals(setOf("shared","approved",document.id),rows.map {it.id}.toSet())
        val encoded=rows.toString()
        listOf("Secret plan","Private home city","Revoked note title","Revoked body").forEach {assertFalse(it,encoded.contains(it))}
        assertEquals("halo_organizer_get",rows.single {it.kind=="ORGANIZER"}.manageTool)
        assertEquals(EverydayTarget.Record(shared.id,shared.version),rows.single {it.kind=="ORGANIZER"}.target)
    }
    @Test fun disabledMutedUnselectedAndDeletedItemsAreExcludedWithoutMakingDuplicateOwners() {
        val record=OrganizerRecord("one",OrganizerKind.TASK,"Plan",createdAt=now)
        val source=StarterSourceCatalogue.external().first()
        val states=listOf(source.copy(id="disabled",selected=true,enabled=false),source.copy(id="muted",selected=true,muted=true),source.copy(id="unselected"))
            .map {CollectorState(it,null,CollectorAttempt(it.id,null,CollectorAttemptState.NEVER))}
        val rows=EverydayLibraryProjection.project(listOf(record,record.copy(id="trash",deletedAt=now)),emptyList(),emptyList(),states,BriefSettings(),false)
        assertEquals(listOf("everyday:ORGANIZER:one"),rows.map {it.key});assertEquals(record.version,rows.single().version)
    }
}
