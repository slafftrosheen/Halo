package com.halo.app.data.everyday

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.everyday.briefing.*
import com.halo.app.everyday.collectors.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],application=Application::class)
class EverydayRetentionTest {
    private val app=ApplicationProvider.getApplicationContext<Application>()
    private val db=Room.inMemoryDatabaseBuilder(app,AppDatabase::class.java).allowMainThreadQueries().build()
    private val now=Instant.ofEpochMilli(System.currentTimeMillis())
    private val collectors=RoomCollectorRepository(db)
    private val briefs=RoomBriefRepository(db,beforePublish={EverydayBriefPublicationAuthority.requireCurrent(db,it)})
    @After fun close() {db.close()}
    @Test fun purgingANoteRemovesDerivedCacheAndBriefAndFencesAnAlreadyReservedCollector()=runBlocking {
        val note=WorkspaceObjectEntity("note","NOTE","Private note",now.toEpochMilli(),now.toEpochMilli(),"USER","ACTIVE",false,"READY","note:note")
        db.notebookDao().insertWorkspaceObject(note)
        val source=collectors.save(StarterSourceCatalogue.local().single {it.kind==CollectorKind.LOCAL_WORKSPACE},null)
        val claim=(collectors.reserve(source.id,source.version,now,true) as CollectorAdmission.Accepted).reservation
        val payload=CollectorPayload.Local(listOf(LocalFact(note.id,"NOTE",note.title,"An excerpt","halo://workspace/object/note","1",now)))
        val snapshot=CollectorSnapshot(source.id,source.version,payload,"r1",now,now,now.plusSeconds(900))
        collectors.finish(claim,snapshot,CollectorAttempt(source.id,now,CollectorAttemptState.CURRENT))
        val projection=EverydayBriefEngine.project(BriefInput(now,ZoneOffset.UTC,BriefAudience.EVERYDAY,BriefPeriod.ON_DEMAND,collectors.states(),emptyList(),emptyList()),BriefSettings())
        assertTrue(projection.document.facts.any {it.kind==BriefFactKind.NOTE})
        briefs.publish(projection)
        val held=(collectors.reserve(source.id,source.version,now.plusSeconds(1000),true) as CollectorAdmission.Accepted).reservation
        db.withTransaction {
            db.notebookDao().insertWorkspaceObject(note.copy(lifecycle="TRASH"))
            EverydayLocalDataCleanup.notePurged(db,note.id)
        }
        assertNull(collectors.state(source.id)!!.snapshot);assertNull(briefs.get(projection.document.id))
        assertFalse(collectors.finish(held,snapshot,CollectorAttempt(source.id,now,CollectorAttemptState.CURRENT)))
        assertTrue(runCatching {briefs.publish(projection)}.isFailure)
        assertNull(briefs.get(projection.document.id))
    }
    @Test fun mismatchedTypedSnapshotCannotBePublishedOrSilentlyReadAsAnotherCollector()=runBlocking {
        val source=collectors.save(StarterSourceCatalogue.external().first().copy(selected=true),null)
        val claim=(collectors.reserve(source.id,source.version,now,true) as CollectorAdmission.Accepted).reservation
        val invalid=CollectorSnapshot(source.id,source.version,CollectorPayload.Local(emptyList()),"wrong",now,now,now.plusSeconds(900))
        assertTrue(runCatching {collectors.finish(claim,invalid,CollectorAttempt(source.id,now,CollectorAttemptState.CURRENT))}.isFailure)
        assertNull(collectors.state(source.id)!!.snapshot)
        db.collectorDao().putSnapshot(CollectorCodec.entity(invalid))
        val recovered=collectors.state(source.id)!!
        assertNull(recovered.snapshot);assertEquals(CollectorAttemptState.FAILED,recovered.attempt.state)
    }
}
