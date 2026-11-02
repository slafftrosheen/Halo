package com.halo.app.backup

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.everyday.*
import com.halo.app.everyday.EverydayWriteAuthority
import com.halo.app.everyday.organizer.*
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.briefing.*
import com.halo.app.screen.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33], application=Application::class)
class EverydayArchiveTest {
    private val app=ApplicationProvider.getApplicationContext<Application>()
    private val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
    private val now=Instant.ofEpochMilli(System.currentTimeMillis())
    private val organizer=RoomOrganizerRepository(db)
    private val collectors=RoomCollectorRepository(db)
    private val briefs=RoomBriefRepository(db)
    @After fun close() {db.close();app.getSharedPreferences("halo_screen",0).edit().clear().commit()}
    private fun task(id: String,title: String=id,parent: String?=null)=OrganizerRecord(id,OrganizerKind.TASK,title,parentId=parent,createdAt=now)

    @Test fun roundTripPreservesFactsButRevokesLiveDeliveryExternalRefreshAndChildGrants()=runBlocking {
        val record=organizer.save(task("plan"),null)
        val timer=organizer.save(OrganizerRecord("timer",OrganizerKind.TIMER,"Tea",timer=TimerState(600000,600000).start(now,50,"old-boot"),createdAt=now),null)
        db.organizerDao().putReceipt(OrganizerCodec.entity(DeliveryReceipt("plan@1",record.id,record.version,now,DeliveryState.EXACT,now)))
        val source=collectors.save(StarterSourceCatalogue.external().first().copy(selected=true,childApproved=true),null)
        val claim=(collectors.reserve(source.id,source.version,now,true) as CollectorAdmission.Accepted).reservation
        val snapshot=CollectorSnapshot(source.id,source.version,CollectorPayload.Articles(listOf(ArticleFact("one","Real saved title","Saved body","https://example.org/one","Publisher",now,"en"))),"r1",now,now,now.plusSeconds(3600))
        collectors.finish(claim,snapshot,CollectorAttempt(source.id,now,CollectorAttemptState.CURRENT))
        briefs.saveSettings(BriefSettings(proactive=true,childRecordIds=setOf(record.id)),0)
        val archive=EverydayArchive.snapshot(db)
        db.withTransaction {EverydayArchive.restore(db,archive,BrainImportMode.REPLACE,emptyMap(),emptyMap())}
        assertTrue(organizer.get(record.id)!!.version>record.version)
        assertTrue(organizer.receipts(record.id).isEmpty())
        val restored=organizer.get(timer.id)!!.timer!!
        assertNull(restored.startedAt);assertNull(restored.bootId);assertTrue(restored.remainingMs in 1..600000)
        val saved=collectors.state(source.id)!!
        assertFalse(saved.source.enabled);assertFalse(saved.source.childApproved);assertTrue(saved.source.selected)
        assertEquals(now,saved.snapshot!!.retrievedAt);assertEquals("r1",saved.snapshot!!.contentRevision)
        assertEquals(saved.source.version,saved.snapshot!!.sourceVersion)
        assertEquals(CollectorAttemptState.NEVER,saved.attempt.state)
        assertFalse(briefs.settings().proactive);assertTrue(briefs.settings().childRecordIds.isEmpty())
    }

    @Test fun mergeKeepsLocalByDefaultAndDuplicatesPreserveParentAndRelatedRecordIdentity()=runBlocking {
        val parent=organizer.save(task("parent","Backup parent"),null)
        organizer.save(task("child","Backup child",parent.id).copy(links=listOf(OrganizerLink("organizer:${parent.id}"))),null)
        val archive=EverydayArchive.snapshot(db)
        organizer.save(parent.copy(title="Local parent"),parent.version)
        db.withTransaction {EverydayArchive.restore(db,archive,BrainImportMode.MERGE,emptyMap(),emptyMap())}
        assertEquals("Local parent",organizer.get(parent.id)!!.title)
        val policies=archive.filter {it.kind=="ORGANIZER"}.associate {EverydayArchive.key(it) to ImportConflictPolicy.DUPLICATE}
        val map=db.withTransaction {EverydayArchive.restore(db,archive,BrainImportMode.MERGE,policies,emptyMap())}
        val copy=organizer.get(map.getValue("everyday:ORGANIZER:child"))!!
        val parentCopy=map.getValue("everyday:ORGANIZER:parent")
        assertEquals(parentCopy,copy.parentId);assertEquals("organizer:$parentCopy",copy.links.single().objectId)
        assertEquals("Local parent",organizer.get(parent.id)!!.title);assertEquals(4,organizer.records().size)
    }

    @Test fun invalidMergedParentRollsBackRecordsOutboxAndChangeHistory()=runBlocking {
        val parent=organizer.save(task("parent"),null)
        organizer.save(task("child",parent="parent"),null)
        val archive=EverydayArchive.snapshot(db)
        db.withTransaction {EverydayArchive.clear(db)}
        val existing=organizer.save(parent.copy(status=OrganizerStatus.COMPLETED),null)
        val beforeChanges=db.organizerDao().changes(0)
        assertTrue(runCatching {db.withTransaction {EverydayArchive.restore(db,archive,BrainImportMode.MERGE,emptyMap(),emptyMap())}}.isFailure)
        assertEquals(existing,organizer.get("parent"));assertNull(organizer.get("child"))
        assertEquals(beforeChanges,db.organizerDao().changes(0));assertEquals(listOf("parent"),organizer.pendingScheduleIds())
    }

