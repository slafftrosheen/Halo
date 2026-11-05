package com.halo.app.di.tools

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.*
import com.halo.app.data.workspace.*
import com.halo.app.data.notebook.*
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
    private val fileRoot=java.io.File(app.cacheDir,"c-${java.util.UUID.randomUUID().toString().take(8)}").also {it.mkdirs()}
    private val files=RoomWorkspaceFileRepository(app,db,fileRoot,ownedUri={file ->
        val uri=android.net.Uri.parse("content://calendar-tools/${file.name}")
        org.robolectric.Shadows.shadowOf(app.contentResolver).registerInputStreamSupplier(uri) {file.inputStream()}
        uri
    })
    private val notes=RoomNotebookRepository(db.notebookDao(),documents=NotebookDocumentRepository(db.notebookDocumentDao(),db))
    private val calendars=OrganizerCalendarService(db,useCases,files,RoomWorkspaceRepository(db.notebookDao()),notes,app.contentResolver,clock)
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
    private val briefService=EverydayBriefService(briefs,sources,organizer,{com.halo.app.daily.DailyAssistantObservation()},
        {if(child) BriefAudience.KIDS else BriefAudience.EVERYDAY},{emptySet()},{true},clock,{ZoneOffset.UTC})
    private var afterDismiss: suspend ()->Unit = {}
    private val dismissed=mutableListOf<Pair<String,Long>>()
    private val scheduled=mutableListOf<PlannedDelivery>()
    private val delivery=OrganizerDeliveryCoordinator(organizer,object: OrganizerDeliveryPort {
        override suspend fun resetSchedules()=Unit
        override suspend fun cancelSchedule(recordId: String)=Unit
        override suspend fun schedule(record: OrganizerRecord,delivery: PlannedDelivery): DeliveryOutcome {
            scheduled+=delivery;return DeliveryOutcome(DeliveryState.EXACT)
        }
        override suspend fun present(record: OrganizerRecord,receipt: DeliveryReceipt,recovered: Boolean)=DeliveryOutcome(DeliveryState.FIRED)
        override suspend fun dismiss(occurrenceKey: String,recordVersion: Long) {dismissed+=occurrenceKey to recordVersion;afterDismiss()}
    },clock,{ZoneOffset.UTC},{1000},{"boot"})
    private var opened: EverydayTarget?=null
    private fun executorFor(records: OrganizerUseCases=useCases,sourceReader: CollectorRepository=sources,onAdmission: ()->Unit = {})=EverydayToolExecutor(records,RoomWorldClockRepository(db,clock),OfflineCityCatalogue {error("Not needed")},sourceReader,
        CollectorCoordinator(sources,registry,policy,clock),CollectorPreview(sources,registry,policy,clock),briefs,briefService,{}, {},
        {id,version -> sources.clearSnapshot(id,version)}, {_,_ -> JSONObject()}, {opened=it;true}, {allowed},{child},
        {val family=child;onAdmission();{check(allowed && child==family)}},{1000},{"boot"},delivery,calendars,clock,readState={EverydayDisclosureSnapshot.capture(db)})
    private val executor=executorFor()
    @After fun close() {db.close();app.deleteDatabase(dbName);fileRoot.deleteRecursively()}
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
    @Test fun assistantSnoozeAndRecurringDismissCancelTheExactPostedVersionWithoutReplay()=runBlocking {
        for (action in listOf("SNOOZE","DISMISS")) {
            val record=useCases.create(OrganizerKind.ALARM,"Ring",time=OrganizerTime(LocalDateTime.ofInstant(now,ZoneOffset.UTC),zoneId="UTC"),
                repeat=RepeatRule(RepeatFrequency.DAILY,count=3))
            delivery.reconcile(record.id)
            val receipts=organizer.receipts(record.id)
            // Reconciliation delivers the due occurrence and schedules only the
            // next one. The third recurrence is enabled but not yet scheduled.
            assertEquals(2,receipts.size)
            val receipt=receipts.single {it.triggerAt==now}
            val future=receipts.filter {it.triggerAt>now}
            assertEquals(1,future.size)
            assertEquals(DeliveryState.FIRED,receipt.state)
            assertFalse(delivery.fire(receipt.occurrenceKey,record.version,receipt.triggerAt))
            val args=mapOf("id" to record.id,"version" to record.version,"occurrenceKey" to receipt.occurrenceKey,"action" to action,"minutes" to 5)
            val result=call("halo_organizer_delivery",args)
            assertTrue(result is ToolExecutionResult.Success)
            assertTrue(receipt.occurrenceKey to record.version in dismissed)
            val count=scheduled.size
            assertTrue(call("halo_organizer_delivery",args) is ToolExecutionResult.Rejected)
            assertEquals(count,scheduled.size)
            assertEquals(if(action=="SNOOZE") DeliveryState.EXACT else DeliveryState.DISMISSED,
                organizer.receipts(record.id).first {it.occurrenceKey==receipt.occurrenceKey}.state)
            assertEquals(future,organizer.receipts(record.id).filter {it.occurrenceKey in future.map {row->row.occurrenceKey}})
            val saved=organizer.get(record.id)!!
            assertEquals(OrganizerStatus.ACTIVE,saved.status)
            assertEquals(2,OrganizerRecurrence.between(saved,emptyList(),now.plusSeconds(86400),now.plusSeconds(3L*86400),ZoneOffset.UTC).size)
        }
    }
    @Test fun recognizedAlarmAndTimerOperationsSaveCanonicalRecordsWithoutAndroidClockDispatch()=runBlocking {
        val result=json(call("halo_organizer_alarm",mapOf("timeExpression" to "7 AM","label" to "Wake")))
        val alarm=organizer.get(result.getJSONObject("record").getString("id"))!!
        assertEquals(OrganizerKind.ALARM,alarm.kind)
        assertEquals(LocalTime.of(7,0),alarm.time!!.start.toLocalTime())
        assertTrue(alarm.time!!.instant(ZoneId.systemDefault())>now)
        assertFalse(result.getBoolean("deliveryConfirmed"))
        assertTrue(call("halo_organizer_alarm",mapOf("timeExpression" to "morning")) is ToolExecutionResult.Rejected)
        val timer=json(call("halo_organizer_create",mapOf("record" to mapOf("kind" to "TIMER","title" to "Timer","timerDurationMs" to 120000))))
        assertEquals(120000L,organizer.get(timer.getJSONObject("record").getString("id"))!!.timer!!.durationMs)
        assertEquals(2,organizer.records().size)
        val clarified=json(call("halo_organizer_alarm",mapOf("timeExpression" to "tomorrow morning",
            "resolvedTime" to mapOf("type" to "CLOCK","time" to "07:15:30","dayRelation" to "TOMORROW"))))
        val exact=organizer.get(clarified.getString("id"))!!.time!!
        assertEquals(LocalTime.of(7,15,30),exact.start.toLocalTime())
        assertEquals(now.atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1),exact.start.toLocalDate())
    }
    @Test fun typedAndSpeechFinalNativeRequestsUseTheRegisteredExecutorAndCanonicalOrganizer()=runBlocking {
        val registry=ToolRegistry(listOf(executor),EverydayToolContracts.definitions.map {HaloToolDescriptors.assistant(it)!!})
        for(source in listOf(com.halo.sdk.runtime.Source.USER,com.halo.sdk.runtime.Source.MICROPHONE)) {
            val intent=com.halo.cognition.language.NativeCommandParser.parse("Set a timer for two minutes")!!
            val request=com.halo.runtime.cognition.native.OrganizerNativeRequests.requestFor(
                com.halo.runtime.cognition.blackboard.Interpretation("Set a timer for two minutes",intent=intent,confidence=1f,source=source))!!
            val result=json(registry.executeAuthorized(ToolAuthorization.Authorized(ToolCall(request.interactionId,request.operation,request.arguments)),
                ToolExecutionContext(interactionId=request.interactionId)))
            val saved=organizer.get(result.getString("id"))!!
            assertEquals(OrganizerKind.TIMER,saved.kind);assertEquals(120000L,saved.timer!!.durationMs)
            assertEquals(saved.id,json(call("halo_organizer_get",mapOf("id" to saved.id))).getString("id"))
            val alarm=com.halo.runtime.cognition.native.OrganizerNativeRequests.requestFor(
                com.halo.runtime.cognition.blackboard.Interpretation("Wake me tomorrow morning",intent=com.halo.cognition.language.SetAlarmIntent,confidence=1f,source=source,
                    entities=listOf(com.halo.cognition.language.TimeEntity(com.halo.cognition.language.TemporalValue.TimeOfDay(LocalTime.of(7,15),com.halo.cognition.language.DayRelation.TOMORROW),"7:15",0.99f))))!!
            val alarmResult=json(registry.executeAuthorized(ToolAuthorization.Authorized(ToolCall(alarm.interactionId,alarm.operation,alarm.arguments)),ToolExecutionContext(interactionId=alarm.interactionId)))
            val alarmRecord=organizer.get(alarmResult.getString("id"))!!
            assertEquals(OrganizerKind.ALARM,alarmRecord.kind);assertEquals(LocalTime.of(7,15),alarmRecord.time!!.start.toLocalTime())
        }
        assertEquals(4,organizer.records().size)
    }
    @Test fun calendarToolsReviewImportAndExportExactLocalCopiesWithAuthorityAndStaleGuards()=runBlocking {
        val note=notes.createNote(title="Calendar source")
        val raw="BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:one\r\nDTSTART:20261104T090000Z\r\nDTEND:20261104T100000Z\r\nSUMMARY:Local appointment\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n".toByteArray()
        val file=files.createManagedFile("source.ics","text/calendar",raw,note.id,note.revision.toString(),"{}")
        val args=mapOf("objectId" to file.objectId.value,"fileVersion" to file.version!!,"zoneId" to "UTC")
        val reviewed=json(call("halo_organizer_calendar_preview",args,interaction=null))
        val id=reviewed.getJSONArray("records").getJSONObject(0).getString("id")
        val apply=args+mapOf("reviewToken" to reviewed.getString("reviewToken"),"recordIds" to listOf(id))
        assertTrue(call("halo_organizer_calendar_import",apply,interaction=null) is ToolExecutionResult.Rejected)
        val imported=json(call("halo_organizer_calendar_import",apply))
        assertEquals(id,imported.getJSONArray("added").getString(0))
        assertTrue(call("halo_organizer_calendar_import",apply) is ToolExecutionResult.Rejected)
        val exported=json(call("halo_organizer_calendar_export",mapOf("records" to listOf(mapOf("id" to id,"version" to 1)))))
        assertFalse(exported.getBoolean("exportedExternally"))
        val local=files.getFile(WorkspaceObjectId(exported.getString("objectId")))!!
        val saved=java.io.File(db.workspaceFileDao().getFile(local.objectId.value)!!.ownedCopyPath!!).readText()
        assertTrue(saved.contains("SUMMARY:Local appointment"));assertEquals(exported.getString("fileVersion"),local.version)
        assertTrue(call("halo_organizer_calendar_export",mapOf("records" to listOf(mapOf("id" to id,"version" to 99)))) is ToolExecutionResult.Rejected)
        child=true
        assertTrue(call("halo_organizer_calendar_preview",args) is ToolExecutionResult.Rejected)
        child=false
        val rechecked=json(call("halo_organizer_calendar_preview",args))
        org.robolectric.Shadows.shadowOf(app.contentResolver).registerInputStreamSupplier(file.uri) {
            allowed=false
            java.io.ByteArrayInputStream(raw)
        }
        val revoked=call("halo_organizer_calendar_import",args+mapOf("reviewToken" to rechecked.getString("reviewToken"),"recordIds" to listOf(id)))
        assertFalse(revoked is ToolExecutionResult.Success)
        assertEquals(1,organizer.records().size)
    }
    @Test fun deliveryFailureAfterSnoozeCommitAndPartialTimerBatchRemainUnknown()=runBlocking {
        val record=useCases.create(OrganizerKind.ALARM,"Ring",time=OrganizerTime(LocalDateTime.ofInstant(now,ZoneOffset.UTC),zoneId="UTC"))
        delivery.reconcile(record.id)
        val receipt=organizer.receipts(record.id).single()
        delivery.fire(receipt.occurrenceKey,record.version,receipt.triggerAt)
        afterDismiss={throw IllegalArgumentException("Port failure after commit")}
        val result=call("halo_organizer_delivery",mapOf("id" to record.id,"version" to record.version,"occurrenceKey" to receipt.occurrenceKey,"action" to "SNOOZE","minutes" to 5))
        assertTrue(result is ToolExecutionResult.Failed && result.completionUnknown)
        assertEquals(record.version+1,organizer.get(record.id)!!.version)
        afterDismiss={}
        for(n in 1..2) {
            val timer=organizer.save(OrganizerRecord("timer$n",OrganizerKind.TIMER,"Timer $n",createdAt=now,
                timer=TimerState(1000,1000,now.minusSeconds(1),0,"boot")),null)
            val key="${timer.id}@timer:${timer.timer!!.runId}"
            organizer.putReceipt(DeliveryReceipt(key,timer.id,timer.version,now,DeliveryState.FIRED,now))
        }
        var changed=false
        afterDismiss={if(!changed) {
            changed=true
            val other=organizer.records().first {it.kind==OrganizerKind.TIMER && it.status==OrganizerStatus.ACTIVE}
            useCases.cancel(other.id,other.version)
        }}
        val partial=call("halo_organizer_clock_action",mapOf("action" to "DISMISS_EXPIRED_TIMERS"))
        assertTrue(partial is ToolExecutionResult.Failed && partial.completionUnknown)
        assertEquals(1,organizer.records().count {it.kind==OrganizerKind.TIMER && it.status==OrganizerStatus.COMPLETED})
        assertEquals(1,organizer.records().count {it.kind==OrganizerKind.TIMER && it.status==OrganizerStatus.CANCELLED})
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
    @Test fun aHeldSourceReadCannotDiscloseAnApprovalRevokedWithinTheSameKidsSession()=runBlocking {
        // Cold Room schema setup is not part of the held-read race deadline.
        val source=sources.save(StarterSourceCatalogue.external().first().copy(title="Temporarily approved source",selected=true,childApproved=true),null)
        withTimeout(10000) {
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
        }
    }
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
        assertEquals(EverydayToolContracts.names + ReferenceLibraryContracts.names + MaterialImageContracts.names,PersonalConfigurationToolAccess.everyday)
        EverydayToolContracts.definitions.forEach {definition ->
            val descriptor=HaloToolDescriptors.assistant(definition)
            assertNotNull(definition.name,descriptor)
            assertNotNull(definition.name,descriptor!!.operability)
            assertTrue(definition.name,definition.name in WorkspaceToolAccess.readTools)
            if(definition.name !in EverydayToolContracts.queries) assertEquals(ReplaySafety.NEVER_REPLAY,descriptor.security.replaySafety)
            else assertEquals(ConfirmationPolicy.NONE,descriptor.security.confirmation)
            val contract=descriptor.operability!!
            assertEquals(false,contract.resultSchema["additionalProperties"])
            assertTrue((contract.resultSchema["properties"] as Map<*,*>).isNotEmpty())
            assertTrue(definition.name,runCatching {EverydayResultContracts.validate(definition.name,JSONObject())}.isFailure)
        }
        val descriptors=EverydayToolContracts.definitions.map {HaloToolDescriptors.assistant(it)!!}
        val registry=ToolRegistry(listOf(executor),descriptors)
        // This fixture owns the original executor; the new operation owner has its own registered workflow fixture.
        assertTrue(registry.operabilityReport(listOf(ToolFeatureOperability("everyday-core",EverydayToolContracts.coreDefinitions.map {it.name}.toSet()))).isEmpty())
        assertEquals(ToolOperation.CREATE,EverydayToolContracts.operability(EverydayToolContracts.definitions.single {it.name=="halo_organizer_alarm"})!!.operation)
        assertEquals(ToolOperation.EXPORT,EverydayToolContracts.operability(EverydayToolContracts.definitions.single {it.name=="halo_organizer_calendar_export"})!!.operation)
        assertEquals(setOf("objectId"),EverydayToolContracts.operability(EverydayToolContracts.definitions.single {it.name=="halo_organizer_calendar_export"})!!.resultIdFields)
    }
    @Test fun registeredCalendarPreviewCanContinueWithOneExactFileSnapshot() = runBlocking {
        val note = notes.createNote(title = "Calendar paging fixture")
        val raw = "BEGIN:VCALENDAR\r\n" + (1..7).joinToString("") { index ->
            "BEGIN:VEVENT\r\nUID:owned-$index\r\nDTSTART:20261104T090000Z\r\nSUMMARY:Event $index\r\nEND:VEVENT\r\n"
        } + "END:VCALENDAR\r\n"
        val file = files.createManagedFile("pages.ics", "text/calendar", raw.toByteArray(), note.id, note.revision.toString(), "{}")
        val args = mapOf("objectId" to file.objectId.value, "fileVersion" to file.version!!, "zoneId" to "UTC")
        val registry = ToolRegistry(listOf(executor), executor.definitions().map { HaloToolDescriptors.assistant(it)!! })
        suspend fun read(a: Map<String, Any>) = json(registry.executeAuthorized(ToolAuthorization.Authorized(ToolCall("preview", "halo_organizer_calendar_preview", a)), ToolExecutionContext()))
        val first = read(args)
        assertEquals(5, first.getJSONArray("records").length())
        val second = read(args + mapOf("offset" to first.getInt("nextOffset"), "snapshot" to first.getString("snapshot")))
        assertEquals(2, second.getJSONArray("records").length()); assertTrue(second.isNull("nextOffset"))
        assertEquals(first.getString("reviewToken"), second.getString("reviewToken")); assertEquals(first.getString("snapshot"), second.getString("snapshot"))
    }
    @Test fun registeredReadbackReassemblesEscapedDetailsAndRejectsChangesBetweenChunks() = runBlocking {
        val text = ("quotes\"\\\n😀 ".repeat(1300))
        val record = useCases.create(OrganizerKind.TASK, "Long owned note", details = text)
        val registry = ToolRegistry(listOf(executor), executor.definitions().map { HaloToolDescriptors.assistant(it)!! })
        suspend fun read(args: Map<String, Any>): ToolExecutionResult = registry.executeAuthorized(
            ToolAuthorization.Authorized(ToolCall("read", "halo_organizer_get", args)), ToolExecutionContext())
        val first = json(read(mapOf("id" to record.id)))
        assertTrue(first.toString().length <= 4000)
        val snapshot = first.getString("snapshot")
        val output = StringBuilder(); var offset = 0
        do {
            val page = json(read(mapOf("id" to record.id, "version" to record.version, "snapshot" to snapshot,
                "view" to "text", "path" to listOf("record", "details"), "readOffset" to offset)))
            assertTrue(page.toString().length <= 4000); output.append(page.getString("text"))
            offset = if (page.isNull("nextReadOffset")) -1 else page.getInt("nextReadOffset")
        } while (offset >= 0)
        assertEquals(text, output.toString())
        organizer.save(record.copy(title = "Changed"), record.version)
        assertTrue(read(mapOf("id" to record.id, "snapshot" to snapshot)) is ToolExecutionResult.Rejected)
    }
    @Test fun actualQueryResultIsClosedAndBoundedAndInvalidPostWriteReceiptRemainsUnknown()=runBlocking {
        useCases.create(OrganizerKind.TASK,"Observed")
        val query=json(call("halo_organizer_query"))
        EverydayResultContracts.validate("halo_organizer_query",query)
        val rows=query.getJSONArray("records")
        val tooMany=org.json.JSONArray((1..51).map {rows.getJSONObject(0)})
        assertTrue(runCatching {EverydayResultContracts.validate("halo_organizer_query",JSONObject(query.toString()).put("records",tooMany))}.isFailure)
        assertTrue(runCatching {EverydayResultContracts.validate("halo_organizer_query",JSONObject(query.toString()).put("unexpected","data"))}.isFailure)
        val broken=object: OrganizerRepository by organizer {
            override suspend fun save(record: OrganizerRecord,expectedVersion: Long?,resetOccurrences: Boolean): OrganizerRecord {
                val saved=organizer.save(record,expectedVersion,resetOccurrences)
                return saved.copy(title="x".repeat(201))
            }
        }
        val writer=executorFor(OrganizerUseCases(broken,clock,{ZoneOffset.UTC},{1000},{"boot"}))
        val result=writer.execute(ToolCall("unknown","halo_organizer_create",mapOf("record" to mapOf("kind" to "TASK","title" to "Written once"))),ToolExecutionContext(interactionId="unknown"))
        assertTrue(result is ToolExecutionResult.Failed && result.completionUnknown)
        assertEquals(1,organizer.records().count {it.title=="Written once"})
    }
    @Test fun realSourceFamiliesClockEditsAndBriefReadReceiptsMatchRegisteredResults(): Unit =runBlocking {
        val cases=listOf(
            CollectorKind.FEED to CollectorPayload.Articles(listOf(ArticleFact("a","News","Text",null,"Publisher",null,"en"))),
            CollectorKind.ALERTS to CollectorPayload.Warnings(listOf(WarningFact("w","Warning","Detail",null,null,null,null,null,"Agency"))),
            CollectorKind.WEATHER to CollectorPayload.Weather(WeatherFact(StarterSourceCatalogue.riga,null,null,null,listOf(WeatherDay(LocalDate.of(2026,11,3),null,null,null,null)))),
            CollectorKind.LOCAL_DEVICE to CollectorPayload.Local(listOf(LocalFact("local","DEVICE","Battery","Ready","halo://day",null,null))))
        for((kind,payload) in cases) {
            val external=kind!=CollectorKind.LOCAL_DEVICE
            val source=sources.save(CollectorSource("fixture:${kind.name}",kind.name,"Publisher",kind,SourceCategory.CUSTOM,"en",
                endpoint=if(kind==CollectorKind.WEATHER) OpenMeteoParser.endpoint(StarterSourceCatalogue.riga) else if(external) "https://example.org/${kind.name.lowercase()}" else null,selected=true,
                place=if(kind==CollectorKind.WEATHER) StarterSourceCatalogue.riga else null),null)
            val reservation=(sources.reserve(source.id,source.version,now,true) as CollectorAdmission.Accepted).reservation
            assertTrue(sources.finish(reservation,CollectorSnapshot(source.id,source.version,payload,"revision",now,now,now.plusSeconds(3600)),
                CollectorAttempt(source.id,now,CollectorAttemptState.CURRENT)))
            val value=json(call("halo_source_get",mapOf("id" to source.id,"version" to source.version)))
            assertEquals(source.id,value.getString("id"));assertFalse(value.isNull("content"))
        }
        val saved=json(call("halo_clock_save",mapOf("label" to "Riga","zoneId" to "Europe/Riga")))
        val id=saved.getString("id")
        assertEquals(1,json(call("halo_clock_query")).getJSONArray("clocks").length())
        json(call("halo_clock_action",mapOf("id" to id,"version" to 1,"action" to "REMOVE")))
        val restored=json(call("halo_clock_action",mapOf("id" to id,"version" to 2,"action" to "RESTORE")))
        json(call("halo_clock_action",mapOf("action" to "REORDER","clocks" to listOf(mapOf("id" to id,"version" to restored.getJSONObject("clock").getLong("version"))))))
        val brief=json(call("halo_brief_action",mapOf("action" to "BUILD")))
        json(call("halo_brief_query",mapOf("id" to brief.getString("id"))))
        json(call("halo_brief_query"))
        val reading=json(call("halo_brief_action",mapOf("action" to "READ","id" to brief.getString("id"))))
        assertTrue(reading.getBoolean("playbackRequested"));assertFalse(reading.getBoolean("audible"));assertFalse(reading.getBoolean("completed"))
        json(call("halo_brief_action",mapOf("action" to "STOP")))
    }
}
