package com.halo.app.data.studio

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StudioProjectRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var projects: StudioProjectRepository
    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        projects = StudioProjectRepository(database)
    }
    @After fun close() { database.close() }
    @Test fun draftUndoAndRedoSurviveReopeningWithoutChangingSavedOriginal() = runTest {
        val created = projects.create("Original")
        val changed = projects.apply(created.id, created.revision, StudioCommand.Background(123))
        val reopened = StudioProjectRepository(database)
        assertEquals(changed, reopened.get(created.id))
        assertTrue(changed.hasUnsavedChanges)
        assertEquals(0, changed.committed.background)
        val undone = reopened.undo(changed.id, changed.revision)
        assertEquals(created.session.current, undone.session.current)
        val redone = reopened.redo(undone.id, undone.revision)
        assertEquals(123, redone.session.current.background)
        val saved = reopened.save(redone.id, redone.revision)
        assertFalse(saved.hasUnsavedChanges)
        assertEquals(listOf(saved.revision, 1L), reopened.versions(saved.id).map { it.revision })
    }
    @Test fun concurrentStaleCommandsHaveOneWinnerAndNeverRefreshTheirAuthority() = runTest {
        val created = projects.create("Concurrent")
        val results = (1..2).map { color -> async {
            runCatching { projects.apply(created.id, created.revision, StudioCommand.Background(color)) }
        } }.awaitAll()
        assertEquals(1, results.count { it.isSuccess })
        val winner = results.single { it.isSuccess }.getOrThrow()
        assertEquals(winner, projects.get(created.id))
        assertTrue(runCatching { projects.rename(created.id, created.revision, "Stale") }.isFailure)
        assertEquals("Concurrent", projects.get(created.id)?.title)
    }
    @Test fun saveAsPinsSourceRevisionAndRestoringVersionIsUndoable() = runTest {
        val original = projects.create("Original")
        val draft = projects.apply(original.id, original.revision, StudioCommand.Background(456))
        val copy = projects.saveAs(draft.id, draft.revision, "Copy")
        assertNotEquals(original.id, copy.id)
        assertEquals(draft.session.current, copy.committed)
        assertEquals(draft, projects.get(original.id))
        val provenance = database.workspaceContextDao().provenanceFor(copy.id)!!
        assertEquals(original.id, provenance.sourceId)
        assertEquals(draft.revision.toString(), provenance.revision)
        val saved = projects.save(draft.id, draft.revision)
        val restored = projects.restoreVersion(saved.id, saved.revision, 1)
        assertEquals(original.committed, restored.session.current)
        assertEquals(saved.committed, restored.committed)
        val undo = projects.undo(restored.id, restored.revision)
        assertEquals(saved.session.current, undo.session.current)
        assertEquals(saved.committed, projects.discardDraft(undo.id, undo.revision).session.current)
    }
    @Test fun trashedOrCorruptProjectsAreNeverSilentlyRecreated() = runTest {
        val original = projects.create("Protected")
        database.notebookDao().setLifecycle(original.id, "TRASHED", 5)
        assertNull(projects.get(original.id))
        assertTrue(runCatching { projects.apply(original.id, original.revision, StudioCommand.Background(1)) }.isFailure)
        database.notebookDao().setLifecycle(original.id, "ACTIVE", 6)
        val future = "{\"schema\":99}"
        database.openHelper.writableDatabase.execSQL("UPDATE studio_projects SET session=? WHERE objectId=?", arrayOf(future, original.id))
        assertTrue(runCatching { projects.get(original.id) }.isFailure)
        assertEquals(future, database.studioProjectDao().get(original.id)?.session)
    }
}