    @Test fun malformedSnapshotKindIsRejectedBeforeAnyReplaceClearsData()=runBlocking {
        organizer.save(task("keep"),null)
        val s=StarterSourceCatalogue.external().first()
        val invalid=JSONObject().put("source",CollectorCodec.encode(s)).put("snapshot",JSONObject()
            .put("revision","x").put("retrievedAt",1).put("verifiedAt",1).put("expiresAt",2)
            .put("payload",CollectorCodec.encode(CollectorPayload.Local(emptyList()))))
        assertTrue(runCatching {db.withTransaction {EverydayArchive.restore(db,listOf(EverydayArchiveEntry("SOURCE",s.id,invalid.toString())),BrainImportMode.REPLACE,emptyMap(),emptyMap())}}.isFailure)
        assertNotNull(organizer.get("keep"))
    }

    @Test fun replaceRevokesClockAndBriefSettingsVersionsThatWereAlreadyOpenLocally()=runBlocking {
        val clocks=RoomWorldClockRepository(db)
        val initial=clocks.save(WorldClockRecord("clock","Riga","Europe/Riga"),null)
        briefs.saveSettings(BriefSettings(headlineLimit=2),0)
        val archive=EverydayArchive.snapshot(db)
        val local=clocks.save(initial.copy(label="Locally edited"),initial.version)
        val settings=briefs.saveSettings(briefs.settings().copy(headlineLimit=5),1)
        val deleted=clocks.remove(local.id,local.version)
        db.withTransaction {EverydayArchive.restore(db,archive,BrainImportMode.REPLACE,emptyMap(),emptyMap())}
        assertTrue(clocks.get(local.id)!!.version>deleted.version)
        assertTrue(briefs.settings().version>settings.version)
        assertTrue(runCatching {clocks.save(local.copy(label="Stale edit"),local.version)}.isFailure)
        assertTrue(runCatching {briefs.saveSettings(settings,settings.version)}.isFailure)
    }

    @Test fun restoringAnOldRevisionCancellationDoesNotCancelTheNewerActiveSeries()=runBlocking {
        val original=organizer.save(OrganizerRecord("alarm",OrganizerKind.ALARM,"Wake",time=OrganizerTime(LocalDateTime.ofInstant(now.plusSeconds(3600),ZoneOffset.UTC),zoneId="UTC"),repeat=RepeatRule(RepeatFrequency.DAILY,count=3),createdAt=now),null)
        val at=original.time!!.instant(ZoneOffset.UTC)
        db.organizerDao().putReceipt(OrganizerCodec.entity(DeliveryReceipt("alarm@${at.toEpochMilli()}",original.id,original.version,at,DeliveryState.CANCELLED,now)))
        organizer.save(original.copy(title="Reopened alarm"),original.version)
        val archive=EverydayArchive.snapshot(db)
        db.withTransaction {EverydayArchive.restore(db,archive,BrainImportMode.REPLACE,emptyMap(),emptyMap())}
        assertTrue(organizer.receipts(original.id).isEmpty())
        val restored=organizer.get(original.id)!!
        assertNotNull(OrganizerSchedulePlanner.next(restored,emptyList(),emptyList(),now,ZoneOffset.UTC,0,"new-boot"))
    }

    @Test fun screenRoundTripKeepsIndependentCitiesAndRemapsDomainSelectionsWithoutRestoringExposure() {
        val store=LocalScreenStore(app)
        store.saveLayout(ScreenLayout("day","Day",listOf(
            ScreenCard("clock",null,CardSize.COMPACT,false,mapOf("zoneId" to "Asia/Tokyo","cityLabel" to "Tokyo"),presentationId="tokyo"),
            ScreenCard("clock",null,CardSize.COMPACT,false,mapOf("zoneId" to "Europe/Riga","cityLabel" to "Riga"),presentationId="riga"),
            ScreenCard("organizer_tasks",null,CardSize.STANDARD,false,mapOf("recordId" to "old"),presentationId="task")),1))
        val raw=store.remapPortable(store.portableSnapshot(),mapOf("everyday:ORGANIZER:old" to "new"))
        store.clearPortable();store.restorePortable(raw)
        val restored=LocalScreenStore(app)
        assertEquals(listOf("Asia/Tokyo","Europe/Riga"),restored.layout.value!!.cards.take(2).map {it.config["zoneId"]})
        assertEquals("new",restored.layout.value!!.cards.last().config["recordId"])
        assertEquals(3,restored.layout.value!!.cards.map {it.presentationId}.distinct().size)
        assertTrue(restored.hidePrivate.value);assertFalse(restored.keepCharging.value);assertFalse(restored.hasUndo())
    }

    @Test fun aSuspendedActionCannotRegainItsOldAuthorityAfterRestoreCompletes()=runBlocking {
        val admitted=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val old=async {runCatching {EverydayWriteAuthority.run({}) {admitted.complete(Unit);finish.await();organizer.save(task("old-action"),null)}}}
        admitted.await()
        val maintenance=EverydayWriteAuthority.beginMaintenance()
        assertTrue(runCatching {EverydayWriteAuthority.requireCurrent()}.isFailure)
        maintenance.close();maintenance.close();finish.complete(Unit)
        assertTrue(old.await().isFailure);assertNull(organizer.get("old-action"))
        EverydayWriteAuthority.run({}) {organizer.save(task("new-action"),null)}
        assertNotNull(organizer.get("new-action"))
    }
}
