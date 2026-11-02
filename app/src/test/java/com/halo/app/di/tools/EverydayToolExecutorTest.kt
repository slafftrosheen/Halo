package com.halo.app.di.tools

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.*
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.presentation.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class EverydayToolExecutorTest {
    private val app=ApplicationProvider.getApplicationContext<Application>()
    // Robolectric includes the method name in its Windows temp path; keep native SQLite below MAX_PATH.
    private val dbName="e.db"
    private val db=Room.databaseBuilder(app,AppDatabase::class.java,dbName).setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING).allowMainThreadQueries().build()
    private val now=Instant.parse("2026-11-03T12:00:00Z")
    private val clock=Clock.fixed(now,ZoneOffset.UTC)
    private val organizer=RoomOrganizerRepository(db,clock)
    private val useCases=OrganizerUseCases(organizer,clock,{ZoneOffset.UTC},{1000},{"boot"})
    private val sources=RoomCollectorRepository(db)
    private val briefs=RoomBriefRepository(db,clock)
    private var child=false
    private var allowed=true
    private var networkCalls=0
    private val policy=object: RemoteDispatchPolicy {
        override val revision=0L
        override fun acquire(request: RemoteDispatchRequest,expectedRevision: Long,recordActivity: Boolean,cancel: ()->Unit): RemoteDispatchLease=throw RemoteDispatchBlocked()
    }
    private val registry=CollectorRegistry(listOf(object: Collector {
        override val kind=CollectorKind.FEED
        override suspend fun fetch(source: CollectorSource,cached: CollectorSnapshot?): CollectorFetchResult {networkCalls++;error("Local must not fetch")}
    }))
    private val briefService=EverydayBriefService(briefs,sources,organizer,{com.halo.app.daily.DailyAssistantSnapshot()},
        {if(child) BriefAudience.KIDS else BriefAudience.EVERYDAY},{emptySet()},{true},clock,{ZoneOffset.UTC})
    private var opened: EverydayTarget?=null
    private fun executorFor(records: OrganizerUseCases=useCases,sourceReader: CollectorRepository=sources,onAdmission: ()->Unit = {})=EverydayToolExecutor(records,RoomWorldClockRepository(db,clock),OfflineCityCatalogue {error("Not needed")},sourceReader,
        CollectorCoordinator(sources,registry,policy,clock),CollectorPreview(sources,registry,policy,clock),briefs,briefService,{}, {},
        {id,version -> sources.clearSnapshot(id,version)}, {_,_ -> JSONObject()}, {opened=it;true}, {allowed},{child},
        {val family=child;onAdmission();{check(allowed && child==family)}},{1000},{"boot"},clock,readState={EverydayDisclosureSnapshot.capture(db)})
    private val executor=executorFor()
    @After fun close() {db.close();app.deleteDatabase(dbName)}
    private suspend fun call(name: String,args: Map<String,Any> = emptyMap(),interaction: String?="synthetic")=
        executor.execute(ToolCall("synthetic",name,args),ToolExecutionContext(interactionId=interaction))
    private fun json(value: ToolExecutionResult)=JSONObject((value as ToolExecutionResult.Success).output)
    @Test fun typedCreateMoveCompleteTrashUndoUseTheSameCanonicalRecordAndRejectStaleVersions()=runBlocking {
        val created=json(call("halo_organizer_create",mapOf("record" to mapOf("kind" to "TASK","title" to "Local plan"))))
        val id=created.getJSONObject("record").getString("id")
        val edited=json(call("halo_organizer_edit",mapOf("id" to id,"version" to 1,"patch" to mapOf("title" to "Moved plan"))))
        assertEquals(2,edited.getJSONObject("record").getLong("version"))
        assertTrue(call("halo_organizer_edit",mapOf("id" to id,"version" to 1,"patch" to mapOf("title" to "Stale"))) is ToolExecutionResult.Rejected)
        json(call("halo_organizer_action",mapOf("id" to id,"version" to 2,"action" to "TRASH")))
        assertNotNull(organizer.get(id)!!.deletedAt)
        json(call("halo_organizer_action",mapOf("id" to id,"version" to 3,"action" to "RESTORE")))
        assertNull(organizer.get(id)!!.deletedAt);assertEquals("Moved plan",organizer.get(id)!!.title)
    }
    @Test fun selectedSourceRefreshInLocalPerformsZeroTransportCallsAndRetainsTheSource()=runBlocking {
        val source=sources.save(StarterSourceCatalogue.external().first().copy(selected=true),null)
        val result=json(call("halo_source_action",mapOf("id" to source.id,"version" to source.version,"action" to "REFRESH")))
        assertEquals("LOCAL_ONLY",result.getString("attempt"));assertEquals(0,networkCalls);assertFalse(result.getBoolean("admitted"))
    }
    @Test fun kidsReadOnlyExplicitlySharedOrganizerAndCannotMutateOrReadAdultSources()=runBlocking {
        val adult=useCases.create(OrganizerKind.TASK,"Adult-only")
        val shared=useCases.create(OrganizerKind.TASK,"Shared")
        briefs.saveSettings(BriefSettings(childRecordIds=setOf(shared.id)),0)
        child=true
        val result=json(call("halo_organizer_query"));assertFalse(result.toString().contains("Adult-only"));assertTrue(result.toString().contains("Shared"))
        assertTrue(call("halo_organizer_get",mapOf("id" to adult.id)) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_organizer_create",mapOf("record" to mapOf("kind" to "TASK","title" to "No"))) is ToolExecutionResult.Rejected)
        assertEquals(2,organizer.records().size)
    }
    @Test fun sharedChildRecordDoesNotExposeItsAdultLinksOrUnsharedParentIdentity()=runBlocking {
        val parent=useCases.create(OrganizerKind.TASK,"Parent private")
        val shared=useCases.create(OrganizerKind.TASK,"Shared",parentId=parent.id,links=listOf(OrganizerLink("private-note")))
        briefs.saveSettings(BriefSettings(childRecordIds=setOf(shared.id)),0)
        child=true
        val result=json(call("halo_organizer_get",mapOf("id" to shared.id))).toString()
        assertFalse(result.contains("private-note"));assertFalse(result.contains(parent.id));assertFalse(result.contains("Parent private"))
    }
    @Test fun completedRecurringOccurrenceRemainsCompletedWhenMovedByATool()=runBlocking {
        val task=useCases.create(OrganizerKind.TASK,"Recurring",time=OrganizerTime(LocalDateTime.parse("2026-11-03T09:00"),zoneId="UTC"),repeat=RepeatRule(RepeatFrequency.DAILY,count=3))
        val at=task.time!!.instant(ZoneOffset.UTC)
        val completed=useCases.completeTask(task.id,task.version,at)
        json(call("halo_organizer_occurrence",mapOf("id" to task.id,"version" to completed.version,"originalStart" to at.toString(),"action" to "REPLACE",
            "time" to mapOf("start" to "2026-11-04T11:00","zoneId" to "UTC"))))
        val override=organizer.overrides(task.id).single()
        assertEquals(OccurrenceDisposition.COMPLETED,override.disposition)
        assertEquals(LocalDateTime.parse("2026-11-04T11:00"),override.replacementTime!!.start)
    }
    @Test fun aReadStartingDuringAnUncommittedWalGrantChangeCannotInheritOldDisclosureAuthority()=runBlocking {withTimeout(10000) {
        val record=useCases.create(OrganizerKind.TASK,"Revoked WAL secret")
        briefs.saveSettings(BriefSettings(childRecordIds=setOf(record.id)),0);child=true
        val invalidated=CompletableDeferred<Unit>();val commit=CompletableDeferred<Unit>();val admitted=CompletableDeferred<Unit>()
        val writer=async(Dispatchers.IO) {db.withTransaction {briefs.saveSettings(BriefSettings(),1);invalidated.complete(Unit);commit.await()}}
        invalidated.await()
        val reader=executorFor(onAdmission={admitted.complete(Unit)})
        val request=async {reader.execute(ToolCall("wal","halo_organizer_get",mapOf("id" to record.id)),ToolExecutionContext())}
        admitted.await()
        try {assertNull(withTimeoutOrNull(100) {request.await()})} finally {commit.complete(Unit)}
        writer.await()
        val result=request.await()
        assertTrue(result is ToolExecutionResult.Rejected);assertFalse(result.toString().contains(record.title));assertTrue(child)
    }}
    @Test fun aHeldOrganizerReadCannotReturnAfterItsGrantIsRevokedWithinTheSameKidsSession()=runBlocking {withTimeout(10000) {
        val record=useCases.create(OrganizerKind.TASK,"Temporarily shared secret")
        briefs.saveSettings(BriefSettings(childRecordIds=setOf(record.id)),0);child=true
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val held=object: OrganizerRepository by organizer {
            override suspend fun overrides(id: String): List<OccurrenceOverride> {entered.complete(Unit);release.await();return organizer.overrides(id)}
        }
        val reader=executorFor(OrganizerUseCases(held,clock,{ZoneOffset.UTC},{1000},{"boot"}))
        val request=async {reader.execute(ToolCall("held","halo_organizer_get",mapOf("id" to record.id)),ToolExecutionContext())}
        entered.await();briefs.saveSettings(BriefSettings(),1);release.complete(Unit)
        val result=request.await()
        assertTrue(result is ToolExecutionResult.Rejected);assertFalse(result.toString().contains(record.title));assertTrue(child)
    }}
    @Test fun aHeldSourceReadCannotDiscloseAnApprovalRevokedWithinTheSameKidsSession()=runBlocking {withTimeout(10000) {
        val source=sources.save(StarterSourceCatalogue.external().first().copy(title="Temporarily approved source",selected=true,childApproved=true),null)
        child=true
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val held=object: CollectorRepository by sources {
            override suspend fun state(id: String): CollectorState? {val value=sources.state(id);entered.complete(Unit);release.await();return value}
        }
        val reader=executorFor(sourceReader=held)
        val request=async {reader.execute(ToolCall("held","halo_source_get",mapOf("id" to source.id,"version" to source.version)),ToolExecutionContext())}
        entered.await();sources.save(source.copy(childApproved=false),source.version);release.complete(Unit)
        val result=request.await()
        assertTrue(result is ToolExecutionResult.Rejected);assertFalse(result.toString().contains(source.title));assertTrue(child)
    }}
    @Test fun childrenCannotReadAdultLauncherConfiguration()=runBlocking {
        child=true
        assertTrue(call("halo_widget_query") is ToolExecutionResult.Rejected)
    }
    @Test fun deterministicBriefBuildDoesNotFetchAndNavigationReportsDispatchOnly()=runBlocking {
        val value=json(call("halo_brief_action",mapOf("action" to "BUILD","period" to "ON_DEMAND")))
        assertTrue(value.getBoolean("saved"));assertFalse(value.getBoolean("fetched"));assertEquals(0,networkCalls)
        val result=json(call("halo_everyday_open",mapOf("instrument" to "BRIEF","id" to value.getString("id"))))
        assertTrue(result.getBoolean("dispatched"));assertFalse(result.getBoolean("viewed"));assertTrue(opened is EverydayTarget.Brief)
    }
    @Test fun malformedNestedInputAndMissingWriteInteractionCannotReachAnOwner()=runBlocking {
        assertTrue(call("halo_organizer_create",mapOf("record" to mapOf("kind" to "TASK","title" to "No","execute" to "command"))) is ToolExecutionResult.Rejected)
        assertTrue(call("halo_organizer_create",mapOf("record" to mapOf("kind" to "TASK","title" to "No")),interaction=null) is ToolExecutionResult.Rejected)
        assertTrue(organizer.records().isEmpty())
    }
    @Test fun everyDefinitionHasCentralCapabilitiesNeverReplayWritesAndPersonalDisclosureClassification() {
        assertEquals(EverydayToolContracts.names,PersonalConfigurationToolAccess.everyday)
        EverydayToolContracts.definitions.forEach {definition ->
            val descriptor=HaloToolDescriptors.assistant(definition)
            assertNotNull(definition.name,descriptor)
            assertNotNull(definition.name,descriptor!!.operability)
            assertTrue(definition.name,definition.name in WorkspaceToolAccess.readTools)
            if(definition.name !in EverydayToolContracts.queries) assertEquals(ReplaySafety.NEVER_REPLAY,descriptor.security.replaySafety)
            else assertEquals(ConfirmationPolicy.NONE,descriptor.security.confirmation)
        }
    }
}
