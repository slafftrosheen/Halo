package com.halo.app.ui.screens.notebook

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.*
import com.halo.app.notebook.NotebookUseCases
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class NoteStructuredRecoveryTest {
    private val main = StandardTestDispatcher()
    private val owners = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var notes: RoomNotebookRepository
    private lateinit var canonical: NotebookDocumentRepository
    private lateinit var documents: NotebookDocumentRepository
    private var failWrites = false
    private var count = 0
    @Before fun setup() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.notebookDocumentDao()
        canonical = NotebookDocumentRepository(dao, db)
        documents = NotebookDocumentRepository(object : NotebookDocumentDao by dao {
            override suspend fun upsertRecovery(entity: NoteDraftEntity) {
                if (failWrites) throw java.io.IOException("Simulated draft failure")
                dao.upsertRecovery(entity)
            }
            override suspend fun commitRevision(noteId: String, expectedRevision: Long, body: String, schemaVersion: Int): Int {
                if (failWrites) throw java.io.IOException("Simulated commit failure")
                return dao.commitRevision(noteId, expectedRevision, body, schemaVersion)
            }
        }, db)
        notes = RoomNotebookRepository(db.notebookDao(), documents = canonical)
    }
    @After fun close() {
        owners.clear(); main.scheduler.runCurrent()
        db.close(); Dispatchers.resetMain()
    }
    private suspend fun await(check: () -> Boolean) = withTimeout(10_000) {
        while (true) { main.scheduler.runCurrent(); if (check()) break; delay(10) }
    }
    private suspend fun model(id: String): NoteEditorViewModel = NoteEditorViewModel(id, NotebookUseCases(notes),
        document = documents, autosaveDelayMs = 60_000).also {
        owners.put("${count++}", it); await { it.uiState.value.status != SaveStatus.LOADING }
    }
    @Test fun failedStructuredWritesStayOwnedByEditorAcrossExternalObservation() = runBlocking {
        val note = notes.createNote("Original")
        val model = model(note.id)
        failWrites = true
        model.dispatch(DocumentCommand.SetText(model.uiState.value.blocks.first().id, "Only remaining local work"))
        await { model.uiState.value.status == SaveStatus.FAILED }
        assertFalse(model.keepBeforeLeaving())
        val stored = canonical.load(note.id)!!
        assertTrue(canonical.commit(note.id, stored.revision, NotebookDocumentCodec.parse("Other writer", { java.util.UUID.randomUUID().toString() }).blocks) is CommitOutcome.Committed)
        delay(100); main.scheduler.runCurrent()
        assertEquals("Only remaining local work", model.uiState.value.displayBody)
        assertEquals(SaveStatus.FAILED, model.uiState.value.status)
        assertFalse(model.uiState.value.draftKept)
    }
    @Test fun olderBaseDraftReopensAsExactConflictIncludingUnappliedSource() = runBlocking {
        val note = notes.createNote("# Original\n\nBody")
        val model = model(note.id)
        model.renameTitle("Local title")
        model.setMode(EditorMode.SOURCE)
        val raw = "# Exact raw input\n\n- [ ] not applied\n"
        model.updateSourceDraft(raw)
        await { model.uiState.value.draftKept }
        val kept = canonical.loadRecovery(note.id)!!
        val stored = canonical.load(note.id)!!
        assertTrue(canonical.commit(note.id, stored.revision, NotebookDocumentCodec.parse("Newer stored content", { java.util.UUID.randomUUID().toString() }).blocks,
            title = "Other writer") is CommitOutcome.Committed)
        assertTrue(model.keepBeforeLeaving())
        val reopened = model(note.id)
        assertEquals(SaveStatus.CONFLICT, reopened.uiState.value.status)
        assertEquals(kept.blocks, reopened.uiState.value.blocks)
        assertEquals("Local title", reopened.uiState.value.title)
        assertEquals(raw, reopened.uiState.value.sourceDraft)
        assertTrue(reopened.uiState.value.draftKept)
        assertNotNull(reopened.uiState.value.conflict)
        assertEquals("Other writer", canonical.load(note.id)!!.title)
    }
}
