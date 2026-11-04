package com.halo.app.ui.workspace

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.studio.*
import com.halo.sdk.runtime.ink.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StudioInkSessionTest {
    @Test fun aggregateQuotaFailureStillRecoversTheValidPaintLayer()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(),AppDatabase::class.java).allowMainThreadQueries().build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            fun ink(count:Int)=InkCodec.encode(InkDocument(List(count) {InkPoint(100,100)}.chunked(2048).mapIndexed {index,points->InkStroke(InkTool.LEGACY,points,"stroke-$index")}))
            val selected=StudioLayer(id="selected",name="Recover these marks",kind=StudioLayerKind.INK,ink=ink(4))
            val others=List(4) {StudioLayer(id="other-$it",name="Other",kind=StudioLayerKind.INK,ink=ink(16383))}
            val projects=StudioProjectRepository(db)
            val original=projects.create("Full composition",StudioDocument(layers=others+selected))
            val session=StudioInkSession(original,selected.id,projects,scope,{}, {})
            val latest=ink(5);session.submit(latest)
            assertFalse(session.finishAndAwait())
            val recovered=session.draft().also(StudioDocument::requireValid)
            assertEquals(latest,recovered.layers.single().ink)
            assertEquals(original,projects.get(original.id))
        } finally {scope.cancel();db.close()}
    }
    @Test fun finishingWaitsForQueuedFailureAndRetainsTheLatestMarks()=runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(),AppDatabase::class.java).allowMainThreadQueries().build()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val projects=StudioProjectRepository(db)
            val layer=StudioLayer(id="ink",name="Ink",kind=StudioLayerKind.INK)
            val original=projects.create("Queued finish",StudioDocument(layers=listOf(layer)))
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val held=scope.launch {db.withTransaction {
                projects.apply(original.id,original.revision,StudioCommand.Background(42));entered.complete(Unit);release.await()
            }}
            entered.await()
            val session=StudioInkSession(original,layer.id,projects,scope,{}, {})
            val latest=InkCodec.encode(InkDocument(listOf(InkStroke(InkTool.FINGER,listOf(InkPoint(100,100))))))
            session.submit(latest)
            val closing=scope.async(start=CoroutineStart.UNDISPATCHED) {session.finishAndAwait()}
            assertFalse(closing.isCompleted)
            release.complete(Unit);held.join()
            assertFalse(closing.await());assertTrue(session.failed.value)
            assertEquals(latest,session.draft().layers.single().ink)
        } finally {scope.cancel();db.close()}
    }
    @Test fun completedSnapshotsCoalesceAndExternalEditsStopWithoutOverwritingTheirWinner() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java).allowMainThreadQueries().build()
        val writer = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val projects = StudioProjectRepository(db)
            val layer = StudioLayer(id = "ink", name = "Drawing", kind = StudioLayerKind.INK)
            val initial = projects.create("Drawing", StudioDocument(layers = listOf(layer)))
            var failures = 0
            val session = StudioInkSession(initial, layer.id, projects, writer, {}, { failures++ })
            fun ink(x: Int) = InkCodec.encode(InkDocument(listOf(InkStroke(InkTool.FINGER, listOf(InkPoint(x, x))))))
            val first = ink(100); val latest = ink(200)
            session.submit(first); session.submit(latest)
            assertTrue(session.awaitPersisted())
            assertEquals(latest, projects.get(initial.id)?.session?.current?.layers?.single()?.ink)
            val current = projects.get(initial.id)!!
            assertEquals(current.revision, session.persistedRevision)
            val winner = projects.apply(current.id, current.revision, StudioCommand.Background(42))
            assertEquals(current.revision, session.persistedRevision)
            val recoverable = ink(300)
            session.submit(recoverable)
            assertFalse(session.awaitPersisted())
            assertTrue(session.failed.value)
            assertEquals(1, failures)
            assertEquals(winner, projects.get(initial.id))
            assertEquals(recoverable, session.draft().layers.single().ink)
            session.finish()
        } finally { writer.cancel(); db.close() }
    }
}
