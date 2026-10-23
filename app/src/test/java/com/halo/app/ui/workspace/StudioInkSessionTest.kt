package com.halo.app.ui.workspace

import android.app.Application
import androidx.room.Room
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
            val winner = projects.apply(current.id, current.revision, StudioCommand.Background(42))
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
